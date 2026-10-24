package net.kdt.pojavlaunch.packwiz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class PackPathsTest {
    @Test
    public void relativePathsAreNormalized() throws PackwizException {
        assertEquals("mods/a.jar", PackPaths.safeRelative("mods/a.jar"));
        assertEquals("mods/a.jar", PackPaths.safeRelative("./mods\\a.jar"));
    }

    @Test
    public void escapingPathsAreRejected() {
        for (String path : new String[]{"", "/etc/passwd", "C:/x", "../x", "mods/../../x", "mods//a.jar", "mods/./a"}) {
            try {
                PackPaths.safeRelative(path);
                fail("accepted " + path);
            } catch (PackwizException expected) {
                // expected
            }
        }
    }

    @Test
    public void relativeUrlsKeepFileNameCharacters() throws PackwizException {
        assertEquals("https://example.org/pack/mods/My%20Mod%20%231%3F.pw.toml",
                PackPaths.resolveUrl("https://example.org/pack/index.toml", "mods/My Mod #1?.pw.toml"));
        assertEquals("https://raw.githubusercontent.com/o/r/v1/index.toml",
                PackPaths.resolveUrl("https://raw.githubusercontent.com/o/r/v1/pack.toml", "index.toml"));
    }

    @Test
    public void downloadUrlsAreEncodedButExistingEscapesKept() throws PackwizException {
        assertEquals("https://cdn.example.org/a%20b%2Bc.jar", PackPaths.checkUrl("https://cdn.example.org/a b%2Bc.jar", "p"));
    }

    @Test(expected = PackwizException.class)
    public void nonHttpDownloadsAreRejected() throws PackwizException {
        PackPaths.checkUrl("file:///sdcard/evil.jar", "mods/evil.jar");
    }
}
