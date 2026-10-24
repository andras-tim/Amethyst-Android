package net.kdt.pojavlaunch.modloaders.packwiz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.packwiz.PackwizSync;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Dialogs of the packwiz sync. */
final class PackwizDialogs {
    // A log is a few KiB; the cap only guards the share intent against a runaway file
    private static final int MAX_LOG_BYTES = 200_000;

    private PackwizDialogs() {}

    /**
     * Shows why the sync failed, offers the log for sharing and, when the instance was synced before (e.g. no
     * network now), starting the game with the files already installed.
     */
    static void showSyncFailed(Activity activity, File gameDir, Throwable error, Runnable launch) {
        if (activity.isFinishing()) return;
        boolean syncedBefore = new File(new File(gameDir, PackwizSync.STATE_DIR), "state.toml").isFile();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.packwiz_sync_failed_title)
                .setMessage(String.valueOf(error.getMessage()))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.packwiz_share_log, (dialog, which) -> shareLog(activity, gameDir, error));
        if (syncedBefore) builder.setPositiveButton(R.string.packwiz_play_installed, (dialog, which) -> launch.run());
        builder.show();
    }

    private static void shareLog(Activity activity, File gameDir, Throwable error) {
        String log;
        try {
            log = read(PackwizSync.logFile(gameDir));
        } catch (IOException e) {
            log = "No sync log (" + e.getMessage() + ")\n\n" + error;
        }
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.packwiz_sync_failed_title))
                .putExtra(Intent.EXTRA_TEXT, log);
        activity.startActivity(Intent.createChooser(send, activity.getString(R.string.packwiz_share_log)));
    }

    private static String read(File file) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1 && bytes.size() < MAX_LOG_BYTES) bytes.write(buffer, 0, read);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
}
