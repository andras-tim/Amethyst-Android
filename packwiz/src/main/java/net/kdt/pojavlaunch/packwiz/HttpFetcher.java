package net.kdt.pojavlaunch.packwiz;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/** Plain HttpURLConnection; redirects are followed by hand, because it never follows one from http to https. */
public final class HttpFetcher implements Fetcher {
    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    private final String userAgent;

    public HttpFetcher(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public Download open(String url) throws IOException {
        String current = url;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", userAgent);
            // pack.toml and index.toml change in place; ask caches for a fresh copy
            connection.setRequestProperty("Cache-Control", "no-cache");
            int code;
            try {
                code = connection.getResponseCode();
            } catch (IOException e) {
                connection.disconnect();
                throw new PackwizException("Cannot reach the server: " + e.getMessage(), null, current, e);
            }
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new PackwizException("Redirect without a target (HTTP " + code + ")", null, current);
                current = new URL(new URL(current), location).toString();
                continue;
            }
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                throw new PackwizException("The server answered HTTP " + code, null, current);
            }
            return new Download(connection.getInputStream(), contentLength(connection));
        }
        throw new PackwizException("Too many redirects", null, url);
    }

    private static long contentLength(HttpURLConnection connection) {
        String header = connection.getHeaderField("Content-Length");
        if (header == null) return -1;
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
