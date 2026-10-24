package net.kdt.pojavlaunch.packwiz;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Pack-relative paths and URLs: the only places where text from a pack becomes a file or a request. */
public final class PackPaths {
    private PackPaths() {}

    /**
     * Normalizes a pack-relative path ("mods/a.jar") and rejects anything that could leave the instance directory:
     * absolute paths, drive letters, "." and ".." segments.
     */
    public static String safeRelative(String path) throws PackwizException {
        if (path == null) throw new PackwizException("A pack file has no path");
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        if (normalized.isEmpty() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new PackwizException("Pack path is not relative", path, null);
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new PackwizException("Pack path has an empty, \".\" or \"..\" segment", path, null);
            }
        }
        return normalized;
    }

    /** The file for a pack-relative path, checked to stay inside the instance even through symlinks. */
    public static File resolveInside(File instanceDir, String relativePath) throws IOException {
        String safe = safeRelative(relativePath);
        File target = new File(instanceDir, safe);
        String root = instanceDir.getCanonicalPath() + File.separator;
        if (!target.getCanonicalPath().startsWith(root)) {
            throw new PackwizException("Pack path points outside the instance", relativePath, null);
        }
        return target;
    }

    /** Directory part of a pack-relative path, "" for a top-level file. */
    public static String parent(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash < 0 ? "" : relativePath.substring(0, slash);
    }

    public static String join(String directory, String name) {
        return directory.isEmpty() ? name : directory + "/" + name;
    }

    /** Resolves a pack-relative path (unencoded, as packwiz writes it) against the URL of the file that lists it. */
    public static String resolveUrl(String baseUrl, String relativePath) throws PackwizException {
        try {
            return new URI(encode(baseUrl)).resolve(encodePath(relativePath)).toString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new PackwizException("Cannot build a URL for a pack file", relativePath, baseUrl, e);
        }
    }

    /** Checks an absolute download URL and encodes characters packwiz leaves raw (e.g. spaces in file names). */
    public static String checkUrl(String url, String path) throws PackwizException {
        if (url == null || url.trim().isEmpty()) throw new PackwizException("Pack file has no download URL", path, null);
        String encoded = encode(url.trim());
        String scheme = encoded.contains(":") ? encoded.substring(0, encoded.indexOf(':')).toLowerCase(Locale.ROOT) : "";
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new PackwizException("Only http and https downloads are allowed", path, url);
        }
        try {
            new URI(encoded);
        } catch (URISyntaxException e) {
            throw new PackwizException("Download URL is not valid", path, url, e);
        }
        return encoded;
    }

    /** Percent-encodes characters that are not legal in a URI; existing %XX escapes are kept. */
    static String encode(String text) {
        return encode(text, URI_CHARS);
    }

    /** Like {@link #encode(String)}, but for a relative path: "?", "#" and ":" are part of a file name there. */
    static String encodePath(String text) {
        return encode(text, PATH_CHARS);
    }

    private static final String URI_CHARS = "-._~:/?#[]@!$&'()*+,;=";
    private static final String PATH_CHARS = "-._~/@!$&'()*+,;=";

    private static String encode(String text, String allowed) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean escape = c == '%' && i + 2 < text.length() && isHex(text.charAt(i + 1)) && isHex(text.charAt(i + 2));
            if (escape || isAllowed(c, allowed)) {
                out.append(c);
                continue;
            }
            int end = Character.isHighSurrogate(c) && i + 1 < text.length() ? i + 2 : i + 1;
            for (byte b : text.substring(i, end).getBytes(StandardCharsets.UTF_8)) {
                out.append('%').append(String.format("%02X", b & 0xff));
            }
            i = end - 1;
        }
        return out.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isAllowed(char c, String allowed) {
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return true;
        return allowed.indexOf(c) >= 0;
    }
}
