package net.kdt.pojavlaunch.packwiz;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Builds a packwiz pack the way the packwiz CLI writes it and publishes it on a {@link FakeFetcher}. */
final class TestPack {
    static final String BASE = "https://example.org/pack/";
    static final String PACK_URL = BASE + "pack.toml";
    static final String CDN = "https://cdn.example.org/files/";

    private final List<String> indexEntries = new ArrayList<>();
    private final FakeFetcher fetcher;
    private final List<String[]> served = new ArrayList<>();
    String version = "1";

    TestPack(FakeFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /** A file listed in the index itself (configs, datapacks). */
    TestPack file(String path, String content, boolean preserve) throws IOException {
        serve(BASE + PackPaths.encodePath(path), content);
        indexEntries.add("[[files]]\nfile = \"" + path + "\"\nhash = \"" + sha256(content) + "\"\n"
                + (preserve ? "preserve = true\n" : ""));
        return this;
    }

    /** A mod: a metafile in the index that points at a jar on the CDN. */
    TestPack mod(String metaPath, String filename, String content, String side) throws IOException {
        String jarUrl = CDN + filename.replace(" ", "%20");
        serve(jarUrl, content);
        return metafile(metaPath, "name = \"" + filename + "\"\nfilename = \"" + filename + "\"\nside = \"" + side + "\"\n\n"
                + "[download]\nurl = \"" + CDN + filename + "\"\nhash-format = \"sha1\"\nhash = \""
                + HashFormat.SHA1.hash(bytes(content)) + "\"\n");
    }

    TestPack metafile(String metaPath, String toml) throws IOException {
        serve(BASE + PackPaths.encodePath(metaPath), toml);
        indexEntries.add("[[files]]\nfile = \"" + metaPath + "\"\nhash = \"" + sha256(toml) + "\"\nmetafile = true\n");
        return this;
    }

    void publish() throws IOException {
        StringBuilder index = new StringBuilder("hash-format = \"sha256\"\n");
        for (String entry : indexEntries) index.append('\n').append(entry);
        serve(BASE + "index.toml", index.toString());
        serve(PACK_URL, "name = \"Test Pack\"\nversion = \"" + version + "\"\npack-format = \"packwiz:1.1.0\"\n\n"
                + "[index]\nfile = \"index.toml\"\nhash-format = \"sha256\"\nhash = \"" + sha256(index.toString()) + "\"\n\n"
                + "[versions]\nminecraft = \"1.20.1\"\nforge = \"47.4.20\"\n");
        fetcher.responses.clear();
        for (String[] item : served) fetcher.responses.put(item[0], bytes(item[1]));
    }

    private void serve(String url, String content) {
        served.add(new String[]{url, content});
    }

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static String sha256(String text) throws IOException {
        return HashFormat.SHA256.hash(bytes(text));
    }
}
