package com.promptslinger.burp;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.*;
import java.util.*;

public class KeywordAlerts {

    public static class Alert {
        public String  keyword;
        public String  markKey;
        public boolean caseInsensitive = true;

        public Alert() {}
        public Alert(String keyword, String markKey, boolean caseInsensitive) {
            this.keyword         = keyword;
            this.markKey         = markKey;
            this.caseInsensitive = caseInsensitive;
        }

        @Override public String toString() {
            return (caseInsensitive ? "[i] " : "     ") + keyword + "  →  " + markKey;
        }
    }

    private static final Path FILE = Paths.get(
            System.getProperty("user.home"), ".promptslinger", "keyword_alerts.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static List<Alert> alerts = new ArrayList<>();

    static { load(); }

    public static List<Alert> getAlerts() {
        return Collections.unmodifiableList(alerts);
    }

    public static void setAlerts(List<Alert> updated) {
        alerts = new ArrayList<>(updated);
        save();
    }

    /** Returns the markKey of the first matching alert, or null if none match. */
    public static String check(String responseText) {
        if (responseText == null || responseText.isBlank()) return null;
        for (Alert a : alerts) {
            if (a.keyword == null || a.keyword.isBlank()) continue;
            String text = a.caseInsensitive ? responseText.toLowerCase() : responseText;
            String kw   = a.caseInsensitive ? a.keyword.toLowerCase()    : a.keyword;
            if (text.contains(kw)) return a.markKey;
        }
        return null;
    }

    static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            MAPPER.writeValue(FILE.toFile(), alerts);
        } catch (Exception e) {
            System.err.println("[PromptSlinger] Failed to save keyword alerts: " + e.getMessage());
        }
    }

    private static void load() {
        try {
            if (Files.exists(FILE))
                alerts = new ArrayList<>(Arrays.asList(MAPPER.readValue(FILE.toFile(), Alert[].class)));
        } catch (Exception e) {
            System.err.println("[PromptSlinger] Failed to load keyword alerts: " + e.getMessage());
        }
    }
}
