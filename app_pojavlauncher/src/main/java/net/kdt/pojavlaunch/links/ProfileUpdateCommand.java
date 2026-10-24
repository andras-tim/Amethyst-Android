package net.kdt.pojavlaunch.links;

import android.app.Activity;
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
 * amethyst://profile/update?url=&lt;new pack.toml&gt;[&amp;from=&lt;old pack.toml&gt;][&amp;name=&lt;profile name&gt;]
 * <p>Points an existing pack profile at another pack URL (e.g. a new release tag); its game directory, worlds and
 * settings stay. The profile is the one syncing {@code from}, else the pack profile called {@code name}, else the
 * only pack profile there is.
 */
final class ProfileUpdateCommand implements LinkCommand {
    @Override
    public LinkAction prepare(Activity activity, Uri link) throws Exception {
        String url = PackPaths.checkUrl(PackLinks.requiredParameter(link, "url"), "pack.toml");
        String from = PackLinks.optionalParameter(link, "from");
        if (from != null) from = PackPaths.checkUrl(from, "pack.toml");  // stored URLs are in this form
        String name = PackLinks.optionalParameter(link, "name");
        String key = findProfile(from, name);
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
        PackToml pack = PackLinks.fetchPack(url);
        String oldUrl = profile.packwizUrl;
        String profileName = profile.name;

        return new LinkAction(activity.getString(R.string.link_update_title),
                activity.getString(R.string.link_update_message, profileName, pack.title(), PackLinks.versions(pack),
                        oldUrl, url),
                activity.getString(R.string.link_update_confirm)) {
            @Override
            String apply() throws Exception {
                LauncherProfiles.load();
                MinecraftProfile current = LauncherProfiles.mainProfileJson.profiles.get(key);
                if (current == null) throw new PackwizException("The profile \"" + profileName + "\" was removed meanwhile");
                current.packwizUrl = url;
                LauncherProfiles.write();
                PackLinks.select(key);
                return activity.getString(R.string.link_update_done, profileName);
            }
        };
    }

    private static String findProfile(String from, String name) throws PackwizException {
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
            throw new PackwizException(from != null ? "No profile syncs " + from
                    : name != null ? "No pack profile is called \"" + name + "\"" : "There is no pack profile to update");
        }
        throw new PackwizException("Several pack profiles match; the link has to name one with \"from\" (its pack URL)"
                + " or \"name\"");
    }
}
