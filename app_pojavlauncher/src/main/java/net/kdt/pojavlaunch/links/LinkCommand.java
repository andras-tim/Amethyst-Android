package net.kdt.pojavlaunch.links;

import android.app.Activity;
import android.net.Uri;

/** One command of the amethyst:// links. */
interface LinkCommand {
    /** The parameters this command reads; the confirmation lists them, the others are ignored. */
    String[] parameters();

    /**
     * Checks the link and gathers what the confirmation shows (may use the network); runs off the UI thread and
     * must not change anything yet. A thrown exception's message is shown to the player.
     */
    LinkAction prepare(Activity activity, Uri link) throws Exception;
}
