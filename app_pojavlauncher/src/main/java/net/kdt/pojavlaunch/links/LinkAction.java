package net.kdt.pojavlaunch.links;

/** A prepared link command: what the confirmation shows, and the change it makes once confirmed. */
abstract class LinkAction {
    final String title;
    final CharSequence description;
    final String confirmLabel;

    LinkAction(String title, CharSequence description, String confirmLabel) {
        this.title = title;
        this.description = description;
        this.confirmLabel = confirmLabel;
    }

    /** Makes the change on the UI thread; returns the message shown afterwards. */
    abstract String apply() throws Exception;
}
