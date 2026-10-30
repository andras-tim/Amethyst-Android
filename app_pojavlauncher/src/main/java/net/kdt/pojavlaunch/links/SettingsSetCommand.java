package net.kdt.pojavlaunch.links;

import android.app.Activity;
import android.net.Uri;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.packwiz.PackwizException;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.Set;

/**
 * amethyst://settings/set?resolutionRatio=&lt;percent&gt;
 * <p>Changes a launcher setting, for every profile. Only the listed setting is accepted: a link must not reach the
 * rest of the settings.
 */
final class SettingsSetCommand implements LinkCommand {
    private static final String RESOLUTION_RATIO = "resolutionRatio";

    @Override
    public LinkAction prepare(Activity activity, Uri link) throws Exception {
        Set<String> names = link.getQueryParameterNames();
        if (names.size() != 1 || !names.contains(RESOLUTION_RATIO)) {
            throw new PackwizException(activity.getString(R.string.link_error_settings_only, RESOLUTION_RATIO, names.toString()));
        }
        int ratio = resolutionRatio(activity, PackLinks.requiredParameter(link, RESOLUTION_RATIO));
        int current = Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100);
        return new LinkAction(activity.getString(R.string.link_settings_title),
                activity.getString(R.string.link_settings_resolution, current, ratio),
                activity.getString(R.string.link_settings_confirm)) {
            @Override
            String apply() {
                LauncherPreferences.DEFAULT_PREF.edit().putInt(RESOLUTION_RATIO, ratio).apply();
                LauncherPreferences.PREF_SCALE_FACTOR = ratio / 100f;
                return activity.getString(R.string.link_settings_done);
            }
        };
    }

    /** The values the settings screen's seek bar offers. */
    private static int resolutionRatio(Activity activity, String value) throws PackwizException {
        int min = activity.getResources().getInteger(R.integer.resolution_seekbar_min);
        int step = activity.getResources().getInteger(R.integer.resolution_seekbar_increment);
        try {
            int ratio = Integer.parseInt(value);
            if (ratio >= min && ratio <= 100 && ratio % step == 0) return ratio;
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new PackwizException(activity.getString(R.string.link_error_resolution, min, step, value));
    }
}
