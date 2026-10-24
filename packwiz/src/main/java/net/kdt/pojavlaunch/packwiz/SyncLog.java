package net.kdt.pojavlaunch.packwiz;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The log of one sync, kept in the instance so it can be shared after a failure; the one before is kept too. */
final class SyncLog implements Closeable {
    static final String FILE_NAME = "last-sync.log";
    static final String PREVIOUS_FILE_NAME = "previous-sync.log";

    private final PrintWriter writer;
    private final SyncListener listener;
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);

    private SyncLog(PrintWriter writer, SyncListener listener) {
        this.writer = writer;
        this.listener = listener;
    }

    static SyncLog open(File stateDir, SyncListener listener) throws IOException {
        File log = new File(stateDir, FILE_NAME);
        File previous = new File(stateDir, PREVIOUS_FILE_NAME);
        if (log.exists()) {
            //noinspection ResultOfMethodCallIgnored a missing previous log only loses history
            previous.delete();
            //noinspection ResultOfMethodCallIgnored
            log.renameTo(previous);
        }
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(log), StandardCharsets.UTF_8), true);
        writer.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(new Date()));
        return new SyncLog(writer, listener);
    }

    void line(String text) {
        writer.println(time.format(new Date()) + " " + text);
        listener.onLog(text);
    }

    void error(Throwable error) {
        line("FAILED: " + error.getMessage());
        error.printStackTrace(writer);
        writer.flush();
    }

    @Override
    public void close() {
        writer.close();
    }
}
