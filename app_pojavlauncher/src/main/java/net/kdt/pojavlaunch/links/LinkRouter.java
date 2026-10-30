package net.kdt.pojavlaunch.links;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Opens amethyst://&lt;command&gt;?&lt;params&gt; links, e.g. amethyst://profile/import?url=https://.../pack.toml
 * (the debug build: amethyst-debug://).
 * Every command first prepares (network, parsing) off the UI thread, then shows what it will change; nothing
 * changes before the player confirms.
 */
public final class LinkRouter {
    private static final String TAG = "LinkRouter";
    private static final Map<String, LinkCommand> COMMANDS = new LinkedHashMap<>();

    static {
        COMMANDS.put("profile/import", new ProfileImportCommand());
        COMMANDS.put("profile/update", new ProfileUpdateCommand());
        COMMANDS.put("settings/set", new SettingsSetCommand());
    }

    private LinkRouter() {}

    public static boolean isLink(Context context, Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        return data != null && context.getString(R.string.link_scheme).equalsIgnoreCase(data.getScheme());
    }

    public static void handle(Activity activity, Uri link) {
        handle(activity, link, null);
    }

    /** @param onApplied runs on the UI thread after the player confirmed and the change succeeded; may be null */
    public static void handle(Activity activity, Uri link, Runnable onApplied) {
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
                Tools.runOnUiThread(() -> confirm(activity, action, onApplied));
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

    private static void confirm(Activity activity, LinkAction action, Runnable onApplied) {
        if (activity.isFinishing()) return;
        AlertDialog confirmation = new AlertDialog.Builder(activity)
                .setTitle(action.title)
                .setMessage(action.description)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(action.confirmLabel, (dialog, which) -> {
                    try {
                        Toast.makeText(activity, action.apply(), Toast.LENGTH_LONG).show();
                        if (onApplied != null) onApplied.run();
                    } catch (Exception e) {
                        Log.e(TAG, "Link action failed", e);
                        showFailed(activity, String.valueOf(e.getMessage()));
                    }
                })
                .show();
        // tapping a value copies it
        TextView message = confirmation.findViewById(android.R.id.message);
        if (message != null) message.setMovementMethod(LinkMovementMethod.getInstance());
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
