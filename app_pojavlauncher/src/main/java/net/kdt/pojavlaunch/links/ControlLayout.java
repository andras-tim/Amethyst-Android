package net.kdt.pojavlaunch.links;

import net.kdt.pojavlaunch.Tools;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/** The downloaded control layout of a link, saved once the link is confirmed. */
final class ControlLayout {
    /** Keeps downloads apart from the player's own layouts, which a link never overwrites. */
    static final String PREFIX = "link-";

    final String file;
    final String url;
    private final byte[] mLayout;

    ControlLayout(String file, String url, byte[] layout) {
        this.file = file;
        this.url = url;
        mLayout = layout;
    }

    /** @return whether saving would change the file */
    boolean changes() throws IOException {
        File target = new File(Tools.CTRLMAP_PATH, file);
        return !target.isFile() || !Arrays.equals(FileUtils.readFileToByteArray(target), mLayout);
    }

    void save() throws IOException {
        FileUtils.writeByteArrayToFile(new File(Tools.CTRLMAP_PATH, file), mLayout);
    }
}
