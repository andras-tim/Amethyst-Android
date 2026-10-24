package net.kdt.pojavlaunch.packwiz;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** The hash formats packwiz writes ("hash-format" keys). */
public enum HashFormat {
    SHA1("sha1", "SHA-1"),
    SHA256("sha256", "SHA-256"),
    SHA512("sha512", "SHA-512"),
    MD5("md5", "MD5"),
    /** CurseForge fingerprint: 32-bit MurmurHash2 (seed 1) of the bytes without whitespace, as a decimal number. */
    MURMUR2("murmur2", null);

    private static final int BUFFER_SIZE = 64 * 1024;

    public final String id;
    private final String digestAlgorithm;

    HashFormat(String id, String digestAlgorithm) {
        this.id = id;
        this.digestAlgorithm = digestAlgorithm;
    }

    public static HashFormat fromId(String id) throws PackwizException {
        if (id != null) {
            for (HashFormat format : values()) {
                if (format.id.equals(id.toLowerCase(Locale.ROOT))) return format;
            }
        }
        throw new PackwizException("Unsupported hash format: " + id);
    }

    public boolean matches(String expected, String actual) {
        return expected != null && actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }

    public String hash(File file) throws IOException {
        if (this == MURMUR2) return Long.toString(murmur2(file));
        MessageDigest digest = newDigest();
        try (InputStream in = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return toHex(digest.digest());
    }

    public String hash(byte[] data) throws IOException {
        if (this == MURMUR2) return Long.toString(murmur2(data));
        return toHex(newDigest().digest(data));
    }

    private MessageDigest newDigest() throws IOException {
        try {
            return MessageDigest.getInstance(digestAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static boolean isWhitespace(int b) {
        return b == 9 || b == 10 || b == 13 || b == 32;
    }

    /** The length is part of the seed, so a file is read twice instead of being held in memory. */
    private static long murmur2(File file) throws IOException {
        int length = 0;
        try (InputStream in = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
            int b;
            while ((b = in.read()) != -1) if (!isWhitespace(b)) length++;
        }
        Murmur2 murmur = new Murmur2(length);
        try (InputStream in = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
            int b;
            while ((b = in.read()) != -1) if (!isWhitespace(b)) murmur.update(b);
        }
        return murmur.finish();
    }

    private static long murmur2(byte[] data) {
        int length = 0;
        for (byte b : data) if (!isWhitespace(b & 0xff)) length++;
        Murmur2 murmur = new Murmur2(length);
        for (byte b : data) if (!isWhitespace(b & 0xff)) murmur.update(b & 0xff);
        return murmur.finish();
    }

    private static final class Murmur2 {
        private static final int M = 0x5bd1e995;
        private static final int R = 24;
        private int hash;
        private int chunk;
        private int chunkSize;

        Murmur2(int length) {
            hash = 1 ^ length;
        }

        void update(int b) {
            chunk |= b << (8 * chunkSize);
            if (++chunkSize < 4) return;
            int k = chunk * M;
            k ^= k >>> R;
            k *= M;
            hash = hash * M ^ k;
            chunk = 0;
            chunkSize = 0;
        }

        long finish() {
            if (chunkSize > 0) {
                hash ^= chunk;
                hash *= M;
            }
            hash ^= hash >>> 13;
            hash *= M;
            hash ^= hash >>> 15;
            return hash & 0xffffffffL;
        }
    }
}
