package net.kdt.pojavlaunch.links;

import android.app.Activity;
import android.net.Uri;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.IOException;
import java.util.Objects;

/** What a link sets on a profile for this device; a field the link leaves out stays as it is. */
final class ProfileSettings {
    final String name;
    final String icon;
    final int ram;
    final String renderer;
    final String control;
    final String java;
    /** The launcher setting, so it applies to every profile; 0 leaves it. */
    final int resolution;
    private final String iconUrl;
    private final Activity mActivity;

    private ProfileSettings(Activity activity, String name, String icon, String iconUrl, int ram, String renderer, String control,
                            String java, int resolution) {
        mActivity = activity;
        this.name = name;
        this.icon = icon;
        this.iconUrl = iconUrl;
        this.ram = ram;
        this.renderer = renderer;
        this.control = control;
        this.java = java;
        this.resolution = resolution;
    }

    /** @param name the new name, null when the link has none or uses it to find the profile */
    static ProfileSettings read(Activity activity, Uri link, String name) throws IOException {
        String iconUrl = PackLinks.optionalParameter(link, "icon");
        return new ProfileSettings(activity, name, iconUrl == null ? null : PackLinks.fetchIcon(activity, iconUrl), iconUrl,
                PackLinks.ramParameter(activity, link), PackLinks.rendererParameter(activity, link),
                PackLinks.controlParameter(activity, link), PackLinks.javaParameter(activity, link),
                PackLinks.resolutionParameter(activity, link, "resolutionRatio"));
    }

    /** The rows of a new profile. */
    void addRows(LinkText text) {
        if (iconUrl != null) text.row(R.string.link_key_icon, iconUrl);
        if (ram > 0) text.row(R.string.link_key_ram, ram + " MB");
        if (renderer != null) text.row(R.string.link_key_renderer, renderer);
        if (control != null) text.row(R.string.link_key_control, control);
        if (java != null) text.row(R.string.link_key_java, java);
        addResolution(text);
    }

    private boolean addResolution(LinkText text) {
        int current = PackLinks.currentResolution();
        if (resolution == 0 || resolution == current) return false;
        text.change(R.string.link_key_resolution, current + " %",
                mActivity.getString(R.string.link_resolution_value, resolution));
        return true;
    }

    /** @return whether applying these settings would change the profile */
    boolean changes(MinecraftProfile profile) {
        return addChanges(new LinkText(mActivity), profile);
    }

    /** One row for every field that would change; @return whether there is any */
    boolean addChanges(LinkText text, MinecraftProfile profile) {
        boolean changes = false;
        changes |= addChange(text, R.string.link_key_name, profile.name, name);
        if (icon != null && !icon.equals(profile.icon)) {
            text.change(R.string.link_key_icon, profile.icon == null ? null : "...", iconUrl);
            changes = true;
        }
        if (ram > 0 && ram != profile.ramAllocation) {
            text.change(R.string.link_key_ram, profile.ramAllocation + " MB", ram + " MB");
            changes = true;
        }
        changes |= addChange(text, R.string.link_key_renderer, profile.pojavRendererName, renderer);
        changes |= addChange(text, R.string.link_key_control, profile.controlFile, control);
        changes |= addChange(text, R.string.link_key_java, profile.javaDir, java);
        changes |= addResolution(text);
        return changes;
    }

    void apply(MinecraftProfile profile) {
        if (name != null) profile.name = name;
        if (icon != null) profile.icon = icon;
        if (ram > 0) profile.ramAllocation = ram;
        if (renderer != null) profile.pojavRendererName = renderer;
        if (control != null) profile.controlFile = control;
        if (java != null) profile.javaDir = java;
        if (resolution > 0) PackLinks.setResolution(resolution);
    }

    private static boolean addChange(LinkText text, int label, String oldValue, String newValue) {
        if (newValue == null || Objects.equals(oldValue, newValue)) return false;
        text.change(label, oldValue, newValue);
        return true;
    }
}
