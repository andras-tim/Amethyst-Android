package net.kdt.pojavlaunch.packwiz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class HashFormatTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    // expected values from an independent Python implementation (hashlib, CurseForge murmur2)
    @Test
    public void digestsMatchReferenceValues() throws IOException {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", HashFormat.SHA1.hash(TestPack.bytes("abc")));
        assertTrue(HashFormat.SHA256.hash(TestPack.bytes("abc")).startsWith("ba7816bf8f01cfea"));
    }

    @Test
    public void murmur2IgnoresWhitespaceLikeCurseForge() throws IOException {
        assertEquals("1540447798", HashFormat.MURMUR2.hash(new byte[0]));
        assertEquals("1621425345", HashFormat.MURMUR2.hash(TestPack.bytes("abc")));
        assertEquals("1621425345", HashFormat.MURMUR2.hash(TestPack.bytes("a b\tc\r\n")));
        assertEquals("2970575058", HashFormat.MURMUR2.hash(TestPack.bytes("Hello, packwiz!")));
        byte[] all = new byte[256];
        for (int i = 0; i < all.length; i++) all[i] = (byte) i;
        assertEquals("2094645347", HashFormat.MURMUR2.hash(all));
    }

    @Test
    public void fileAndMemoryHashesAgree() throws IOException {
        byte[] data = TestPack.bytes("Hello, packwiz!");
        File file = temp.newFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
        for (HashFormat format : HashFormat.values()) {
            assertEquals(format.id, format.hash(data), format.hash(file));
        }
    }

    @Test
    public void formatIdsAreCaseInsensitiveAndHashesToo() throws PackwizException {
        assertEquals(HashFormat.SHA512, HashFormat.fromId("SHA512"));
        assertTrue(HashFormat.SHA1.matches("ABCDEF", "abcdef"));
    }

    @Test(expected = PackwizException.class)
    public void unknownFormatIsAnError() throws PackwizException {
        HashFormat.fromId("crc32");
    }
}
