package net.kdt.pojavlaunch.links;

import android.app.Activity;
import android.net.Uri;

import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** What a link sets on a profile for this device; a field the link leaves out stays as it is. */
final class ProfileSettings {
    final String name;
    final String icon;
    final int ram;
    final String renderer;
    final String control;
    final String java;
    private final String iconUrl;

    private ProfileSettings(String name, String icon, String iconUrl, int ram, String renderer, String control,
                            String java) {
        this.name = name;
        this.icon = icon;
        this.iconUrl = iconUrl;
        this.ram = ram;
        this.renderer = renderer;
        this.control = control;
        this.java = java;
    }

    /** @param name the new name, null when the link has none or uses it to find the profile */
    static ProfileSettings read(Activity activity, Uri link, String name) throws IOException {
        String iconUrl = PackLinks.optionalParameter(link, "icon");
        return new ProfileSettings(name, iconUrl == null ? null : PackLinks.fetchIcon(activity, iconUrl), iconUrl,
                PackLinks.ramParameter(activity, link), PackLinks.rendererParameter(activity, link),
                PackLinks.controlParameter(activity, link), PackLinks.javaParameter(activity, link));
    }

    /** One "key: old -> new" line for every field that would change. */
    List<String> changes(MinecraftProfile profile) {
        List<String> changes = new ArrayList<>();
        addChange(changes, "name", profile.name, name);
        if (icon != null && !icon.equals(profile.icon)) changes.add(change("icon", profile.icon == null ? null : "...", iconUrl));
        if (ram > 0 && ram != profile.ramAllocation) changes.add(change("ram", String.valueOf(profile.ramAllocation), String.valueOf(ram)));
        addChange(changes, "renderer", profile.pojavRendererName, renderer);
        addChange(changes, "control", profile.controlFile, control);
        addChange(changes, "java", profile.javaDir, java);
        return changes;
    }

    void apply(MinecraftProfile profile) {
        if (name != null) profile.name = name;
        if (icon != null) profile.icon = icon;
        if (ram > 0) profile.ramAllocation = ram;
        if (renderer != null) profile.pojavRendererName = renderer;
        if (control != null) profile.controlFile = control;
        if (java != null) profile.javaDir = java;
    }

    private static void addChange(List<String> changes, String key, String oldValue, String newValue) {
        if (newValue != null && !Objects.equals(oldValue, newValue)) changes.add(change(key, oldValue, newValue));
    }

    static String change(String key, String oldValue, String newValue) {
        return key + ": " + (oldValue == null ? "-" : oldValue) + " -> " + newValue;
    }
}
