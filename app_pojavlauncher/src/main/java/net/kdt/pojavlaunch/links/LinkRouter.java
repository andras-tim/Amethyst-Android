package net.kdt.pojavlaunch.links;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Opens amethyst://&lt;command&gt;?&lt;params&gt; links, e.g. amethyst://profile/import?url=https://.../pack.toml.
 * Every command first prepares (network, parsing) off the UI thread, then shows what it will change; nothing
 * changes before the player confirms.
 */
public final class LinkRouter {
    public static final String SCHEME = "amethyst";
    private static final String TAG = "LinkRouter";
    private static final Map<String, LinkCommand> COMMANDS = new LinkedHashMap<>();

    static {
        COMMANDS.put("profile/import", new ProfileImportCommand());
        COMMANDS.put("profile/update", new ProfileUpdateCommand());
    }

    private LinkRouter() {}

    public static boolean isLink(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        return data != null && SCHEME.equalsIgnoreCase(data.getScheme());
    }

    public static void handle(Activity activity, Uri link) {
        Log.i(TAG, "Link: " + link);
        String name = commandOf(link);
        LinkCommand command = COMMANDS.get(name);
        if (command == null) {
            showFailed(activity, activity.getString(R.string.link_unknown_command, name, joinedCommands()));
            return;
        }
        Toast.makeText(activity, R.string.link_opening, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                LinkAction action = command.prepare(activity, link);
                Tools.runOnUiThread(() -> confirm(activity, action));
            } catch (Exception e) {
                Log.e(TAG, "Cannot open " + link, e);
                Tools.runOnUiThread(() -> showFailed(activity, String.valueOf(e.getMessage())));
            }
        });
    }

    /** "amethyst://profile/import?..." has host "profile" and path "/import". */
    static String commandOf(Uri link) {
        String host = link.getHost() == null ? "" : link.getHost();
        String path = link.getPath() == null ? "" : link.getPath();
        String command = (host + path).toLowerCase(Locale.ROOT);
        while (command.endsWith("/")) command = command.substring(0, command.length() - 1);
        return command;
    }

    private static String joinedCommands() {
        StringBuilder names = new StringBuilder();
        for (String name : COMMANDS.keySet()) names.append(names.length() == 0 ? "" : ", ").append(name);
        return names.toString();
    }

    private static void confirm(Activity activity, LinkAction action) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle(action.title)
                .setMessage(action.description)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(action.confirmLabel, (dialog, which) -> {
                    try {
                        Toast.makeText(activity, action.apply(), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Link action failed", e);
                        showFailed(activity, String.valueOf(e.getMessage()));
                    }
                })
                .show();
    }

    private static void showFailed(Activity activity, String message) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.link_failed_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
