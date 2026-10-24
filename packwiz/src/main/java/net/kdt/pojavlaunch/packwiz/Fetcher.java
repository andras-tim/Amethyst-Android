package net.kdt.pojavlaunch.packwiz;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/** Opens URLs for the sync; tests replace it with an in-memory one. */
public interface Fetcher {
    /** Opens the URL, following redirects; a non-success answer is a {@link PackwizException}. */
    Download open(String url) throws IOException;

    final class Download implements Closeable {
        public final InputStream body;
        /** Content length in bytes, -1 when unknown. */
        public final long length;

        public Download(InputStream body, long length) {
            this.body = body;
            this.length = length;
        }

        @Override
        public void close() throws IOException {
            body.close();
        }
    }
}
