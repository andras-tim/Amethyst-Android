package net.kdt.pojavlaunch.links;

import android.app.Activity;
import android.net.Uri;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.packwiz.HashFormat;
import net.kdt.pojavlaunch.packwiz.PackPaths;
import net.kdt.pojavlaunch.packwiz.PackToml;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * amethyst://profile/import?url=&lt;pack.toml&gt;[&amp;name=&lt;profile name&gt;][&amp;icon=&lt;png url&gt;][&amp;renderer=&lt;id&gt;][&amp;control=&lt;layout file&gt;][&amp;java=&lt;runtime&gt;][&amp;ram=&lt;MB&gt;]
 * <p>Adds a profile synced from the pack and selects it; a profile that already syncs this pack is selected instead
 * of adding a second one. The pack's files are downloaded on the first Play.
 */
final class ProfileImportCommand implements LinkCommand {
    @Override
    public LinkAction prepare(Activity activity, Uri link) throws Exception {
        String url = PackPaths.checkUrl(PackLinks.requiredParameter(activity, link, "url"), "pack.toml");
        PackToml pack = PackLinks.fetchPack(activity, url);
        String name = PackLinks.optionalParameter(link, "name");
        String profileName = name != null ? name : pack.name;
        String iconUrl = PackLinks.optionalParameter(link, "icon");
        String icon = iconUrl == null ? null : PackLinks.fetchIcon(activity, iconUrl);
        String versionId = PackLinks.versionId(pack);
        int ram = PackLinks.ramParameter(activity, link);
        String renderer = PackLinks.rendererParameter(activity, link);
        String control = PackLinks.controlParameter(activity, link);
        String java = PackLinks.javaParameter(activity, link);

        List<String> existing = PackLinks.profilesWithPack(url);
        if (!existing.isEmpty()) {
            String key = existing.get(0);
            String existingName = LauncherProfiles.mainProfileJson.profiles.get(key).name;
            return new LinkAction(activity.getString(R.string.link_import_title),
                    activity.getString(R.string.link_import_existing, existingName, pack.title(), PackLinks.versions(pack)),
                    activity.getString(R.string.link_select)) {
                @Override
                String apply() {
                    PackLinks.select(key);
                    return activity.getString(R.string.link_selected, existingName);
                }
            };
        }

        return new LinkAction(activity.getString(R.string.link_import_title),
                activity.getString(R.string.link_import_message, profileName, pack.title(), PackLinks.versions(pack),
                        PackLinks.host(url))
                        + (ram > 0 ? "\n" + activity.getString(R.string.link_import_ram, ram) : "")
                        + (renderer == null ? "" : "\n" + activity.getString(R.string.link_import_renderer, renderer))
                        + (control == null ? "" : "\n" + activity.getString(R.string.link_import_control, control))
                        + (java == null ? "" : "\n" + activity.getString(R.string.link_import_java, java))
                        + "\n\n" + activity.getString(R.string.link_import_download),
                activity.getString(R.string.link_import_confirm)) {
            @Override
            String apply() throws Exception {
                MinecraftProfile profile = MinecraftProfile.createTemplate();
                profile.name = profileName;
                profile.packwizUrl = url;
                profile.lastVersionId = versionId;
                profile.icon = icon;
                profile.ramAllocation = ram;
                profile.pojavRendererName = renderer;
                profile.controlFile = control;
                profile.javaDir = java;
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

    /** Readable and unique per pack URL, so two packs with the same name never share a directory. */
    private static String instanceFolder(String name, String url) throws Exception {
        String readable = name.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (readable.isEmpty()) readable = "pack";
        return readable + "-" + HashFormat.SHA1.hash(url.getBytes(StandardCharsets.UTF_8)).substring(0, 8);
    }
}
