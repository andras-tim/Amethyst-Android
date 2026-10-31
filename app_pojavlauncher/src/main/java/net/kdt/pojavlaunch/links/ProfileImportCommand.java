package net.kdt.pojavlaunch.links;

import android.app.Activity;
import android.net.Uri;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.packwiz.HashFormat;
import net.kdt.pojavlaunch.packwiz.PackPaths;
import net.kdt.pojavlaunch.packwiz.PackToml;
import net.kdt.pojavlaunch.packwiz.PackwizException;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * amethyst://profile/import?url=&lt;pack.toml&gt;[&amp;name=&lt;profile name&gt;][&amp;icon=&lt;png url&gt;][&amp;renderer=&lt;id&gt;][&amp;control=&lt;layout file&gt;][&amp;java=&lt;runtime&gt;][&amp;suggest.resolutionLines=&lt;lines&gt;][&amp;ram=&lt;MB&gt;]
 * <p>Adds a profile synced from the pack and selects it. A profile that already syncs this pack URL is updated instead
 * when the link changes it, showing only the fields that change and keeping the worlds, else it is selected. The pack's files are downloaded on the first Play.
 */
final class ProfileImportCommand implements LinkCommand {
    @Override
    public LinkAction prepare(Activity activity, Uri link) throws Exception {
        String url = PackPaths.checkUrl(PackLinks.requiredParameter(activity, link, "url"), "pack.toml");
        PackToml pack = PackLinks.fetchPack(activity, url);
        ProfileSettings settings = ProfileSettings.read(activity, link, PackLinks.optionalParameter(link, "name"));
        String profileName = settings.name != null ? settings.name : pack.name;
        String versionId = PackLinks.versionId(pack);

        List<String> existing = PackLinks.profilesWithPack(url);
        if (!existing.isEmpty()) {
            String key = existing.get(0);
            MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
            if (settings.changes(profile)) return update(activity, link, key, pack, settings);
            String existingName = profile.name;
            LinkText text = new LinkText(activity)
                    .row(R.string.link_key_pack, pack.title())
                    .row(R.string.link_key_minecraft, PackLinks.versions(pack))
                    .sentence(R.string.link_import_existing);
            PackLinks.addResolutionHint(activity, text, link);
            return new LinkAction(LinkText.title(activity, R.string.link_select_title, existingName), text.build(),
                    activity.getString(R.string.link_select)) {
                @Override
                String apply() {
                    PackLinks.select(key);
                    return activity.getString(R.string.link_selected, existingName);
                }
            };
        }

        LinkText text = new LinkText(activity)
                .row(R.string.link_key_pack, pack.title())
                .row(R.string.link_key_minecraft, PackLinks.versions(pack))
                .row(R.string.link_key_url, url);
        settings.addRows(text);
        text.sentence(R.string.link_import_download);
        PackLinks.addResolutionHint(activity, text, link);
        return new LinkAction(LinkText.title(activity, R.string.link_import_title, profileName), text.build(),
                activity.getString(R.string.link_import_confirm)) {
            @Override
            String apply() throws Exception {
                MinecraftProfile profile = MinecraftProfile.createTemplate();
                profile.name = profileName;
                profile.packwizUrl = url;
                profile.lastVersionId = versionId;
                settings.apply(profile);
                profile.gameDir = "./custom_instances/" + instanceFolder(profileName, url);
                LauncherProfiles.load();
                String key = LauncherProfiles.getFreeProfileKey();
                LauncherProfiles.mainProfileJson.profiles.put(key, profile);
                LauncherProfiles.write();
                PackLinks.select(key);
                return activity.getString(R.string.link_import_done, profileName);
            }
        };
    }

    /** Overrides what the link gives on the existing profile {@code key}. */
    private static LinkAction update(Activity activity, Uri link, String key, PackToml pack, ProfileSettings settings) {
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
        String profileName = profile.name;
        LinkText text = new LinkText(activity)
                .row(R.string.link_key_pack, pack.title())
                .row(R.string.link_key_minecraft, PackLinks.versions(pack));
        settings.addChanges(text, profile);
        text.sentence(R.string.link_update_worlds);
        PackLinks.addResolutionHint(activity, text, link);

        return new LinkAction(LinkText.title(activity, R.string.link_update_title, profileName), text.build(),
                activity.getString(R.string.link_update_confirm)) {
            @Override
            String apply() throws Exception {
                LauncherProfiles.load();
                MinecraftProfile current = LauncherProfiles.mainProfileJson.profiles.get(key);
                if (current == null) throw new PackwizException(activity.getString(R.string.link_error_profile_removed, profileName));
                settings.apply(current);
                LauncherProfiles.write();
                PackLinks.select(key);
                return activity.getString(R.string.link_update_done, current.name);
            }
        };
    }

    /** Readable and unique per pack URL, so two packs with the same name never share a directory. */
    private static String instanceFolder(String name, String url) throws Exception {
        String readable = name.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (readable.isEmpty()) readable = "pack";
        return readable + "-" + HashFormat.SHA1.hash(url.getBytes(StandardCharsets.UTF_8)).substring(0, 8);
    }
}
