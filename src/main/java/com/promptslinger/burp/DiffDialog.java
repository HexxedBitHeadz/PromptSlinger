package com.promptslinger.burp;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.promptslinger.burp.PSPanel.*;

public class DiffDialog extends JDialog {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final HistoryStore store;

    private JComboBox<EntryItem> comboA;
    private JComboBox<EntryItem> comboB;
    private JTextPane            paneA;
    private JTextPane            paneB;
    private JLabel               statsLabel;

    private static final Color REMOVED_BG = new Color(0x5c2020);
    private static final Color ADDED_BG   = new Color(0x1e4d1e);
    private static final Color SAME_FG    = new Color(0x888888);

    public DiffDialog(Component parent, HistoryStore store, HistoryEntry preselect) {
        super(SwingUtilities.getWindowAncestor(parent), "Response Diff",
              ModalityType.MODELESS);
        this.store = store;
        setSize(1000, 620);
        setLocationRelativeTo(parent);
        buildUI();
        populateCombos(preselect);
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);

        // Top: entry selectors
        JPanel topBar = new JPanel(new GridLayout(1, 2, 6, 0));
        topBar.setBackground(BG);
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));

        comboA = styledCombo();
        comboB = styledCombo();
        comboA.addActionListener(e -> compare());
        comboB.addActionListener(e -> compare());

        topBar.add(labeledCombo("Entry A:", comboA, ACCENT));
        topBar.add(labeledCombo("Entry B:", comboB, GREEN));
        root.add(topBar, BorderLayout.NORTH);

        // Centre: side-by-side diff panes
        paneA = diffPane();
        paneB = diffPane();

        JScrollPane scrollA = new JScrollPane(paneA);
        JScrollPane scrollB = new JScrollPane(paneB);
        scrollA.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 1, SURFACE));
        scrollB.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, SURFACE));

        // Sync scrolling between the two panes — guard prevents re-entrant feedback loop
        boolean[] syncing = {false};
        scrollA.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (syncing[0]) return;
            syncing[0] = true;
            scrollB.getVerticalScrollBar().setValue(e.getValue());
            syncing[0] = false;
        });
        scrollB.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (syncing[0]) return;
            syncing[0] = true;
            scrollA.getVerticalScrollBar().setValue(e.getValue());
            syncing[0] = false;
        });

        JLabel headerA = paneHeader("A — removed / unique", REMOVED_BG);
        JLabel headerB = paneHeader("B — added / unique",   ADDED_BG);

        JPanel panelA = new JPanel(new BorderLayout()); panelA.setBackground(BG);
        panelA.add(headerA, BorderLayout.NORTH); panelA.add(scrollA, BorderLayout.CENTER);

        JPanel panelB = new JPanel(new BorderLayout()); panelB.setBackground(BG);
        panelB.add(headerB, BorderLayout.NORTH); panelB.add(scrollB, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelA, panelB);
        split.setBackground(BG);
        split.setBorder(null);
        split.setDividerSize(4);
        split.setResizeWeight(0.5);
        root.add(split, BorderLayout.CENTER);

        // Bottom: stats + close
        JPanel bottomBar = new JPanel(new BorderLayout(8, 0));
        bottomBar.setBackground(BG);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));

        statsLabel = new JLabel(" ");
        statsLabel.setFont(new Font("Monospaced", Font.ITALIC, Math.max(BASE_SIZE - 2, 10)));
        statsLabel.setForeground(MUTED);
        bottomBar.add(statsLabel, BorderLayout.WEST);

        JButton closeBtn = new JButton("Close");
        closeBtn.setBackground(SURFACE);
        closeBtn.setForeground(MUTED);
        closeBtn.setFont(new Font("Monospaced", Font.BOLD, BASE_SIZE - 1));
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.addActionListener(e -> dispose());
        bottomBar.add(closeBtn, BorderLayout.EAST);
        root.add(bottomBar, BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ── Population ────────────────────────────────────────────────────────────

    private void populateCombos(HistoryEntry preselect) {
        List<HistoryEntry> entries = store.getEntries();
        comboA.removeAllItems();
        comboB.removeAllItems();

        int preselectIdx = -1;
        for (int i = 0; i < entries.size(); i++) {
            EntryItem item = new EntryItem(entries.get(i));
            comboA.addItem(item);
            comboB.addItem(item);
            if (entries.get(i) == preselect) preselectIdx = i;
        }

        if (preselectIdx >= 0) comboA.setSelectedIndex(preselectIdx);
        if (comboB.getItemCount() > 1)
            comboB.setSelectedIndex(preselectIdx >= 1 ? preselectIdx - 1 : 1);
    }

    // ── Diff computation ──────────────────────────────────────────────────────

    private void compare() {
        EntryItem a = (EntryItem) comboA.getSelectedItem();
        EntryItem b = (EntryItem) comboB.getSelectedItem();
        if (a == null || b == null) return;

        String textA = responseText(a.entry);
        String textB = responseText(b.entry);

        Set<String> setA = lineSet(textA);
        Set<String> setB = lineSet(textB);

        renderDiff(paneA, textA, setB, REMOVED_BG);
        renderDiff(paneB, textB, setA, ADDED_BG);

        // Stats
        long uniqueA = Arrays.stream(textA.split("\n", -1))
                .map(String::trim).filter(l -> !l.isEmpty() && !setB.contains(l)).count();
        long uniqueB = Arrays.stream(textB.split("\n", -1))
                .map(String::trim).filter(l -> !l.isEmpty() && !setA.contains(l)).count();
        statsLabel.setText(uniqueA + " lines unique to A   |   " + uniqueB + " lines unique to B");
    }

    private static String responseText(HistoryEntry e) {
        if (e.response == null) return "(no response)";
        // Try to extract the inner "response" value for cleaner diffs
        try {
            com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(e.response);
            if (root.has("response")) return root.get("response").asText();
        } catch (Exception ignored) {}
        return e.response;
    }

    private static Set<String> lineSet(String text) {
        Set<String> set = new HashSet<>();
        for (String line : text.split("\n", -1)) {
            String t = line.trim();
            if (!t.isEmpty()) set.add(t);
        }
        return set;
    }

    private void renderDiff(JTextPane pane, String text, Set<String> otherLines, Color uniqueColor) {
        StyledDocument doc = pane.getStyledDocument();
        try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {}

        Style base    = pane.addStyle("base",    null);
        Style unique  = pane.addStyle("unique",  base);
        Style common  = pane.addStyle("common",  base);
        StyleConstants.setFontFamily(base,   "Monospaced");
        StyleConstants.setFontSize(base,     BASE_SIZE - 1);
        StyleConstants.setBackground(unique, uniqueColor);
        StyleConstants.setForeground(unique, FG);
        StyleConstants.setForeground(common, SAME_FG);

        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            boolean isUnique = !line.trim().isEmpty() && !otherLines.contains(line.trim());
            Style style = isUnique ? unique : common;
            try {
                doc.insertString(doc.getLength(), line + "\n", style);
            } catch (BadLocationException ignored) {}
        }
        pane.setCaretPosition(0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JTextPane diffPane() {
        JTextPane p = new JTextPane();
        p.setEditable(false);
        p.setBackground(SURFACE);
        p.setForeground(FG);
        p.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        p.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return p;
    }

    private static JLabel paneHeader(String text, Color bg) {
        JLabel l = new JLabel("  " + text);
        l.setFont(new Font("Monospaced", Font.BOLD, Math.max(BASE_SIZE - 2, 10)));
        l.setForeground(FG);
        l.setBackground(bg);
        l.setOpaque(true);
        l.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return l;
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<EntryItem> styledCombo() {
        JComboBox<EntryItem> cb = new JComboBox<>();
        cb.setBackground(SURFACE);
        cb.setForeground(FG);
        cb.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        return cb;
    }

    private static JPanel labeledCombo(String label, JComboBox<EntryItem> combo, Color labelColor) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setBackground(BG);
        JLabel l = new JLabel(label);
        l.setForeground(labelColor);
        l.setFont(new Font("Monospaced", Font.BOLD, BASE_SIZE - 1));
        p.add(l, BorderLayout.WEST);
        p.add(combo, BorderLayout.CENTER);
        return p;
    }

    // ── EntryItem wrapper ─────────────────────────────────────────────────────

    private static class EntryItem {
        final HistoryEntry entry;
        EntryItem(HistoryEntry e) { this.entry = e; }

        @Override public String toString() {
            String preview = entry.message == null ? "" : entry.message.replace("\n", " ");
            if (preview.length() > 45) preview = preview.substring(0, 45) + "…";
            String mark = entry.mark != null ? "[" + entry.mark + "] " : "";
            return entry.timestamp + "  " + mark + preview;
        }
    }
}
