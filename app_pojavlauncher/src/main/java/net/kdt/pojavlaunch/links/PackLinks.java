package net.kdt.pojavlaunch.links;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader;
import net.kdt.pojavlaunch.modloaders.packwiz.PackwizLauncher;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.packwiz.Fetcher;
import net.kdt.pojavlaunch.packwiz.PackPaths;
import net.kdt.pojavlaunch.packwiz.PackToml;
import net.kdt.pojavlaunch.packwiz.PackwizException;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.ByteArrayOutputStream;
import java.io.File;
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

    static String requiredParameter(Context context, Uri link, String name) throws PackwizException {
        String value = link.getQueryParameter(name);
        if (value == null || value.trim().isEmpty()) throw new PackwizException(context.getString(R.string.link_error_missing_parameter, name));
        return value.trim();
    }

    static String optionalParameter(Uri link, String name) {
        String value = link.getQueryParameter(name);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** The "ram" parameter in MB; 0 when missing (the profile then uses the global setting). */
    static int ramParameter(Context context, Uri link) throws PackwizException {
        String value = optionalParameter(link, "ram");
        if (value == null) return 0;
        try {
            int ram = Integer.parseInt(value);
            if (ram >= 512) return ram;
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new PackwizException(context.getString(R.string.link_error_ram, value));
    }

    /** The launcher's resolution scale from a link, one of the settings screen's values; 0 when missing. */
    static int resolutionParameter(Context context, Uri link, String name) throws PackwizException {
        String value = optionalParameter(link, name);
        if (value == null) return 0;
        int min = context.getResources().getInteger(R.integer.resolution_seekbar_min);
        int step = context.getResources().getInteger(R.integer.resolution_seekbar_increment);
        try {
            int ratio = Integer.parseInt(value);
            if (ratio >= min && ratio <= 100 && ratio % step == 0) return ratio;
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new PackwizException(context.getString(R.string.link_error_resolution, min, step, value));
    }

    /** The launcher's current resolution scale in percent. */
    static int currentResolution() {
        return Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100);
    }

    /** Sets the launcher's resolution scale, for every profile. */
    static void setResolution(int ratio) {
        LauncherPreferences.DEFAULT_PREF.edit().putInt("resolutionRatio", ratio).apply();
        LauncherPreferences.PREF_SCALE_FACTOR = ratio / 100f;
    }

    /** The "renderer" parameter, one of the launcher's renderer ids for this device; null when missing. */
    static String rendererParameter(Context context, Uri link) throws PackwizException {
        String value = optionalParameter(link, "renderer");
        if (value != null && !Tools.checkRendererCompatible(context, value)) {
            throw new PackwizException(context.getString(R.string.link_error_renderer, value));
        }
        return value;
    }

    /** The "control" parameter, a control layout file that already exists here; null when missing. */
    static String controlParameter(Context context, Uri link) throws PackwizException {
        String value = optionalParameter(link, "control");
        if (value == null) return null;
        if (value.contains("/") || value.contains("\\") || !new File(Tools.CTRLMAP_PATH, value).isFile()) {
            throw new PackwizException(context.getString(R.string.link_error_control, value));
        }
        return value;
    }

    /** The "java" parameter, the name of an installed runtime, as a profile stores it; null when missing. */
    static String javaParameter(Context context, Uri link) throws PackwizException {
        String value = optionalParameter(link, "java");
        if (value == null) return null;
        if (value.contains("/") || value.contains("\\") || MultiRTUtils.read(value).versionString == null) {
            throw new PackwizException(context.getString(R.string.link_error_java, value));
        }
        return Tools.LAUNCHERPROFILES_RTPREFIX + value;
    }

    /** Downloads and checks pack.toml, so the confirmation can show what the link adds. */
    static PackToml fetchPack(Context context, String url) throws IOException {
        String checked = PackPaths.checkUrl(url, "pack.toml");
        PackToml pack = PackToml.parse(new String(download(context, checked, MAX_PACK_BYTES), StandardCharsets.UTF_8), checked);
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
    static String fetchIcon(Context context, String url) throws IOException {
        String checked = PackPaths.checkUrl(url, "icon");
        byte[] png = download(context, checked, MAX_ICON_BYTES);
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (png.length <= i || png[i] != PNG_SIGNATURE[i]) throw new PackwizException(context.getString(R.string.link_error_icon), null, url);
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

    private static byte[] download(Context context, String url, int maxBytes) throws IOException {
        try (Fetcher.Download download = PackwizLauncher.newFetcher().open(url)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = download.body.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > maxBytes) throw new PackwizException(context.getString(R.string.link_error_too_large), null, url);
            }
            return out.toByteArray();
        }
    }
}
