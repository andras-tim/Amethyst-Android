package net.kdt.pojavlaunch.links;

import android.app.Activity;
import android.net.Uri;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.packwiz.HashFormat;
import net.kdt.pojavlaunch.packwiz.PackPaths;
import net.kdt.pojavlaunch.packwiz.PackToml;
import net.kdt.pojavlaunch.packwiz.PackwizException;
import net.kdt.pojavlaunch.profiles.ProfileIconCache;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * amethyst://profile/import?url=&lt;pack.toml&gt;[&amp;name=&lt;profile name&gt;][&amp;icon=&lt;png url&gt;][&amp;renderer=&lt;id&gt;][&amp;control=&lt;layout file&gt;][&amp;java=&lt;runtime&gt;][&amp;suggest.resolutionLines=&lt;lines&gt;][&amp;ram=&lt;MB&gt;]
 * <p>Adds a profile synced from the pack and selects it. A profile that already syncs this pack URL is updated instead
 * when the link changes it, showing only the fields that change and keeping the worlds, else it is selected. The pack's files are downloaded on the first Play.
 */
final class ProfileImportCommand implements LinkCommand {
    private static final String INSTANCES_DIR = "./custom_instances/";

    @Override
    public LinkAction prepare(Activity activity, Uri link) throws Exception {
        String url = PackLinks.checkedUrl(activity, PackLinks.requiredParameter(activity, link, "url"), "pack.toml");
        PackToml pack = PackLinks.fetchPack(activity, url);
        ProfileSettings settings = ProfileSettings.read(activity, link, PackLinks.nameParameter(activity, link));
        String profileName = settings.name != null ? settings.name : pack.name;
        String versionId = PackLinks.versionId(pack);

        List<String> existing = PackLinks.profilesWithPack(url);
        if (!existing.isEmpty()) {
            String key = existing.get(0);
            MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
            return settings.changes(profile) ? update(activity, key, pack, settings) : select(activity, key);
        }

        MinecraftProfile preview = new MinecraftProfile();
        preview.packwizUrl = url;
        preview.gameDir = INSTANCES_DIR + instanceFolder(profileName, url);
        LinkText text = new LinkText(activity).row(R.string.link_key_minecraft, PackLinks.versions(pack));
        settings.addRows(text, preview, true);
        text.sentence(R.string.link_import_download);
        return new LinkAction(activity.getString(R.string.link_import_title, profileName), settings.icon, text.build(),
                activity.getString(R.string.global_save)) {
            @Override
            String apply() throws Exception {
                MinecraftProfile profile = MinecraftProfile.createTemplate();
                profile.name = profileName;
                profile.packwizUrl = url;
                profile.gameDir = preview.gameDir;
                profile.lastVersionId = versionId;
                settings.apply(profile);
                LauncherProfiles.load();
                String key = LauncherProfiles.getFreeProfileKey();
                LauncherProfiles.mainProfileJson.profiles.put(key, profile);
                LauncherProfiles.write();
                PackLinks.select(key);
                return activity.getString(R.string.link_import_done, profileName);
            }
        };
    }

    /** Nothing to confirm: the profile already is what the link asks for. */
    private static LinkAction select(Activity activity, String key) {
        return new LinkAction(null, null, null, null) {
            @Override
            String apply() {
                PackLinks.select(key);
                return activity.getString(R.string.link_selected);
            }
        };
    }

    /** Overrides what the link gives on the existing profile {@code key}. */
    private static LinkAction update(Activity activity, String key, PackToml pack, ProfileSettings settings) {
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
        String profileName = settings.name != null ? settings.name : profile.name;
        LinkText text = new LinkText(activity).row(R.string.link_key_minecraft, PackLinks.versions(pack));
        settings.addRows(text, profile, false);
        text.sentence(R.string.link_update_worlds);

        return new LinkAction(activity.getString(R.string.link_update_title, profileName),
                settings.icon != null ? settings.icon : profile.icon, text.build(),
                activity.getString(R.string.global_save)) {
            @Override
            String apply() throws Exception {
                LauncherProfiles.load();
                MinecraftProfile current = LauncherProfiles.mainProfileJson.profiles.get(key);
                if (current == null) throw new PackwizException(activity.getString(R.string.link_error_profile_removed, profileName));
                settings.apply(current);
                LauncherProfiles.write();
                ProfileIconCache.dropIcon(key);
                PackLinks.select(key);
                return activity.getString(R.string.link_update_done, current.name);
            }
        };
    }

    /** A readable id; two packs with the same name never share a directory. */
    private static String instanceFolder(String name, String url) throws Exception {
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (id.isEmpty()) id = "pack";
        for (MinecraftProfile other : LauncherProfiles.mainProfileJson.profiles.values()) {
            if ((INSTANCES_DIR + id).equals(other.gameDir)) {
                return id + "-" + HashFormat.SHA1.hash(url.getBytes(StandardCharsets.UTF_8)).substring(0, 8);
            }
        }
        return id;
    }
}
