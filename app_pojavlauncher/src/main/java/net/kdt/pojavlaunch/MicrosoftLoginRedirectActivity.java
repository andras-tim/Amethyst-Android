package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

/**
 * Takes the Microsoft login back from the system browser. Enabled only while such a login runs: other launchers can
 * claim the same address, and a login code must not arrive here unasked.
 */
public class MicrosoftLoginRedirectActivity extends Activity {
    private static boolean sWaiting;
    private static boolean sReturned;

    /** Opens the page in the system browser, and listens for its redirect. */
    public static void startLogin(Activity activity, String url) {
        setEnabled(activity, true);
        sWaiting = true;
        Tools.openURL(activity, url);
    }

    /** @return whether the login came back since the last call */
    public static boolean consumeReturned() {
        boolean returned = sReturned;
        sReturned = false;
        return returned;
    }

    private static void setEnabled(Context context, boolean enabled) {
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context, MicrosoftLoginRedirectActivity.class),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnabled(this, false);
        Uri data = getIntent().getData();
        if (sWaiting) {
            sWaiting = false;
            sReturned = true;
            if (data != null && data.getQueryParameter("code") != null) {
                ExtraCore.setValue(ExtraConstants.MICROSOFT_LOGIN_TODO, data);
            }
            startActivity(new Intent(this, LauncherActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        finish();
    }
}
