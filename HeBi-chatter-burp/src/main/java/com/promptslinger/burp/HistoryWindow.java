package com.promptslinger.burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.HighlightColor;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

import static com.promptslinger.burp.PSPanel.*;

public class HistoryWindow extends JFrame {

    // Maps our mark keys to Burp HighlightColor so proxy history also gets coloured
    private static final HighlightColor[] BURP_COLORS = {
        HighlightColor.RED, HighlightColor.YELLOW, HighlightColor.CYAN,
        HighlightColor.GREEN, HighlightColor.GRAY
    };

    private final MontoyaApi   api;
    private final HistoryStore store;
    private final PSPanel    mainPanel;

    private DefaultListModel<String> listModel;
    private JList<String>            entryList;
    private JTextArea                msgView;
    private JTextArea                respView;
    private JTextArea                noteEntry;
    private JButton                  loadBtn;

    private int currentIdx = -1;

    public HistoryWindow(MontoyaApi api, HistoryStore store, PSPanel mainPanel) {
        super("Message History — PromptSlinger");
        this.api       = api;
        this.store     = store;
        this.mainPanel = mainPanel;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(screen.width / 2, (int)(screen.height * 0.9));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(0, 0));

        buildUI();
        populateList();
    }

    // â”€â”€ UI construction â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void buildUI() {
        // â”€â”€ Title + legend â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setBackground(BG);
        top.setBorder(BorderFactory.createEmptyBorder(10, 14, 6, 14));

        JLabel title = new JLabel("History  (newest first)");
        title.setFont(new Font("Monospaced", Font.BOLD, 13));
        title.setForeground(ACCENT);
        top.add(title, BorderLayout.NORTH);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        legend.setBackground(BG);
        legend.add(label("Tags:", MUTED));
        for (int i = 0; i < MARK_KEYS.length; i++) {
            JLabel chip = new JLabel("  " + MARK_KEYS[i] + "  ");
            chip.setBackground(MARK_BG[i]);
            chip.setForeground(MARK_FG[i]);
            chip.setFont(new Font("Monospaced", Font.BOLD, 9));
            chip.setOpaque(true);
            legend.add(chip);
        }
        top.add(legend, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        // â”€â”€ Bottom bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        bottomBar.setBackground(BG);

        JButton clearAll = new JButton("Clear All History");
        clearAll.setBackground(BG);
        clearAll.setForeground(RED);
        clearAll.setFont(new Font("Monospaced", Font.PLAIN, 10));
        clearAll.setFocusPainted(false);
        clearAll.setBorderPainted(false);
        clearAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearAll.addActionListener(e -> clearAll());
        bottomBar.add(clearAll);
        add(bottomBar, BorderLayout.SOUTH);

        // â”€â”€ Split pane â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setBackground(BG);
        split.setBorder(null);
        split.setDividerSize(5);
        split.setDividerLocation(260);

        split.setLeftComponent(buildListPanel());
        split.setRightComponent(buildDetailPanel());
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildListPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);

        listModel = new DefaultListModel<>();
        entryList = new JList<>(listModel);
        entryList.setBackground(SURFACE);
        entryList.setForeground(FG);
        entryList.setSelectionBackground(ACCENT);
        entryList.setSelectionForeground(BG);
        entryList.setFont(MONO);
        entryList.setFixedCellHeight(22);

        entryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && entryList.getSelectedIndex() >= 0)
                showEntry(entryList.getSelectedIndex());
        });

        // Right-click mark menu
        entryList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int idx = entryList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        entryList.setSelectedIndex(idx);
                        showMarkMenu(e, idx);
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(entryList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildDetailPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weightx = 1.0;
        g.insets = new Insets(2, 0, 2, 0);

        // MESSAGE label
        g.gridy = 0; g.weighty = 0;
        p.add(label("MESSAGE", MUTED), g);

        // Message view
        msgView = readOnlyArea(ENTRY_BG, FG, 4);
        g.gridy = 1; g.weighty = 0.25;
        p.add(new JScrollPane(msgView), g);

        // RESPONSE label
        g.gridy = 2; g.weighty = 0;
        p.add(label("RESPONSE", MUTED), g);

        // Response view
        respView = readOnlyArea(SURFACE, GREEN, 10);
        g.gridy = 3; g.weighty = 0.55;
        p.add(new JScrollPane(respView), g);

        // NOTE label
        g.gridy = 4; g.weighty = 0;
        JPanel noteHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        noteHeader.setBackground(BG);
        noteHeader.add(label("NOTE", MUTED));
        JLabel autoSaved = new JLabel("  (auto-saved)");
        autoSaved.setFont(new Font("Monospaced", Font.ITALIC, 9));
        autoSaved.setForeground(new Color(0x44475a));
        noteHeader.add(autoSaved);
        p.add(noteHeader, g);

        // Note entry
        noteEntry = new JTextArea(2, 40);
        noteEntry.setBackground(ENTRY_BG);
        noteEntry.setForeground(new Color(0xf1fa8c));
        noteEntry.setCaretColor(FG);
        noteEntry.setFont(MONO);
        noteEntry.setLineWrap(true);
        noteEntry.setWrapStyleWord(true);
        noteEntry.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        noteEntry.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { saveCurrentNote(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { saveCurrentNote(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { saveCurrentNote(); }
        });
        g.gridy = 5; g.weighty = 0.1;
        p.add(new JScrollPane(noteEntry), g);

        // Load button
        loadBtn = new JButton("Load message into input →");
        loadBtn.setBackground(ACCENT);
        loadBtn.setForeground(BG);
        loadBtn.setFont(MONO_BOLD);
        loadBtn.setFocusPainted(false);
        loadBtn.setBorderPainted(false);
        loadBtn.setEnabled(false);
        loadBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        g.gridy = 6; g.weighty = 0; g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.CENTER;
        p.add(loadBtn, g);

        // Copy response button
        JButton copyRespBtn = new JButton("Copy Response");
        copyRespBtn.setBackground(SURFACE);
        copyRespBtn.setForeground(FG);
        copyRespBtn.setFont(MONO);
        copyRespBtn.setFocusPainted(false);
        copyRespBtn.setBorderPainted(false);
        copyRespBtn.addActionListener(e ->
            Toolkit.getDefaultToolkit().getSystemClipboard()
                   .setContents(new StringSelection(respView.getText()), null));
        g.gridy = 7; g.fill = GridBagConstraints.NONE;
        p.add(copyRespBtn, g);

        return p;
    }

    // â”€â”€ List population â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void populateList() {
        listModel.clear();
        List<HistoryEntry> entries = store.getEntries();
        for (HistoryEntry e : entries) {
            listModel.addElement(formatLabel(e));
        }
        applyListColors();
        if (!entries.isEmpty()) {
            entryList.setSelectedIndex(0);
            showEntry(0);
        }
    }

    private String formatLabel(HistoryEntry e) {
        String preview = e.message == null ? "" : e.message.replace("\n", " ");
        if (preview.length() > 32) preview = preview.substring(0, 32) + "…";
        return "  " + e.timestamp + "  " + preview;
    }

    private void applyListColors() {
        List<HistoryEntry> entries = store.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            String mark = entries.get(i).mark;
            if (mark != null) {
                for (int j = 0; j < MARK_KEYS.length; j++) {
                    if (MARK_KEYS[j].equals(mark)) {
                        setRowColor(i, MARK_BG[j], MARK_FG[j]);
                        break;
                    }
                }
            }
        }
    }

    private void setRowColor(int idx, Color bg, Color fg) {
        entryList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean hasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
                setFont(MONO);
                List<HistoryEntry> entries = store.getEntries();
                if (index < entries.size() && entries.get(index).mark != null) {
                    String m = entries.get(index).mark;
                    for (int j = 0; j < MARK_KEYS.length; j++) {
                        if (MARK_KEYS[j].equals(m)) {
                            setBackground(isSelected ? MARK_BG[j].darker() : MARK_BG[j]);
                            setForeground(MARK_FG[j]);
                            return this;
                        }
                    }
                }
                setBackground(isSelected ? ACCENT : SURFACE);
                setForeground(isSelected ? BG : FG);
                return this;
            }
        });
    }

    // â”€â”€ Detail view â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void showEntry(int idx) {
        saveCurrentNote();
        currentIdx = idx;
        List<HistoryEntry> entries = store.getEntries();
        if (idx >= entries.size()) return;
        HistoryEntry e = entries.get(idx);

        msgView.setText(e.message != null ? e.message : "");
        msgView.setCaretPosition(0);
        respView.setText(e.response != null ? e.response : "");
        respView.setCaretPosition(0);
        noteEntry.setText(e.note != null ? e.note : "");

        loadBtn.setEnabled(true);
        // Replace action listener each time so we always load the current entry
        for (java.awt.event.ActionListener al : loadBtn.getActionListeners())
            loadBtn.removeActionListener(al);
        loadBtn.addActionListener(ev -> {
            mainPanel.messageArea.setText(e.message != null ? e.message : "");
            dispose();
        });
    }

    private void saveCurrentNote() {
        if (currentIdx < 0) return;
        List<HistoryEntry> entries = store.getEntries();
        if (currentIdx >= entries.size()) return;
        entries.get(currentIdx).note = noteEntry.getText().trim();
        store.save();
    }

    // â”€â”€ Mark menu â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void showMarkMenu(java.awt.event.MouseEvent e, int idx) {
        List<HistoryEntry> entries = store.getEntries();
        if (idx >= entries.size()) return;
        HistoryEntry entry = entries.get(idx);

        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(SURFACE);

        JMenuItem clearItem = new JMenuItem("Clear mark");
        clearItem.setBackground(SURFACE);
        clearItem.setForeground(FG);
        clearItem.addActionListener(ev -> applyMark(idx, null));
        menu.add(clearItem);
        menu.addSeparator();

        for (int i = 0; i < MARK_KEYS.length; i++) {
            final int fi = i;
            JMenuItem item = new JMenuItem("● " + MARK_KEYS[i]);
            item.setBackground(SURFACE);
            item.setForeground(MARK_BG[i]);
            item.addActionListener(ev -> applyMark(idx, MARK_KEYS[fi]));
            menu.add(item);
        }
        menu.show(entryList, e.getX(), e.getY());
    }

    private void applyMark(int idx, String markKey) {
        List<HistoryEntry> entries = store.getEntries();
        if (idx >= entries.size()) return;
        HistoryEntry entry = entries.get(idx);
        entry.mark = markKey;
        store.save();

        // Sync with Burp proxy highlight
        if (entry.proxyItem != null) {
            try {
                if (markKey == null) {
                    entry.proxyItem.annotations().setHighlightColor(HighlightColor.NONE);
                } else {
                    for (int i = 0; i < MARK_KEYS.length; i++) {
                        if (MARK_KEYS[i].equals(markKey)) {
                            entry.proxyItem.annotations().setHighlightColor(BURP_COLORS[i]);
                            break;
                        }
                    }
                }
                // Also write the note to Burp's comment field
                if (entry.note != null && !entry.note.isBlank())
                    entry.proxyItem.annotations().setNotes(entry.note);
            } catch (Exception ignored) {}
        }

        // Refresh cell renderer
        setRowColor(idx, markKey == null ? SURFACE : MARK_BG[markForIndex(markKey)],
                         markKey == null ? FG      : MARK_FG[markForIndex(markKey)]);
        entryList.repaint();
    }

    private static int markForIndex(String key) {
        for (int i = 0; i < MARK_KEYS.length; i++) if (MARK_KEYS[i].equals(key)) return i;
        return 0;
    }

    // â”€â”€ Clear all â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void clearAll() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete all history entries permanently?", "Clear History",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        store.clearAll();
        listModel.clear();
        msgView.setText("");
        respView.setText("");
        noteEntry.setText("");
        loadBtn.setEnabled(false);
        currentIdx = -1;
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static JLabel label(String text, Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setFont(new Font("Monospaced", Font.BOLD, 9));
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
        return l;
    }

    private static JTextArea readOnlyArea(Color bg, Color fg, int rows) {
        JTextArea ta = new JTextArea(rows, 40);
        ta.setBackground(bg);
        ta.setForeground(fg);
        ta.setFont(MONO);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setEditable(false);
        ta.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return ta;
    }
}
