package net.kdt.pojavlaunch.packwiz;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Serves URLs from memory and records every request. */
final class FakeFetcher implements Fetcher {
    final Map<String, byte[]> responses = new HashMap<>();
    final List<String> requests = new ArrayList<>();

    @Override
    public Download open(String url) throws PackwizException {
        requests.add(url);
        byte[] body = responses.get(url);
        if (body == null) throw new PackwizException("The server answered HTTP 404", null, url);
        return new Download(new ByteArrayInputStream(body), body.length);
    }

    int requestsFor(String urlPart) {
        int count = 0;
        for (String url : requests) if (url.contains(urlPart)) count++;
        return count;
    }
}
