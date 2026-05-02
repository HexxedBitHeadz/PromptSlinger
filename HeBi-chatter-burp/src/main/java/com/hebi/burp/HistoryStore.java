package com.hebi.burp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HistoryStore {

    private static final int MAX = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final File file;
    private final List<HistoryEntry> entries = new ArrayList<>();

    public HistoryStore(File storageDir) {
        storageDir.mkdirs();
        this.file = new File(storageDir, "promptslinger_history.json");
        load();
    }

    public synchronized void add(HistoryEntry entry) {
        entries.add(0, entry);
        if (entries.size() > MAX) entries.remove(entries.size() - 1);
        save();
    }

    public synchronized List<HistoryEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public synchronized void clearAll() {
        entries.clear();
        save();
    }

    public synchronized void save() {
        try {
            MAPPER.writeValue(file, entries);
        } catch (IOException ignored) {}
    }

    private void load() {
        if (!file.exists()) return;
        try {
            HistoryEntry[] loaded = MAPPER.readValue(file, HistoryEntry[].class);
            entries.addAll(Arrays.asList(loaded));
        } catch (IOException ignored) {}
    }
}
