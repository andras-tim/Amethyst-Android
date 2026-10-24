package net.kdt.pojavlaunch.packwiz;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * .packwiz/state.toml: what the last sync installed. A file is only ever deleted when it is listed here and still
 * has the bytes the sync wrote, so the player's own files are never touched. A path is listed (without a hash)
 * before its download starts, so a sync killed halfway finds its partial files on the next run.
 */
final class SyncState {
    static final String FILE_NAME = "state.toml";
    private static final int VERSION = 1;

    String packUrl = "";
    String indexHash = "";
    /** By installed path. */
    final Map<String, FileRecord> files = new LinkedHashMap<>();
    /** Metafiles that resolved to nothing on this side (server-only, optional off), by metafile path. */
    final Map<String, String> skippedMetafiles = new LinkedHashMap<>();

    static final class FileRecord {
        final String path;
        /** The pack's hash for this file; null while a download is pending. */
        HashFormat hashFormat;
        String hash;
        /** Size and timestamp of the file when it last had the pack's bytes; size -1 when it has not. */
        long size = -1;
        long modified;
        boolean preserve;
        /** The metafile this file came from and that metafile's hash, null for files listed in the index. */
        String source;
        String sourceHash;
        String url;

        FileRecord(String path) {
            this.path = path;
        }

        boolean isPending() {
            return hash == null;
        }

        boolean hasHash(HashFormat format, String expected) {
            return !isPending() && hashFormat == format && format.matches(expected, hash);
        }

        /** Still the pack's bytes, judged by size and timestamp, so it is trusted without hashing again. */
        boolean isUntouched(File file) {
            return !isPending() && size >= 0 && file.isFile() && file.length() == size && file.lastModified() == modified;
        }

        /** The file has the pack's bytes. */
        void markIntact(File file, HashFormat format, String packHash) {
            hashFormat = format;
            hash = packHash;
            size = file.length();
            modified = file.lastModified();
        }

        /** The file differs from the pack and is kept on purpose (a preserved config the player changed). */
        void markDifferent(HashFormat format, String packHash) {
            hashFormat = format;
            hash = packHash;
            size = -1;
            modified = 0;
        }

        void markPending() {
            hashFormat = null;
            hash = null;
            size = -1;
            modified = 0;
        }
    }

    FileRecord recordFromSource(String source, String sourceHash) {
        for (FileRecord record : files.values()) {
            if (source.equals(record.source) && sourceHash.equals(record.sourceHash) && !record.isPending()) return record;
        }
        return null;
    }

    /** An unreadable state is set aside as state.toml.corrupt and the sync starts over from the files on disk. */
    static SyncState load(File stateDir, SyncLog log) {
        File file = new File(stateDir, FILE_NAME);
        SyncState state = new SyncState();
        if (!file.isFile()) return state;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            UnmodifiableConfig toml = new TomlParser().parse(reader);
            state.packUrl = text(toml.get("pack.url"));
            state.indexHash = text(toml.get("pack.index-hash"));
            for (UnmodifiableConfig item : tables(toml.get("file"))) {
                FileRecord record = new FileRecord(PackPaths.safeRelative(text(item.get("path"))));
                String hash = text(item.get("hash"));
                if (!hash.isEmpty()) {
                    record.hashFormat = HashFormat.fromId(text(item.get("hash-format")));
                    record.hash = hash;
                }
                record.size = item.get("size") instanceof Number ? number(item.get("size")) : -1;
                record.modified = number(item.get("modified"));
                record.preserve = Boolean.TRUE.equals(item.get("preserve"));
                record.source = emptyToNull(text(item.get("source")));
                record.sourceHash = emptyToNull(text(item.get("source-hash")));
                record.url = emptyToNull(text(item.get("url")));
                state.files.put(record.path, record);
            }
            for (UnmodifiableConfig item : tables(toml.get("skipped"))) {
                state.skippedMetafiles.put(text(item.get("source")), text(item.get("source-hash")));
            }
            return state;
        } catch (Exception e) {
            File corrupt = new File(stateDir, FILE_NAME + ".corrupt");
            //noinspection ResultOfMethodCallIgnored
            corrupt.delete();
            boolean moved = file.renameTo(corrupt);
            log.line("State file unreadable (" + e.getMessage() + "), " + (moved ? "moved to " + corrupt.getName() : "ignored")
                    + "; checking every file again");
            return new SyncState();
        }
    }

    /** Written to a temporary file and renamed over the old one, so a crash leaves either the old or the new state. */
    void save(File stateDir) throws IOException {
        Config toml = newTable();
        toml.set("version", VERSION);
        toml.set("pack.url", packUrl);
        toml.set("pack.index-hash", indexHash);
        List<Config> fileTables = new ArrayList<>();
        for (FileRecord record : files.values()) {
            Config item = newTable();
            item.set("path", record.path);
            item.set("hash-format", record.hashFormat == null ? "" : record.hashFormat.id);
            item.set("hash", record.hash == null ? "" : record.hash);
            item.set("size", record.size);
            item.set("modified", record.modified);
            if (record.preserve) item.set("preserve", true);
            if (record.source != null) item.set("source", record.source);
            if (record.sourceHash != null) item.set("source-hash", record.sourceHash);
            if (record.url != null) item.set("url", record.url);
            fileTables.add(item);
        }
        toml.set("file", fileTables);
        List<Config> skippedTables = new ArrayList<>();
        for (Map.Entry<String, String> skipped : skippedMetafiles.entrySet()) {
            Config item = newTable();
            item.set("source", skipped.getKey());
            item.set("source-hash", skipped.getValue());
            skippedTables.add(item);
        }
        toml.set("skipped", skippedTables);

        File target = new File(stateDir, FILE_NAME);
        File temporary = new File(stateDir, FILE_NAME + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(temporary), StandardCharsets.UTF_8)) {
            new TomlWriter().write(toml, writer);
        }
        if (!temporary.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored some file systems do not rename over an existing file
            target.delete();
            if (!temporary.renameTo(target)) throw new IOException("Cannot write " + target);
        }
    }

    /** Keys in insertion order, so the file reads path first. */
    private static Config newTable() {
        return TomlFormat.instance().createConfig(LinkedHashMap::new);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private static List<UnmodifiableConfig> tables(Object value) {
        List<UnmodifiableConfig> tables = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof UnmodifiableConfig) tables.add((UnmodifiableConfig) item);
            }
        }
        return tables;
    }
}
