package net.kdt.pojavlaunch.links;

/** A prepared link command: what the confirmation shows, and the change it makes once confirmed. */
abstract class LinkAction {
    final String title;
    final String description;
    final String confirmLabel;

    LinkAction(String title, String description, String confirmLabel) {
        this.title = title;
        this.description = description;
        this.confirmLabel = confirmLabel;
    }

    /** Whether the confirmation lists the link's parameters; a description of the changes replaces that list. */
    boolean listsParameters() {
        return true;
    }

    /** Makes the change on the UI thread; returns the message shown afterwards. */
    abstract String apply() throws Exception;
}
