package net.kdt.pojavlaunch.links;

import android.app.Activity;
import android.net.Uri;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.packwiz.PackwizException;

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
        int ratio = PackLinks.resolutionParameter(activity, link, RESOLUTION_RATIO);
        if (ratio == 0) PackLinks.requiredParameter(activity, link, RESOLUTION_RATIO);
        return new LinkAction(activity.getString(R.string.link_settings_title),
                new LinkText(activity).change(R.string.link_key_resolution, PackLinks.currentResolution() + " %", ratio + " %")
                        .sentence(R.string.link_settings_every_profile).build(),
                activity.getString(R.string.link_settings_confirm)) {
            @Override
            String apply() {
                PackLinks.setResolution(ratio);
                return activity.getString(R.string.link_settings_done);
            }
        };
    }
}
