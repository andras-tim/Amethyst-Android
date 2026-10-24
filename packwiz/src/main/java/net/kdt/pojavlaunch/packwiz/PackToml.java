package net.kdt.pojavlaunch.packwiz;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** pack.toml: the pack's name, version, game and loader versions, and where its index is. */
public final class PackToml {
    public final String name;
    public final String version;
    public final String author;
    /** Relative to pack.toml. */
    public final String indexFile;
    public final HashFormat indexHashFormat;
    public final String indexHash;
    /** "minecraft" plus at most one loader: "forge", "neoforge", "fabric" or "quilt". */
    public final Map<String, String> versions;

    private PackToml(String name, String version, String author, String indexFile, HashFormat indexHashFormat,
                     String indexHash, Map<String, String> versions) {
        this.name = name;
        this.version = version;
        this.author = author;
        this.indexFile = indexFile;
        this.indexHashFormat = indexHashFormat;
        this.indexHash = indexHash;
        this.versions = Collections.unmodifiableMap(new LinkedHashMap<>(versions));
    }

    public static PackToml parse(String text, String url) throws PackwizException {
        TomlReader toml = TomlReader.parse(text, url);
        String packFormat = toml.string("pack-format", "packwiz:1.0.0");
        if (!packFormat.startsWith("packwiz:1.")) {
            throw new PackwizException("Unsupported pack format \"" + packFormat + "\"", "pack.toml", url);
        }
        PackToml pack = new PackToml(
                toml.requiredString("name"),
                toml.string("version", ""),
                toml.string("author", ""),
                PackPaths.safeRelative(toml.requiredString("index.file")),
                HashFormat.fromId(toml.requiredString("index.hash-format")),
                toml.requiredString("index.hash"),
                toml.stringTable("versions"));
        if (pack.minecraftVersion() == null) throw new PackwizException("[versions] has no minecraft version", "pack.toml", url);
        return pack;
    }

    public String minecraftVersion() {
        return versions.get("minecraft");
    }

    /** The loader name ("forge", ...) or null for a vanilla pack. */
    public String loader() {
        for (String key : versions.keySet()) {
            if (!key.equals("minecraft")) return key;
        }
        return null;
    }

    public String loaderVersion() {
        String loader = loader();
        return loader == null ? null : versions.get(loader);
    }

    /** "name version" for logs and dialogs. */
    public String title() {
        return version.isEmpty() ? name : name + " " + version;
    }
}
