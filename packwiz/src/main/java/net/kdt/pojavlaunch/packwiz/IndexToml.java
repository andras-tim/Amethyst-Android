package net.kdt.pojavlaunch.packwiz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** index.toml: every file of the pack with its hash; metafiles (.pw.toml) stand for files downloaded elsewhere. */
public final class IndexToml {
    public final List<Entry> files;

    private IndexToml(List<Entry> files) {
        this.files = Collections.unmodifiableList(files);
    }

    public static final class Entry {
        /** Relative to index.toml. */
        public final String file;
        public final HashFormat hashFormat;
        public final String hash;
        /** Install under this path instead of {@link #file}; null when not set. */
        public final String alias;
        public final boolean metafile;
        /** Install once, then leave it to the player (configs the player may change). */
        public final boolean preserve;

        Entry(String file, HashFormat hashFormat, String hash, String alias, boolean metafile, boolean preserve) {
            this.file = file;
            this.hashFormat = hashFormat;
            this.hash = hash;
            this.alias = alias;
            this.metafile = metafile;
            this.preserve = preserve;
        }
    }

    public static IndexToml parse(String text, String url) throws PackwizException {
        TomlReader toml = TomlReader.parse(text, url);
        String defaultFormat = toml.requiredString("hash-format");
        List<Entry> files = new ArrayList<>();
        for (TomlReader file : toml.tables("files")) {
            String path = PackPaths.safeRelative(file.requiredString("file"));
            String alias = file.string("alias", null);
            files.add(new Entry(
                    path,
                    HashFormat.fromId(file.string("hash-format", defaultFormat)),
                    file.requiredString("hash"),
                    alias == null || alias.isEmpty() ? null : PackPaths.safeRelative(alias),
                    file.bool("metafile", false),
                    file.bool("preserve", false)));
        }
        return new IndexToml(files);
    }
}
