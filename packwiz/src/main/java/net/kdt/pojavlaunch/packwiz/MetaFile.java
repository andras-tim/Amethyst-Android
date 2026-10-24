package net.kdt.pojavlaunch.packwiz;

import java.util.Locale;

/** A .pw.toml metafile: one file the pack downloads from elsewhere (a mod from CurseForge, Modrinth, ...). */
public final class MetaFile {
    public final String name;
    /** Installed next to the metafile under this name. */
    public final String filename;
    public final Side side;
    public final String url;
    public final HashFormat hashFormat;
    public final String hash;
    public final boolean optional;
    /** Whether an optional file is installed when nobody chose. */
    public final boolean optionalDefault;

    private MetaFile(String name, String filename, Side side, String url, HashFormat hashFormat, String hash,
                     boolean optional, boolean optionalDefault) {
        this.name = name;
        this.filename = filename;
        this.side = side;
        this.url = url;
        this.hashFormat = hashFormat;
        this.hash = hash;
        this.optional = optional;
        this.optionalDefault = optionalDefault;
    }

    /**
     * @param path pack-relative path of the metafile, for error messages
     */
    public static MetaFile parse(String text, String url, String path) throws PackwizException {
        TomlReader toml = TomlReader.parse(text, url);
        String name = toml.string("name", path);
        String filename = toml.requiredString("filename");
        if (filename.contains("/") || filename.contains("\\")) {
            throw new PackwizException("\"filename\" must not contain a directory", path, url);
        }
        String mode = toml.string("download.mode", "url");
        if (!mode.isEmpty() && !mode.equals("url")) {
            // "metadata:curseforge" needs the CurseForge API; packwiz only writes it when the author forbids
            // third-party downloads, and such a file must be hosted by the pack itself
            throw new PackwizException("\"" + name + "\" uses download mode \"" + mode + "\", which this launcher"
                    + " does not support: the pack has to give a direct URL for it", path, url);
        }
        return new MetaFile(
                name,
                filename,
                Side.fromId(toml.string("side", "both"), path),
                PackPaths.checkUrl(toml.requiredString("download.url"), path),
                HashFormat.fromId(toml.requiredString("download.hash-format")),
                toml.requiredString("download.hash"),
                toml.bool("option.optional", false),
                toml.bool("option.default", false));
    }

    public enum Side {
        CLIENT, SERVER, BOTH;

        static Side fromId(String id, String path) throws PackwizException {
            switch (id.toLowerCase(Locale.ROOT)) {
                case "client": return CLIENT;
                case "server": return SERVER;
                case "both": case "": return BOTH;
                default: throw new PackwizException("Unknown side \"" + id + "\"", path, null);
            }
        }

        public boolean includes(Side target) {
            return this == BOTH || this == target;
        }
    }
}
