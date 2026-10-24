package net.kdt.pojavlaunch.packwiz;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What a sync did, for the summary shown to the player. */
public final class SyncResult {
    public final PackToml pack;
    /** Nothing changed since the last sync; only pack.toml was downloaded. */
    public final boolean upToDate;
    public final List<String> installed;
    public final List<String> deleted;
    /** Files the pack no longer lists, kept because the player changed them. */
    public final List<String> keptChanged;
    public final File logFile;

    SyncResult(PackToml pack, boolean upToDate, List<String> installed, List<String> deleted, List<String> keptChanged,
               File logFile) {
        this.pack = pack;
        this.upToDate = upToDate;
        this.installed = Collections.unmodifiableList(new ArrayList<>(installed));
        this.deleted = Collections.unmodifiableList(new ArrayList<>(deleted));
        this.keptChanged = Collections.unmodifiableList(new ArrayList<>(keptChanged));
        this.logFile = logFile;
    }
}
