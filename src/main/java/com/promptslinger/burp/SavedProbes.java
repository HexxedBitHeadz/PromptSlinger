package com.promptslinger.burp;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.*;
import java.util.*;

public class SavedProbes {

    public static class Probe {
        public String name;
        public String text;

        public Probe() {}
        public Probe(String name, String text) { this.name = name; this.text = text; }

        @Override public String toString() { return name; }
    }

    private static final Path FILE = Paths.get(
            System.getProperty("user.home"), ".promptslinger", "saved_probes.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static List<Probe> probes = new ArrayList<>();

    static { load(); }

    public static List<Probe> getAll() {
        return Collections.unmodifiableList(probes);
    }

    public static void add(String name, String text) {
        probes.add(new Probe(name, text));
        save();
    }

    public static void remove(int idx) {
        if (idx >= 0 && idx < probes.size()) { probes.remove(idx); save(); }
    }

    public static void rename(int idx, String newName) {
        if (idx >= 0 && idx < probes.size()) { probes.get(idx).name = newName; save(); }
    }

    static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            MAPPER.writeValue(FILE.toFile(), probes);
        } catch (Exception e) {
            System.err.println("[PromptSlinger] Failed to save probes: " + e.getMessage());
        }
    }

    private static void load() {
        try {
            if (Files.exists(FILE))
                probes = new ArrayList<>(Arrays.asList(MAPPER.readValue(FILE.toFile(), Probe[].class)));
        } catch (Exception e) {
            System.err.println("[PromptSlinger] Failed to load probes: " + e.getMessage());
        }
    }
}
