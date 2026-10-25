package net.kdt.pojavlaunch.links;

import android.net.Uri;
import android.util.Base64;

import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader;
import net.kdt.pojavlaunch.modloaders.packwiz.PackwizLauncher;
import net.kdt.pojavlaunch.packwiz.Fetcher;
import net.kdt.pojavlaunch.packwiz.PackPaths;
import net.kdt.pojavlaunch.packwiz.PackToml;
import net.kdt.pojavlaunch.packwiz.PackwizException;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** What the pack profile link commands share. */
final class PackLinks {
    private static final int MAX_PACK_BYTES = 1024 * 1024;
    private static final int MAX_ICON_BYTES = 512 * 1024;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};

    private PackLinks() {}

    static String requiredParameter(Uri link, String name) throws PackwizException {
        String value = link.getQueryParameter(name);
        if (value == null || value.trim().isEmpty()) throw new PackwizException("The link has no \"" + name + "\" parameter");
        return value.trim();
    }

    static String optionalParameter(Uri link, String name) {
        String value = link.getQueryParameter(name);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** The "ram" parameter in MB; 0 when missing (the profile then uses the global setting). */
    static int ramParameter(Uri link) throws PackwizException {
        String value = optionalParameter(link, "ram");
        if (value == null) return 0;
        try {
            int ram = Integer.parseInt(value);
            if (ram >= 512) return ram;
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new PackwizException("The link's \"ram\" parameter must be a number of MB (at least 512): " + value);
    }

    /** Downloads and checks pack.toml, so the confirmation can show what the link adds. */
    static PackToml fetchPack(String url) throws IOException {
        String checked = PackPaths.checkUrl(url, "pack.toml");
        PackToml pack = PackToml.parse(new String(download(checked, MAX_PACK_BYTES), StandardCharsets.UTF_8), checked);
        PackwizLauncher.loaderOf(pack);  // fails now for a loader the launcher cannot install
        return pack;
    }

    /** The profile version the pack needs, e.g. "1.20.1-forge-47.4.20". */
    static String versionId(PackToml pack) throws PackwizException {
        ModLoader loader = PackwizLauncher.loaderOf(pack);
        return loader == null ? pack.minecraftVersion() : loader.getVersionId();
    }

    /** "1.20.1, forge 47.4.20" for the confirmation. */
    static String versions(PackToml pack) {
        return pack.minecraftVersion() + (pack.loader() == null ? "" : ", " + pack.loader() + " " + pack.loaderVersion());
    }

    /** A profile icon from a PNG link, as the data URI profiles store. */
    static String fetchIcon(String url) throws IOException {
        String checked = PackPaths.checkUrl(url, "icon");
        byte[] png = download(checked, MAX_ICON_BYTES);
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (png.length <= i || png[i] != PNG_SIGNATURE[i]) throw new PackwizException("The icon is not a PNG image", null, url);
        }
        return "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP);
    }

    /** Keys of the profiles synced from this pack URL. */
    static List<String> profilesWithPack(String packUrl) {
        LauncherProfiles.load();
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            if (packUrl.equals(entry.getValue().packwizUrl)) keys.add(entry.getKey());
        }
        return keys;
    }

    static void select(String profileKey) {
        LauncherPreferences.DEFAULT_PREF.edit().putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey).apply();
        ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, profileKey);
    }

    static String host(String url) {
        String host = Uri.parse(url).getHost();
        return host == null ? url : host;
    }

    private static byte[] download(String url, int maxBytes) throws IOException {
        try (Fetcher.Download download = PackwizLauncher.newFetcher().open(url)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = download.body.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > maxBytes) throw new PackwizException("The file is larger than expected", null, url);
            }
            return out.toByteArray();
        }
    }
}
