package net.kdt.pojavlaunch.modloaders.packwiz;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader;
import net.kdt.pojavlaunch.modloaders.modpacks.api.NotificationDownloadListener;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.packwiz.HttpFetcher;
import net.kdt.pojavlaunch.packwiz.MetaFile;
import net.kdt.pojavlaunch.packwiz.PackToml;
import net.kdt.pojavlaunch.packwiz.PackwizException;
import net.kdt.pojavlaunch.packwiz.PackwizSync;
import net.kdt.pojavlaunch.packwiz.SyncListener;
import net.kdt.pojavlaunch.packwiz.SyncResult;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Syncs a profile's packwiz pack before the game starts and keeps the profile's version (Minecraft + mod loader) in
 * step with the pack.
 */
public final class PackwizLauncher {
    private static final String TAG = "PackwizLauncher";
    // The internal Java 8 runtime the Forge installer needs is unpacked in the background on the first start
    private static final long RUNTIME_WAIT_MS = 15 * 60 * 1000;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private PackwizLauncher() {}

    public static boolean hasPack(MinecraftProfile profile) {
        return profile.packwizUrl != null && !profile.packwizUrl.trim().isEmpty();
    }

    public static HttpFetcher newFetcher() {
        return new HttpFetcher(Tools.APP_NAME + " packwiz sync");
    }

    /** Syncs on a background thread, then runs {@code launch} on the UI thread when the game can start. */
    public static void syncThenLaunch(Activity activity, MinecraftProfile profile, Runnable launch) {
        if (!RUNNING.compareAndSet(false, true)) {
            Toast.makeText(activity, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDir = Tools.getGameDirPath(profile);
        String packUrl = profile.packwizUrl.trim();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                ProgressLayout.setProgress(ProgressLayout.SYNC_PACK, 0, R.string.packwiz_progress_checking);
                SyncResult result = new PackwizSync(gameDir, packUrl, newFetcher(), MetaFile.Side.CLIENT,
                        new ProgressReporter()).run();
                ModLoader loader = loaderOf(result.pack);
                String versionId = loader == null ? result.pack.minecraftVersion() : loader.getVersionId();
                if (!versionId.equals(profile.lastVersionId)) {
                    Log.i(TAG, "Profile version " + profile.lastVersionId + " -> " + versionId);
                    profile.lastVersionId = versionId;
                    LauncherProfiles.write();
                }
                if (loader != null && !isInstalled(versionId)) {
                    installLoader(activity, loader, result.pack);
                    if (loader.requiresGuiInstallation() || !isInstalled(versionId)) return;
                }
                Tools.runOnUiThread(launch);
            } catch (IOException | RuntimeException | InterruptedException e) {
                Log.e(TAG, "Pack sync failed", e);
                Tools.runOnUiThread(() -> PackwizDialogs.showSyncFailed(activity, gameDir, e, launch));
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.SYNC_PACK);
                RUNNING.set(false);
            }
        });
    }

    /** The mod loader the pack asks for; null for a vanilla pack. */
    public static ModLoader loaderOf(PackToml pack) throws PackwizException {
        String loader = pack.loader();
        if (loader == null) return null;
        switch (loader) {
            case "forge": return new ModLoader(ModLoader.MOD_LOADER_FORGE, pack.loaderVersion(), pack.minecraftVersion());
            case "neoforge": return new ModLoader(ModLoader.MOD_LOADER_NEOFORGE, pack.loaderVersion(), pack.minecraftVersion());
            case "fabric": return new ModLoader(ModLoader.MOD_LOADER_FABRIC, pack.loaderVersion(), pack.minecraftVersion());
            case "quilt": return new ModLoader(ModLoader.MOD_LOADER_QUILT, pack.loaderVersion(), pack.minecraftVersion());
            default: throw new PackwizException("The pack needs the \"" + loader + "\" mod loader, which this launcher cannot install");
        }
    }

    private static boolean isInstalled(String versionId) {
        return new File(Tools.DIR_HOME_VERSION, versionId + "/" + versionId + ".json").isFile();
    }

    /** Forge and NeoForge open their installer when the download is done; the player presses Play afterwards. */
    private static void installLoader(Activity activity, ModLoader loader, PackToml pack)
            throws InterruptedException, PackwizException {
        if (loader.requiresGuiInstallation()) {
            waitForRuntime();
            Tools.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.packwiz_installing_loader,
                    pack.loader(), pack.loaderVersion()), Toast.LENGTH_LONG).show());
        }
        Runnable download = loader.getDownloadTask(new NotificationDownloadListener(activity, loader));
        if (download == null) throw new PackwizException("No installer for " + pack.loader() + " " + pack.loaderVersion());
        download.run();
    }

    private static void waitForRuntime() throws InterruptedException, PackwizException {
        long deadline = System.currentTimeMillis() + RUNTIME_WAIT_MS;
        while (MultiRTUtils.getNearestJreName(8) == null) {
            if (System.currentTimeMillis() > deadline) throw new PackwizException("The Java runtime did not get unpacked");
            Thread.sleep(1000);
        }
    }

    private static final class ProgressReporter implements SyncListener {
        @Override
        public void onStage(String text) {
            ProgressLayout.setProgress(ProgressLayout.SYNC_PACK, 0, text);
        }

        @Override
        public void onDownload(int index, int count, String path, long bytes, long totalBytes) {
            double fileShare = totalBytes > 0 ? Math.min(1.0, (double) bytes / totalBytes) : 0;
            int percent = (int) (100 * ((index - 1) + fileShare) / count);
            String size = totalBytes > 0 ? String.format(java.util.Locale.ROOT, " (%.1f/%.1f MB)",
                    bytes / 1048576.0, totalBytes / 1048576.0) : "";
            ProgressLayout.setProgress(ProgressLayout.SYNC_PACK, percent, index + "/" + count + " " + path + size);
        }

        @Override
        public void onLog(String line) {
            Log.i(TAG, line);
        }
    }
}
