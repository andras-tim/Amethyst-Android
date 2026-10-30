package net.kdt.pojavlaunch.links;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.packwiz.PackPaths;
import net.kdt.pojavlaunch.packwiz.PackToml;
import net.kdt.pojavlaunch.packwiz.PackwizException;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * amethyst://profile/update?url=&lt;pack.toml&gt;[&amp;from=&lt;old pack.toml&gt;][&amp;name=&lt;profile name&gt;]
 * [&amp;icon=&lt;png url&gt;][&amp;ram=&lt;MB&gt;][&amp;renderer=&lt;id&gt;][&amp;control=&lt;layout file&gt;][&amp;java=&lt;runtime&gt;]
 * <p>Overrides what the link gives on an existing pack profile; the game directory and the worlds stay. The profile is
 * the one syncing {@code from}, else the pack profile called {@code name}, else the only pack profile there is. With
 * {@code from}, {@code name} is the new name. The confirmation shows only the fields that change.
 */
final class ProfileUpdateCommand implements LinkCommand {
    @Override
    public LinkAction prepare(Activity activity, Uri link) throws Exception {
        String url = PackPaths.checkUrl(PackLinks.requiredParameter(activity, link, "url"), "pack.toml");
        String from = PackLinks.optionalParameter(link, "from");
        if (from != null) from = PackPaths.checkUrl(from, "pack.toml");  // stored URLs are in this form
        String name = PackLinks.optionalParameter(link, "name");
        String key = findProfile(activity, from, name);
        PackToml pack = PackLinks.fetchPack(activity, url);
        return action(activity, key, pack, url, ProfileSettings.read(activity, link, from != null ? name : null));
    }

    /** Overrides the settings of the profile {@code key} and points it at {@code url}. */
    static LinkAction action(Activity activity, String key, PackToml pack, String url, ProfileSettings settings) {
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
        String profileName = profile.name;
        LinkText text = new LinkText(activity)
                .row(R.string.link_key_profile, profileName)
                .row(R.string.link_key_pack, pack.title())
                .row(R.string.link_key_minecraft, PackLinks.versions(pack));
        boolean changes = settings.addChanges(text, profile);
        if (!url.equals(profile.packwizUrl)) {
            text.change(R.string.link_key_url, profile.packwizUrl, url);
            changes = true;
        }
        text.sentence(changes ? R.string.link_update_worlds : R.string.link_update_nothing);

        return new LinkAction(activity.getString(R.string.link_update_title), text.build(),
                activity.getString(R.string.link_update_confirm)) {
            @Override
            String apply() throws Exception {
                LauncherProfiles.load();
                MinecraftProfile current = LauncherProfiles.mainProfileJson.profiles.get(key);
                if (current == null) throw new PackwizException(activity.getString(R.string.link_error_profile_removed, profileName));
                current.packwizUrl = url;
                settings.apply(current);
                LauncherProfiles.write();
                PackLinks.select(key);
                return activity.getString(R.string.link_update_done, current.name);
            }
        };
    }

    private static String findProfile(Context context, String from, String name) throws PackwizException {
        LauncherProfiles.load();
        List<String> packProfiles = new ArrayList<>();
        for (Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            MinecraftProfile profile = entry.getValue();
            if (profile.packwizUrl == null) continue;
            if (from != null ? from.equals(profile.packwizUrl) : name == null || name.equals(profile.name)) {
                packProfiles.add(entry.getKey());
            }
        }
        if (packProfiles.size() == 1) return packProfiles.get(0);
        if (packProfiles.isEmpty()) {
            throw new PackwizException(from != null ? context.getString(R.string.link_error_no_profile_from, from)
                    : name != null ? context.getString(R.string.link_error_no_profile_named, name)
                    : context.getString(R.string.link_error_no_profile));
        }
        throw new PackwizException(context.getString(R.string.link_error_several_profiles));
    }
}
