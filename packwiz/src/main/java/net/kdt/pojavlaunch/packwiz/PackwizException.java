package net.kdt.pojavlaunch.packwiz;

import java.io.IOException;

/**
 * A pack sync failure that names what failed, so the player (or whoever reads the log) can act on it.
 */
public class PackwizException extends IOException {
    /** Pack-relative path of the file involved, or null. */
    public final String path;
    /** URL involved, or null. */
    public final String url;

    public PackwizException(String message) {
        this(message, null, null, null);
    }

    public PackwizException(String message, String path, String url) {
        this(message, path, url, null);
    }

    public PackwizException(String message, String path, String url, Throwable cause) {
        super(describe(message, path, url), cause);
        this.path = path;
        this.url = url;
    }

    private static String describe(String message, String path, String url) {
        StringBuilder text = new StringBuilder(message);
        if (path != null) text.append("\nFile: ").append(path);
        if (url != null) text.append("\nURL: ").append(url);
        return text.toString();
    }
}
