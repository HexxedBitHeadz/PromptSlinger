package com.promptslinger.burp;

import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import static com.promptslinger.burp.PSPanel.*;

public class HistoryPanel extends JPanel {

    private final MontoyaApi   api;
    private final HistoryStore store;
    private final PSPanel      owner;

    private DefaultListModel<HistoryEntry> listModel;
    private JList<HistoryEntry>            histList;
    private JTextArea                      msgArea;
    private JTextArea                      respArea;
    private JTextField                     noteEntry;
    JButton                                loadBtn;
    private JLabel[]                       markChips;

    public HistoryPanel(MontoyaApi api, HistoryStore store, PSPanel owner) {
        this.api   = api;
        this.store = store;
        this.owner = owner;
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);
        buildUI();
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setBackground(BG);
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));

        JLabel title = new JLabel("History  (newest first)");
        title.setFont(new Font("Monospaced", Font.BOLD, BASE_SIZE));
        title.setForeground(ACCENT);
        header.add(title, BorderLayout.NORTH);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        chips.setBackground(BG);
        markChips = new JLabel[MARK_KEYS.length];
        for (int i = 0; i < MARK_KEYS.length; i++) {
            JLabel chip = new JLabel(MARK_KEYS[i]);
            chip.setBackground(MARK_BG[i]);
            chip.setForeground(CHIP_TEXT);
            chip.setFont(new Font("Monospaced", Font.BOLD, Math.max(BASE_SIZE - 3, 9)));
            chip.setOpaque(true);
            chip.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final int idx = i;
            chip.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) renameLabel(idx);
                }
            });
            markChips[i] = chip;
            chips.add(chip);
        }
        header.add(chips, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        histList  = new JList<>(listModel);
        histList.setBackground(SURFACE);
        histList.setForeground(FG);
        histList.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        histList.setSelectionBackground(ACCENT);
        histList.setSelectionForeground(BG);
        histList.setCellRenderer(new HistoryCellRenderer());
        histList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelected();
        });
        histList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int idx = histList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        histList.setSelectedIndex(idx);
                        showContextMenu(e, idx);
                    }
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(histList);
        listScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, SURFACE));
        listScroll.setPreferredSize(new Dimension(0, 160));

        JPanel detail = new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                java.awt.Container p = getParent();
                if (p != null && p.getWidth() > 0) d.width = p.getWidth();
                return d;
            }
        };
        detail.setBackground(BG);
        detail.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        GridBagConstraints g = new GridBagConstraints();
        g.fill    = GridBagConstraints.BOTH;
        g.gridx   = 0;
        g.weightx = 1.0;
        g.insets  = new Insets(2, 0, 2, 0);

        g.gridy = 0; g.weighty = 0;
        detail.add(sectionLabel("MESSAGE"), g);

        g.gridy = 1; g.weighty = 0.25;
        msgArea = detailArea(2);
        detail.add(new JScrollPane(msgArea), g);

        g.gridy = 2; g.weighty = 0;
        detail.add(sectionLabel("RESPONSE"), g);

        g.gridy = 3; g.weighty = 0.45;
        respArea = detailArea(4);
        detail.add(new JScrollPane(respArea), g);

        g.gridy = 4; g.weighty = 0;
        detail.add(sectionLabel("NOTE  (auto-saved)"), g);

        g.gridy = 5; g.weighty = 0;
        noteEntry = new JTextField();
        noteEntry.setBackground(ENTRY_BG);
        noteEntry.setForeground(FG);
        noteEntry.setCaretColor(FG);
        noteEntry.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        noteEntry.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        noteEntry.addActionListener(e -> saveNote());
        noteEntry.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { saveNote(); }
        });
        detail.add(noteEntry, g);

        g.gridy = 6; g.weighty = 0;
        loadBtn = new JButton("Load message into input →");
        loadBtn.setBackground(ACCENT);
        loadBtn.setForeground(BG);
        loadBtn.setFont(new Font("Monospaced", Font.BOLD, BASE_SIZE - 1));
        loadBtn.setFocusPainted(false);
        loadBtn.setBorderPainted(false);
        loadBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loadBtn.addActionListener(e -> loadIntoInput());
        detail.add(loadBtn, g);

        g.gridy = 7; g.weighty = 0;
        JButton copyRespBtn = new JButton("Copy Response");
        copyRespBtn.setBackground(SURFACE);
        copyRespBtn.setForeground(FG);
        copyRespBtn.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        copyRespBtn.setFocusPainted(false);
        copyRespBtn.setBorderPainted(false);
        copyRespBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        copyRespBtn.addActionListener(e -> copySelectedResponse());
        detail.add(copyRespBtn, g);

        g.gridy = 8; g.weighty = 0;
        JLabel clearAll = new JLabel("Clear All History");
        clearAll.setForeground(RED);
        clearAll.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        clearAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearAll.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        clearAll.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { clearAllHistory(); }
        });
        detail.add(clearAll, g);

        JScrollPane detailScroll = new JScrollPane(detail);
        detailScroll.setBorder(null);
        detailScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listScroll, detailScroll);
        split.setBackground(BG);
        split.setBorder(null);
        split.setDividerSize(4);
        split.setResizeWeight(0.35);
        add(split, BorderLayout.CENTER);

        refresh();
    }

    // ── List population ────────────────────────────────────────────────────────

    public void refresh() {
        int selected = histList.getSelectedIndex();
        listModel.clear();
        List<HistoryEntry> entries = store.getEntries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            listModel.addElement(entries.get(i));
        }
        if (selected >= 0 && selected < listModel.size()) {
            histList.setSelectedIndex(selected);
        } else if (!listModel.isEmpty()) {
            histList.setSelectedIndex(0);
        }
    }

    private String formatLabel(HistoryEntry e) {
        String preview = e.message == null ? "" : e.message.replace("\n", " ");
        if (preview.length() > 30) preview = preview.substring(0, 30) + "…";
        return "  " + e.timestamp + "  " + preview;
    }

    // ── Selection display ──────────────────────────────────────────────────────

    private void showSelected() {
        HistoryEntry e = histList.getSelectedValue();
        if (e == null) return;
        msgArea.setText(e.message   != null ? e.message   : "");
        respArea.setText(e.response != null ? e.response  : "");
        noteEntry.setText(e.note    != null ? e.note      : "");
        msgArea.setCaretPosition(0);
        respArea.setCaretPosition(0);
    }

    // ── Context menu ───────────────────────────────────────────────────────────

    private void showContextMenu(MouseEvent e, int listIdx) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(SURFACE);

        for (int i = 0; i < MARK_KEYS.length; i++) {
            final int fi = i;
            JMenuItem item = new JMenuItem("● " + MARK_KEYS[i]);
            item.setBackground(SURFACE);
            item.setForeground(MARK_BG[i]);
            item.setFont(new Font("Monospaced", Font.BOLD, BASE_SIZE - 1));
            item.addActionListener(ev -> applyMark(listIdx, MARK_KEYS[fi]));
            menu.add(item);
        }

        menu.addSeparator();
        JMenuItem clearMark = new JMenuItem("Clear mark");
        clearMark.setBackground(SURFACE);
        clearMark.setForeground(MUTED);
        clearMark.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        clearMark.addActionListener(ev -> applyMark(listIdx, null));
        menu.add(clearMark);

        menu.addSeparator();
        JMenuItem deleteItem = new JMenuItem("Delete entry");
        deleteItem.setBackground(SURFACE);
        deleteItem.setForeground(RED);
        deleteItem.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        deleteItem.addActionListener(ev -> deleteEntry(listIdx));
        menu.add(deleteItem);

        menu.show(histList, e.getX(), e.getY());
    }

    // ── Label rename ───────────────────────────────────────────────────────────

    private void renameLabel(int idx) {
        String current = MARK_KEYS[idx];
        String newName = (String) JOptionPane.showInputDialog(
                this, "Rename label:", "Rename Mark",
                JOptionPane.PLAIN_MESSAGE, null, null, current);
        if (newName != null && !newName.isBlank()) {
            MARK_KEYS[idx] = newName.trim().toUpperCase();
            markChips[idx].setText(MARK_KEYS[idx]);
            PSPanel.saveMarkNames();
            histList.repaint();
        }
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private void applyMark(int listIdx, String markKey) {
        HistoryEntry e = listModel.getElementAt(listIdx);
        e.mark = markKey;
        store.save();
        syncBurpAnnotations(e);
        histList.repaint();
    }

    private void saveNote() {
        HistoryEntry e = histList.getSelectedValue();
        if (e == null) return;
        e.note = noteEntry.getText();
        store.save();
        syncBurpAnnotations(e);
    }

    private void syncBurpAnnotations(HistoryEntry e) {
        if (e.proxyItem == null) return;
        try {
            String colour = markToBurpColour(e.mark);
            e.proxyItem.annotations().setHighlightColor(
                    burp.api.montoya.core.HighlightColor.highlightColor(colour));
            if (e.note != null) e.proxyItem.annotations().setNotes(e.note);
        } catch (Exception ignored) {}
    }

    private void loadIntoInput() {
        HistoryEntry e = histList.getSelectedValue();
        if (e == null) return;
        owner.messageArea.setText(e.message != null ? e.message : "");
        owner.messageArea.requestFocus();
    }

    private void copySelectedResponse() {
        HistoryEntry e = histList.getSelectedValue();
        if (e == null || e.response == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
               .setContents(new java.awt.datatransfer.StringSelection(e.response), null);
    }

    private void deleteEntry(int listIdx) {
        HistoryEntry e = listModel.getElementAt(listIdx);
        store.remove(e);
        refresh();
    }

    private void clearAllHistory() {
        int confirm = JOptionPane.showConfirmDialog(
                this, "Delete all history entries?", "Clear History",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            store.clearAll();
            refresh();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String markToBurpColour(String mark) {
        if (mark == null) return "none";
        return switch (mark.toUpperCase()) {
            case "FINDING"   -> "red";
            case "HINT"      -> "yellow";
            case "INFO"      -> "cyan";
            case "CONFIRMED" -> "green";
            case "NOISE"     -> "gray";
            default          -> "none";
        };
    }

    private static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        l.setFont(new Font("Monospaced", Font.BOLD, Math.max(BASE_SIZE - 3, 9)));
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
        return l;
    }

    private static JTextArea detailArea(int rows) {
        JTextArea ta = new JTextArea(rows, 0);
        ta.setBackground(SURFACE);
        ta.setForeground(FG);
        ta.setCaretColor(FG);
        ta.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setEditable(false);
        ta.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        return ta;
    }

    // ── Cell renderer ──────────────────────────────────────────────────────────

    private class HistoryCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            HistoryEntry e = (HistoryEntry) value;
            super.getListCellRendererComponent(list, formatLabel(e), index, isSelected, cellHasFocus);
            setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
            if (!isSelected) {
                Color markColor = markToColor(e.mark);
                if (markColor != null) {
                    setBackground(markColor.darker().darker());
                    setForeground(markColor);
                } else {
                    setBackground(SURFACE);
                    setForeground(FG);
                }
            }
            setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            return this;
        }

        private Color markToColor(String mark) {
            if (mark == null) return null;
            for (int i = 0; i < MARK_KEYS.length; i++) {
                if (MARK_KEYS[i].equalsIgnoreCase(mark)) return MARK_BG[i];
            }
            return null;
        }
    }
}
