package net.kdt.pojavlaunch.packwiz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Brings an instance directory to the state a packwiz pack describes: downloads what is missing or changed, removes
 * what the pack dropped, and leaves the player's own files alone. Worlds, options and anything the pack does not
 * list are never touched.
 *
 * <p>When pack.toml still points at the index of the last sync and no installed file changed, only pack.toml is
 * downloaded, so checking on every launch is cheap.
 */
public final class PackwizSync {
    public static final String STATE_DIR = ".packwiz";
    private static final String PART_SUFFIX = ".packwiz-part";
    private static final int ATTEMPTS = 3;
    private static final long MAX_TEXT_BYTES = 16L * 1024 * 1024;
    private static final long PROGRESS_STEP_BYTES = 256L * 1024;

    private final File instanceDir;
    private final String packUrl;
    private final Fetcher fetcher;
    private final MetaFile.Side side;
    private final SyncListener listener;
    long retryDelayMs = 1000;

    public PackwizSync(File instanceDir, String packUrl, Fetcher fetcher, MetaFile.Side side, SyncListener listener) {
        this.instanceDir = instanceDir;
        this.packUrl = packUrl.trim();
        this.fetcher = fetcher;
        this.side = side;
        this.listener = listener;
    }

    /** The log of the last sync of an instance, also after a failure. */
    public static File logFile(File instanceDir) {
        return new File(new File(instanceDir, STATE_DIR), SyncLog.FILE_NAME);
    }

    public SyncResult run() throws IOException {
        File stateDir = new File(instanceDir, STATE_DIR);
        if (!stateDir.isDirectory() && !stateDir.mkdirs()) throw new IOException("Cannot create " + stateDir);
        try (SyncLog log = SyncLog.open(stateDir, listener)) {
            try {
                return new Run(stateDir, log).execute();
            } catch (IOException | RuntimeException e) {
                log.error(e);
                throw e;
            }
        }
    }

    /** One file the pack wants at a path. */
    private static final class Target {
        final String path;
        final String url;
        final HashFormat hashFormat;
        final String hash;
        final boolean preserve;
        final String source;
        final String sourceHash;

        Target(String path, String url, HashFormat hashFormat, String hash, boolean preserve, String source,
               String sourceHash) {
            this.path = path;
            this.url = url;
            this.hashFormat = hashFormat;
            this.hash = hash;
            this.preserve = preserve;
            this.source = source;
            this.sourceHash = sourceHash;
        }
    }

    private final class Run {
        private final File stateDir;
        private final SyncLog log;
        private SyncState state;
        private final List<String> installed = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();
        private final List<String> keptChanged = new ArrayList<>();

        Run(File stateDir, SyncLog log) {
            this.stateDir = stateDir;
            this.log = log;
        }

        SyncResult execute() throws IOException {
            log.line("Syncing " + packUrl + " into " + instanceDir + " (" + side.name().toLowerCase(Locale.ROOT) + " side)");
            stage("Checking the pack");
            PackToml pack = PackToml.parse(text(fetch(packUrl, "pack.toml")), packUrl);
            log.line("Pack: " + pack.title() + ", Minecraft " + pack.minecraftVersion()
                    + (pack.loader() == null ? "" : ", " + pack.loader() + " " + pack.loaderVersion()));
            state = SyncState.load(stateDir, log);
            if (isUpToDate(pack)) {
                log.line("Up to date");
                return result(pack, true);
            }

            String indexUrl = PackPaths.resolveUrl(packUrl, pack.indexFile);
            stage("Reading the file list");
            byte[] indexBytes = fetch(indexUrl, pack.indexFile);
            verify(pack.indexHashFormat, pack.indexHash, pack.indexHashFormat.hash(indexBytes), pack.indexFile, indexUrl);
            IndexToml index = IndexToml.parse(text(indexBytes), indexUrl);
            log.line(index.files.size() + " entries in the index");

            Map<String, Target> targets = resolveTargets(index, indexUrl);
            List<Target> downloads = planDownloads(targets);
            List<SyncState.FileRecord> removals = planRemovals(targets);
            log.line("Plan: " + downloads.size() + " to download, " + removals.size() + " no longer in the pack");

            claim(downloads);
            download(downloads);
            remove(removals);

            state.packUrl = packUrl;
            state.indexHash = pack.indexHash;
            state.save(stateDir);
            log.line("Done: " + installed.size() + " installed, " + deleted.size() + " removed, "
                    + keptChanged.size() + " kept because the player changed them");
            return result(pack, false);
        }

        /** Same pack and index as last time, nothing pending, every installed file still has its bytes. */
        private boolean isUpToDate(PackToml pack) throws IOException {
            if (!packUrl.equals(state.packUrl) || !pack.indexHash.equalsIgnoreCase(state.indexHash)) return false;
            for (SyncState.FileRecord record : state.files.values()) {
                if (record.isPending()) return false;
                if (record.preserve) continue;  // the player may change these
                if (!record.isUntouched(PackPaths.resolveInside(instanceDir, record.path))) {
                    log.line("Changed or missing since the last sync: " + record.path);
                    return false;
                }
            }
            return true;
        }

