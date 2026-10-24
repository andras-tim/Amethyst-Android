package net.kdt.pojavlaunch.packwiz;

/** Progress of a sync, for the launcher's progress bar; every line also goes to the sync log. */
public interface SyncListener {
    SyncListener NONE = new SyncListener() {};

    /** A step of the sync, e.g. "Checking the pack". */
    default void onStage(String text) {}

    /**
     * @param index 1-based number of the file being downloaded
     * @param totalBytes size of the file, -1 when the server did not tell
     */
    default void onDownload(int index, int count, String path, long bytes, long totalBytes) {}

    default void onLog(String line) {}
}
