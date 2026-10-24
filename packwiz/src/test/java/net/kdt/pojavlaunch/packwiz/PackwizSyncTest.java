package net.kdt.pojavlaunch.packwiz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

public class PackwizSyncTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File instance;
    private FakeFetcher fetcher;

    @Before
    public void setUp() throws IOException {
        instance = temp.newFolder("instance");
        fetcher = new FakeFetcher();
    }

    private SyncResult sync() throws IOException {
        PackwizSync sync = new PackwizSync(instance, TestPack.PACK_URL, fetcher, MetaFile.Side.CLIENT, SyncListener.NONE);
        sync.retryDelayMs = 0;
        return sync.run();
    }

    private TestPack basePack() throws IOException {
        return new TestPack(fetcher)
                .file("config/create-common.toml", "disableWorldGen = false\n", false)
                .file("config/options-default.toml", "fov = 70\n", true)
                .mod("mods/create.pw.toml", "create-6.0.8.jar", "create jar", "both")
                .mod("mods/spark.pw.toml", "spark-server.jar", "server only", "server");
    }

    @Test
    public void installsPackFilesForTheClientSide() throws IOException {
        basePack().publish();

        SyncResult result = sync();

        assertFalse(result.upToDate);
        assertEquals("disableWorldGen = false\n", read("config/create-common.toml"));
        assertEquals("fov = 70\n", read("config/options-default.toml"));
        assertEquals("create jar", read("mods/create-6.0.8.jar"));
        assertFalse(new File(instance, "mods/spark-server.jar").exists());
        assertEquals(3, result.installed.size());
        assertEquals("1.20.1", result.pack.minecraftVersion());
        assertEquals("forge", result.pack.loader());
        assertEquals("47.4.20", result.pack.loaderVersion());
        assertTrue(PackwizSync.logFile(instance).isFile());
    }

    @Test
    public void unchangedPackOnlyDownloadsPackToml() throws IOException {
        basePack().publish();
        sync();
        fetcher.requests.clear();

        SyncResult result = sync();

        assertTrue(result.upToDate);
        assertEquals(Collections.singletonList(TestPack.PACK_URL), fetcher.requests);
    }

    @Test
    public void updatedModReplacesTheOldJarAndReusesKnownMetafiles() throws IOException {
        basePack().publish();
        sync();
        new TestPack(fetcher)
                .file("config/create-common.toml", "disableWorldGen = false\n", false)
                .file("config/options-default.toml", "fov = 70\n", true)
                .mod("mods/create.pw.toml", "create-6.0.9.jar", "newer create jar", "both")
                .mod("mods/spark.pw.toml", "spark-server.jar", "server only", "server")
                .publish();
        fetcher.requests.clear();

        SyncResult result = sync();

        assertEquals(Collections.singletonList("mods/create-6.0.9.jar"), result.installed);
        assertEquals(Collections.singletonList("mods/create-6.0.8.jar"), result.deleted);
        assertFalse(new File(instance, "mods/create-6.0.8.jar").exists());
        assertEquals("newer create jar", read("mods/create-6.0.9.jar"));
        assertEquals("the server-only metafile is remembered", 0, fetcher.requestsFor("spark.pw.toml"));
        assertEquals(0, fetcher.requestsFor("create-common.toml"));
    }

    @Test
    public void changedPackFileIsRestored() throws IOException {
        basePack().publish();
        sync();
        write("mods/create-6.0.8.jar", "corrupted by hand");

        SyncResult result = sync();

        assertFalse(result.upToDate);
        assertEquals(Collections.singletonList("mods/create-6.0.8.jar"), result.installed);
        assertEquals("create jar", read("mods/create-6.0.8.jar"));
    }

    @Test
    public void preservedFileChangedByThePlayerIsKept() throws IOException {
        basePack().publish();
        sync();
        write("config/options-default.toml", "fov = 110\n");
        fetcher.requests.clear();

        SyncResult result = sync();

        assertTrue("a preserved file does not force a full check", result.upToDate);
        assertEquals("fov = 110\n", read("config/options-default.toml"));
    }

    @Test
    public void droppedFileIsRemovedUnlessThePlayerChangedIt() throws IOException {
        basePack().publish();
        sync();
        write("config/create-common.toml", "disableWorldGen = true\n");
        new TestPack(fetcher).publish();

        SyncResult result = sync();

        assertTrue(result.deleted.contains("mods/create-6.0.8.jar"));
        assertTrue(result.deleted.contains("config/options-default.toml"));
        assertEquals(Collections.singletonList("config/create-common.toml"), result.keptChanged);
        assertEquals("disableWorldGen = true\n", read("config/create-common.toml"));
    }

    @Test
    public void playerFilesOutsideThePackAreNeverTouched() throws IOException {
        write("mods/own-mod.jar", "mine");
        write("saves/World/level.dat", "world");
        basePack().publish();
        sync();
        new TestPack(fetcher).publish();

        sync();

        assertEquals("mine", read("mods/own-mod.jar"));
        assertEquals("world", read("saves/World/level.dat"));
    }

    @Test
    public void fileAlreadyMatchingThePackIsNotDownloaded() throws IOException {
        write("mods/create-6.0.8.jar", "create jar");
        basePack().publish();

        SyncResult result = sync();

        assertFalse(result.installed.contains("mods/create-6.0.8.jar"));
        assertEquals(0, fetcher.requestsFor("create-6.0.8.jar"));
    }

    @Test
    public void wrongDownloadFailsWithFileAndUrlAndRecoversLater() throws IOException {
        basePack().publish();
        String jarUrl = TestPack.CDN + "create-6.0.8.jar";
        fetcher.responses.put(jarUrl, TestPack.bytes("tampered"));

        try {
            sync();
            fail("expected a hash mismatch");
        } catch (PackwizException e) {
            assertEquals("mods/create-6.0.8.jar", e.path);
            assertEquals(jarUrl, e.url);
        }
        assertEquals("tried three times", 3, fetcher.requestsFor("create-6.0.8.jar"));
        assertFalse(new File(instance, "mods/create-6.0.8.jar").exists());
        assertFalse(new File(instance, "mods/create-6.0.8.jar.packwiz-part").exists());
        assertTrue(read(".packwiz/last-sync.log").contains("FAILED"));

        fetcher.responses.put(jarUrl, TestPack.bytes("create jar"));
        SyncResult result = sync();
        assertEquals("create jar", read("mods/create-6.0.8.jar"));
        assertTrue(result.installed.contains("mods/create-6.0.8.jar"));
    }

    @Test
    public void corruptStateIsSetAsideAndFilesAreCheckedAgain() throws IOException {
        basePack().publish();
        sync();
        write(".packwiz/state.toml", "this is [not toml");
        fetcher.requests.clear();

        SyncResult result = sync();

        assertTrue(new File(instance, ".packwiz/state.toml.corrupt").isFile());
        assertTrue("intact files are adopted, not downloaded again", result.installed.isEmpty());
        assertTrue(sync().upToDate);
    }

    @Test
    public void pathsWithSpacesAreEncodedInUrls() throws IOException {
        new TestPack(fetcher).mod("mods/My Mod.pw.toml", "My Mod 1.0.jar", "spaced", "both").publish();

        sync();

        assertEquals("spaced", read("mods/My Mod 1.0.jar"));
        assertTrue(fetcher.requests.contains(TestPack.BASE + "mods/My%20Mod.pw.toml"));
        assertTrue(fetcher.requests.contains(TestPack.CDN + "My%20Mod%201.0.jar"));
    }

    @Test
    public void pathOutsideTheInstanceIsRejected() throws IOException {
        new TestPack(fetcher).file("../escape.txt", "x", false).publish();

        try {
            sync();
            fail("expected the path to be rejected");
        } catch (PackwizException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(".."));
        }
        assertFalse(new File(temp.getRoot(), "escape.txt").exists());
    }

    @Test
    public void curseforgeApiModeIsRejectedWithTheModName() throws IOException {
        new TestPack(fetcher).metafile("mods/locked.pw.toml", "name = \"Locked Mod\"\nfilename = \"locked.jar\"\n\n"
                + "[download]\nhash-format = \"sha1\"\nhash = \"00\"\nmode = \"metadata:curseforge\"\n").publish();

        try {
            sync();
            fail("expected an unsupported mode");
        } catch (PackwizException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Locked Mod"));
            assertEquals("mods/locked.pw.toml", e.path);
        }
    }

    @Test
    public void missingServerFileNamesTheUrl() throws IOException {
        try {
            sync();
            fail("expected a 404");
        } catch (PackwizException e) {
            assertEquals(TestPack.PACK_URL, e.url);
            assertTrue(e.getMessage(), e.getMessage().contains("404"));
        }
    }

    @Test
    public void serverSideGetsServerFilesOnly() throws IOException {
        basePack().publish();
        PackwizSync sync = new PackwizSync(instance, TestPack.PACK_URL, fetcher, MetaFile.Side.SERVER, SyncListener.NONE);

        SyncResult result = sync.run();

        assertTrue(new File(instance, "mods/spark-server.jar").isFile());
        assertEquals(Arrays.asList("config/create-common.toml", "config/options-default.toml",
                "mods/create-6.0.8.jar", "mods/spark-server.jar"), sorted(result.installed));
    }

    private static java.util.List<String> sorted(java.util.List<String> items) {
        java.util.List<String> copy = new java.util.ArrayList<>(items);
        Collections.sort(copy);
        return copy;
    }

    private String read(String path) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(new File(instance, path).toPath()), StandardCharsets.UTF_8);
    }

    private void write(String path, String content) throws IOException {
        File file = new File(instance, path);
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        // a same-length edit must still look changed
        //noinspection ResultOfMethodCallIgnored
        file.setLastModified(file.lastModified() + 2000);
    }
}