        private Map<String, Target> resolveTargets(IndexToml index, String indexUrl) throws IOException {
            Map<String, Target> targets = new LinkedHashMap<>();
            Map<String, String> skipped = new LinkedHashMap<>();
            int metafiles = 0;
            int downloaded = 0;
            for (IndexToml.Entry entry : index.files) {
                Target target;
                if (!entry.metafile) {
                    target = new Target(entry.alias != null ? entry.alias : entry.file,
                            PackPaths.resolveUrl(indexUrl, entry.file), entry.hashFormat, entry.hash, entry.preserve, null, null);
                } else {
                    metafiles++;
                    if (entry.hash.equalsIgnoreCase(nullToEmpty(state.skippedMetafiles.get(entry.file)))) {
                        skipped.put(entry.file, entry.hash);
                        continue;
                    }
                    SyncState.FileRecord known = state.recordFromSource(entry.file, entry.hash);
                    if (known != null) {
                        target = new Target(known.path, known.url, known.hashFormat, known.hash, entry.preserve,
                                entry.file, entry.hash);
                    } else {
                        stage("Reading mod information (" + metafiles + ")");
                        String metaUrl = PackPaths.resolveUrl(indexUrl, entry.file);
                        byte[] bytes = fetch(metaUrl, entry.file);
                        downloaded++;
                        verify(entry.hashFormat, entry.hash, entry.hashFormat.hash(bytes), entry.file, metaUrl);
                        MetaFile meta = MetaFile.parse(text(bytes), metaUrl, entry.file);
                        if (!meta.side.includes(side)) {
                            log.line("Skipped " + meta.name + ": " + meta.side.name().toLowerCase(Locale.ROOT) + " side only");
                            skipped.put(entry.file, entry.hash);
                            continue;
                        }
                        if (meta.optional && !meta.optionalDefault) {
                            log.line("Skipped " + meta.name + ": optional and off by default");
                            skipped.put(entry.file, entry.hash);
                            continue;
                        }
                        target = new Target(PackPaths.join(PackPaths.parent(entry.file), meta.filename), meta.url,
                                meta.hashFormat, meta.hash, entry.preserve, entry.file, entry.hash);
                    }
                }
                if (targets.put(target.path, target) != null) {
                    throw new PackwizException("Two pack entries install the same file", target.path, null);
                }
            }
            state.skippedMetafiles.clear();
            state.skippedMetafiles.putAll(skipped);
            log.line(metafiles + " metafiles: " + downloaded + " downloaded, " + (metafiles - downloaded)
                    + " known from the last sync; " + skipped.size() + " not for this side");
            return targets;
        }

        private List<Target> planDownloads(Map<String, Target> targets) throws IOException {
            List<Target> downloads = new ArrayList<>();
            int checked = 0;
            for (Target target : targets.values()) {
                stage("Checking files (" + (++checked) + "/" + targets.size() + ")");
                File file = PackPaths.resolveInside(instanceDir, target.path);
                SyncState.FileRecord record = state.files.get(target.path);
                if (!file.exists()) {
                    downloads.add(target);
                    continue;
                }
                if (file.isDirectory()) throw new PackwizException("A directory is in the way of a pack file", target.path, null);
                if (record != null && record.hasHash(target.hashFormat, target.hash) && record.isUntouched(file)) {
                    remember(target).markIntact(file, target.hashFormat, target.hash);
                    continue;
                }
                String actual = target.hashFormat.hash(file);
                if (target.hashFormat.matches(target.hash, actual)) {
                    remember(target).markIntact(file, target.hashFormat, target.hash);
                } else if (target.preserve) {
                    log.line("Kept the player's version of " + target.path);
                    remember(target).markDifferent(target.hashFormat, target.hash);
                } else {
                    log.line((record == null ? "Replacing a file the pack now provides: " : "Replacing a changed file: ")
                            + target.path);
                    downloads.add(target);
                }
            }
            return downloads;
        }

        private List<SyncState.FileRecord> planRemovals(Map<String, Target> targets) {
            List<SyncState.FileRecord> removals = new ArrayList<>();
            for (SyncState.FileRecord record : state.files.values()) {
                if (!targets.containsKey(record.path)) removals.add(record);
            }
            return removals;
        }

        /** Lists every path before its download starts, so an interrupted sync knows its files. */
        private void claim(List<Target> downloads) throws IOException {
            if (downloads.isEmpty()) return;
            for (Target target : downloads) remember(target).markPending();
            state.save(stateDir);
        }

        private void download(List<Target> downloads) throws IOException {
            for (int i = 0; i < downloads.size(); i++) {
                Target target = downloads.get(i);
                stage("Downloading " + (i + 1) + "/" + downloads.size() + ": " + target.path);
                File file = PackPaths.resolveInside(instanceDir, target.path);
                File parent = file.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new PackwizException("Cannot create the directory for a pack file", target.path, null);
                }
                File part = new File(file.getPath() + PART_SUFFIX);
                downloadVerified(target, part, i + 1, downloads.size());
                replace(part, file, target.path);
                remember(target).markIntact(file, target.hashFormat, target.hash);
                installed.add(target.path);
                log.line("Installed " + target.path);
                state.save(stateDir);
            }
        }

