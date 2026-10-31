package net.kdt.pojavlaunch.links;

import android.app.Activity;
import android.net.Uri;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.IOException;

/** What a link sets on a profile for this device; a field the link leaves out stays as it is. */
final class ProfileSettings {
    final String name;
    final String icon;
    final int ram;
    final String renderer;
    final String control;
    private final ControlLayout mControlLayout;
    final String java;
    private final Activity mActivity;

    private ProfileSettings(Activity activity, String name, String icon, int ram, String renderer,
                            ControlLayout controlLayout, String java) {
        mActivity = activity;
        this.name = name;
        this.icon = icon;
        this.ram = ram;
        this.renderer = renderer;
        mControlLayout = controlLayout;
        control = controlLayout == null ? null : controlLayout.file;
        this.java = java;
    }

    /** @param name the new name, null when the link has none or uses it to find the profile */
    static ProfileSettings read(Activity activity, Uri link, String name) throws IOException {
        String iconUrl = PackLinks.optionalParameter(link, "icon");
        return new ProfileSettings(activity, name, iconUrl == null ? null : PackLinks.fetchIcon(activity, iconUrl),
                PackLinks.ramParameter(activity, link), PackLinks.rendererParameter(activity, link),
                PackLinks.controlParameter(activity, link), PackLinks.javaParameter(activity, link));
    }

    /** The rows in the order of the profile editor; a value the link changes shows the old one too. */
    void addRows(LinkText text, MinecraftProfile profile, boolean isNew) {
        addRow(text, R.string.link_key_control, control != null && control.equals(profile.controlFile)
                ? mControlLayout.url : profile.controlFile, mControlLayout == null ? null : mControlLayout.url, isNew);
        addRow(text, R.string.link_key_url, profile.packwizUrl, null, isNew);
        addRow(text, R.string.link_key_ram, profile.ramAllocation > 0 ? megabytes(profile.ramAllocation) : null,
                ram > 0 ? megabytes(ram) : null, isNew);
        addRow(text, R.string.link_key_java, profile.javaDir, java, isNew);
        addRow(text, R.string.link_key_renderer, profile.pojavRendererName, renderer, isNew);
    }

    private String megabytes(int value) {
        return mActivity.getString(R.string.link_value_ram, value);
    }

    private void addRow(LinkText text, int label, String oldValue, String newValue, boolean isNew) {
        if (differs(newValue, oldValue) && !isNew) {
            text.change(label, oldValue, newValue);
            return;
        }
        String value = newValue != null ? newValue : oldValue;
        if (value != null) text.row(label, value);
        else text.plainRow(label, mActivity.getString(R.string.link_value_default));
    }

    /** @return whether applying these settings would change the profile */
    boolean changes(MinecraftProfile profile) throws IOException {
        return (mControlLayout != null && mControlLayout.changes()) || differs(name, profile.name) || differs(icon, profile.icon) || (ram > 0 && ram != profile.ramAllocation)
                || differs(control, profile.controlFile) || differs(java, profile.javaDir)
                || differs(renderer, profile.pojavRendererName);
    }

    private static boolean differs(String value, String current) {
        return value != null && !value.equals(current);
    }

    void apply(MinecraftProfile profile) throws IOException {
        if (mControlLayout != null) mControlLayout.save();
        if (name != null) profile.name = name;
        if (icon != null) profile.icon = icon;
        if (ram > 0) profile.ramAllocation = ram;
        if (renderer != null) profile.pojavRendererName = renderer;
        if (control != null) profile.controlFile = control;
        if (java != null) profile.javaDir = java;
    }
}
