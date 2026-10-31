package net.kdt.pojavlaunch.links;

/** A prepared link command: what the confirmation shows, and the change it makes once confirmed. */
abstract class LinkAction {
    final String title;
    final String icon;
    final CharSequence description;
    final String confirmLabel;

    /** @param icon the profile's data icon, or null */
    LinkAction(String title, String icon, CharSequence description, String confirmLabel) {
        this.title = title;
        this.icon = icon;
        this.description = description;
        this.confirmLabel = confirmLabel;
    }

    /** Makes the change on the UI thread; returns the message shown afterwards. */
    abstract String apply() throws Exception;
}