        private void remove(List<SyncState.FileRecord> removals) throws IOException {
            if (removals.isEmpty()) return;
            for (SyncState.FileRecord record : removals) {
                File file = PackPaths.resolveInside(instanceDir, record.path);
                //noinspection ResultOfMethodCallIgnored a leftover of an interrupted download
                new File(file.getPath() + PART_SUFFIX).delete();
                state.files.remove(record.path);
                if (!file.exists()) continue;
                if (record.isPending()) {
                    // the interrupted sync was about to replace it, so it never had the pack's bytes
                    log.line("Left in place (an earlier sync stopped before replacing it): " + record.path);
                    keptChanged.add(record.path);
                } else if (record.isUntouched(file) || record.hashFormat.matches(record.hash, record.hashFormat.hash(file))) {
                    if (!file.delete()) throw new PackwizException("Cannot remove a file the pack dropped", record.path, null);
                    deleted.add(record.path);
                    log.line("Removed " + record.path);
                } else {
                    log.line("Left in place because the player changed it: " + record.path);
                    keptChanged.add(record.path);
                }
            }
            state.save(stateDir);
        }

        private SyncState.FileRecord remember(Target target) {
            SyncState.FileRecord record = state.files.get(target.path);
            if (record == null) {
                record = new SyncState.FileRecord(target.path);
                state.files.put(target.path, record);
            }
            record.preserve = target.preserve;
            record.source = target.source;
            record.sourceHash = target.sourceHash;
            record.url = target.url;
            return record;
        }

        private void downloadVerified(Target target, File part, int index, int count) throws IOException {
            IOException failure = null;
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                try {
                    copy(target, part, index, count);
                    String actual = target.hashFormat.hash(part);
                    if (target.hashFormat.matches(target.hash, actual)) return;
                    failure = new PackwizException("The downloaded file does not match its " + target.hashFormat.id
                            + " hash in the pack (expected " + target.hash + ", got " + actual + ")", target.path, target.url);
                } catch (PackwizException e) {
                    failure = e;
                } catch (IOException e) {
                    failure = new PackwizException("Download failed: " + e.getMessage(), target.path, target.url, e);
                }
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                if (attempt < ATTEMPTS) {
                    log.line("Attempt " + attempt + " failed for " + target.path + ", retrying: " + firstLine(failure));
                    sleep(retryDelayMs * attempt);
                }
            }
            throw failure;
        }

        private void copy(Target target, File part, int index, int count) throws IOException {
            try (Fetcher.Download download = fetcher.open(target.url);
                 OutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                long copied = 0;
                long reported = 0;
                listener.onDownload(index, count, target.path, 0, download.length);
                int read;
                while ((read = download.body.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    copied += read;
                    if (copied - reported >= PROGRESS_STEP_BYTES) {
                        reported = copied;
                        listener.onDownload(index, count, target.path, copied, download.length);
                    }
                }
                listener.onDownload(index, count, target.path, copied, download.length);
            }
        }

        private void replace(File part, File file, String path) throws PackwizException {
            if (part.renameTo(file)) return;
            // some Android storage does not rename over an existing file
            if (file.delete() && part.renameTo(file)) return;
            throw new PackwizException("Cannot move the downloaded file into place", path, null);
        }

        private byte[] fetch(String url, String path) throws IOException {
            try (Fetcher.Download download = fetcher.open(url)) {
                return readLimited(download.body, path, url);
            } catch (PackwizException e) {
                if (e.path != null || path == null) throw e;
                throw new PackwizException(firstLine(e), path, e.url != null ? e.url : url, e);
            } catch (IOException e) {
                throw new PackwizException("Download failed: " + e.getMessage(), path, url, e);
            }
        }

        private void verify(HashFormat format, String expected, String actual, String path, String url)
                throws PackwizException {
            if (!format.matches(expected, actual)) {
                throw new PackwizException("The file does not match its " + format.id + " hash in the pack (expected "
                        + expected + ", got " + actual + "); the pack may be changing right now, try again", path, url);
            }
        }

        private void stage(String text) {
            listener.onStage(text);
        }

        private SyncResult result(PackToml pack, boolean upToDate) {
            return new SyncResult(pack, upToDate, installed, deleted, keptChanged, new File(stateDir, SyncLog.FILE_NAME));
        }
    }

    private static byte[] readLimited(InputStream in, String path, String url) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_TEXT_BYTES) throw new PackwizException("Pack file is too large to be a pack file", path, url);
        }
        return out.toByteArray();
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String firstLine(Throwable error) {
        String message = String.valueOf(error.getMessage());
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static void sleep(long millis) throws PackwizException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PackwizException("Sync interrupted");
        }
    }
}
