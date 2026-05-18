package com.promptslinger.burp;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.promptslinger.burp.PSPanel.*;

public class PayloadBrowserDialog extends JDialog {

    private final PSPanel owner;

    private final DefaultListModel<String>               catModel     = new DefaultListModel<>();
    private final DefaultListModel<PayloadLibrary.Payload> payModel   = new DefaultListModel<>();
    private final JList<String>                          catList      = new JList<>(catModel);
    private final JList<PayloadLibrary.Payload>          payList      = new JList<>(payModel);
    private final JTextArea                              previewArea  = new JTextArea();
    private final JTextField                             searchField  = new JTextField();
    private final JButton                                deleteBtn    = btn("Delete", RED);
    private       String                                 lastLoadedPreviewText = "";

    public PayloadBrowserDialog(PSPanel owner) {
        super(SwingUtilities.getWindowAncestor(owner), "Payload Library",
              ModalityType.MODELESS);
        this.owner = owner;
        setSize(780, 540);
        setLocationRelativeTo(owner);
        buildUI();
        populateCategories();
        if (!catModel.isEmpty()) catList.setSelectedIndex(0);
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);

        // Category list (left)
        catList.setBackground(SURFACE);
        catList.setForeground(FG);
        catList.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        catList.setSelectionBackground(ACCENT);
        catList.setSelectionForeground(BG);
        catList.setFixedCellHeight(BASE_SIZE + 10);
        catList.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        catList.addListSelectionListener(this::onCategorySelected);

        JScrollPane catScroll = new JScrollPane(catList);
        catScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, SURFACE));
        catScroll.setPreferredSize(new Dimension(200, 0));

        JPanel catPanel = new JPanel(new BorderLayout());
        catPanel.setBackground(BG);
        JLabel catTitle = new JLabel("  Category");
        catTitle.setFont(new Font("Monospaced", Font.BOLD, Math.max(BASE_SIZE - 2, 10)));
        catTitle.setForeground(MUTED);
        catTitle.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        catPanel.add(catTitle, BorderLayout.NORTH);
        catPanel.add(catScroll, BorderLayout.CENTER);

        // Payload list + preview (right)
        payList.setBackground(SURFACE);
        payList.setForeground(FG);
        payList.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        payList.setSelectionBackground(ACCENT);
        payList.setSelectionForeground(BG);
        payList.setFixedCellHeight(BASE_SIZE + 10);
        payList.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        payList.addListSelectionListener(this::onPayloadSelected);
        payList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) loadIntoMessage();
            }
        });

        JScrollPane payScroll = new JScrollPane(payList);
        payScroll.setBorder(null);

        previewArea.setBackground(ENTRY_BG);
        previewArea.setForeground(FG);
        previewArea.setCaretColor(FG);
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, SURFACE));
        previewScroll.setPreferredSize(new Dimension(0, 140));

        JLabel payTitle = new JLabel("  Payloads  (double-click to load)");
        payTitle.setFont(new Font("Monospaced", Font.BOLD, Math.max(BASE_SIZE - 2, 10)));
        payTitle.setForeground(MUTED);
        payTitle.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JLabel prevTitle = new JLabel("  Preview  (editable before loading)");
        prevTitle.setFont(new Font("Monospaced", Font.BOLD, Math.max(BASE_SIZE - 2, 10)));
        prevTitle.setForeground(MUTED);
        prevTitle.setBorder(BorderFactory.createEmptyBorder(6, 6, 4, 6));

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(BG);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        rightSplit.setBackground(BG);
        rightSplit.setBorder(null);
        rightSplit.setDividerSize(4);
        rightSplit.setResizeWeight(0.55);

        searchField.setBackground(ENTRY_BG);
        searchField.setForeground(FG);
        searchField.setCaretColor(FG);
        searchField.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SURFACE, 1),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        searchField.putClientProperty("JTextField.placeholderText", "Search payloads...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { applyFilter(); }
            public void removeUpdate(DocumentEvent e)  { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        JPanel searchBar = new JPanel(new BorderLayout());
        searchBar.setBackground(BG);
        searchBar.setBorder(BorderFactory.createEmptyBorder(0, 6, 4, 6));
        searchBar.add(searchField, BorderLayout.CENTER);

        JPanel payHeader = new JPanel(new BorderLayout());
        payHeader.setBackground(BG);
        payHeader.add(payTitle,   BorderLayout.NORTH);
        payHeader.add(searchBar,  BorderLayout.SOUTH);

        JPanel paySection = new JPanel(new BorderLayout());
        paySection.setBackground(BG);
        paySection.add(payHeader, BorderLayout.NORTH);
        paySection.add(payScroll, BorderLayout.CENTER);

        JPanel prevSection = new JPanel(new BorderLayout());
        prevSection.setBackground(BG);
        prevSection.add(prevTitle, BorderLayout.NORTH);
        prevSection.add(previewScroll, BorderLayout.CENTER);

        rightSplit.setTopComponent(paySection);
        rightSplit.setBottomComponent(prevSection);
        rightPanel.add(rightSplit, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, catPanel, rightPanel);
        mainSplit.setBackground(BG);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(4);
        mainSplit.setDividerLocation(200);
        root.add(mainSplit, BorderLayout.CENTER);

        // Button bar
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        btnBar.setBackground(BG);
        btnBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, SURFACE));

        JButton addCustomBtn = btn("Add Custom", GREEN);
        addCustomBtn.addActionListener(e -> addCustomPayload());
        deleteBtn.addActionListener(e -> deleteCustomPayload());
        deleteBtn.setEnabled(false);

        JButton saveProbeBtn  = btn("Save as Probe", ORANGE);
        saveProbeBtn.addActionListener(e -> saveAsProbe());

        JButton loadBtn = btn("Load into Message", ACCENT);
        loadBtn.addActionListener(e -> loadIntoMessage());

        btnBar.add(addCustomBtn);
        btnBar.add(deleteBtn);
        btnBar.add(Box.createHorizontalStrut(20));
        btnBar.add(saveProbeBtn);
        btnBar.add(loadBtn);
        root.add(btnBar, BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ── Population ────────────────────────────────────────────────────────────

    private void populateCategories() {
        catModel.clear();
        for (String cat : PayloadLibrary.getCategories()) catModel.addElement(cat);
    }

    private void applyFilter() {
        String query = searchField.getText().trim().toLowerCase();
        payModel.clear();
        previewArea.setText("");
        lastLoadedPreviewText = "";
        if (query.isEmpty()) {
            String cat = catList.getSelectedValue();
            if (cat != null) {
                for (PayloadLibrary.Payload p : PayloadLibrary.getByCategory(cat)) payModel.addElement(p);
                if (!payModel.isEmpty()) payList.setSelectedIndex(0);
            }
            return;
        }
        for (PayloadLibrary.Payload p : PayloadLibrary.getAll()) {
            if (p.name.toLowerCase().contains(query)
                    || p.category.toLowerCase().contains(query)
                    || p.text.toLowerCase().contains(query)) {
                payModel.addElement(p);
            }
        }
        if (!payModel.isEmpty()) payList.setSelectedIndex(0);
    }

    private void onCategorySelected(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        if (!searchField.getText().trim().isEmpty()) return; // search overrides category
        String cat = catList.getSelectedValue();
        payModel.clear();
        previewArea.setText("");
        lastLoadedPreviewText = "";
        if (cat == null) return;
        List<PayloadLibrary.Payload> payloads = PayloadLibrary.getByCategory(cat);
        for (PayloadLibrary.Payload p : payloads) payModel.addElement(p);
        deleteBtn.setEnabled("Custom".equals(cat));
        if (!payModel.isEmpty()) payList.setSelectedIndex(0);
    }

    private void onPayloadSelected(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        PayloadLibrary.Payload p = payList.getSelectedValue();
        if (p != null) {
            String current = previewArea.getText();
            if (!current.equals(lastLoadedPreviewText) && !current.isEmpty()) {
                int choice = JOptionPane.showConfirmDialog(this,
                        "You have unsaved edits in the preview. Discard them?",
                        "Discard Changes", JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.OK_OPTION) return;
            }
            previewArea.setText(p.text);
            previewArea.setCaretPosition(0);
            lastLoadedPreviewText = p.text;
        } else {
            previewArea.setText("");
            lastLoadedPreviewText = "";
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void loadIntoMessage() {
        String text = previewArea.getText().trim();
        if (text.isEmpty()) return;
        owner.messageArea.setText(text);
        owner.messageArea.requestFocus();
        owner.messageArea.setCaretPosition(text.length());
        lastLoadedPreviewText = text;
    }

    private void saveAsProbe() {
        String text = previewArea.getText().trim();
        if (text.isEmpty()) return;
        PayloadLibrary.Payload p = payList.getSelectedValue();
        String defaultName = p != null ? p.name : "Probe";
        String name = (String) JOptionPane.showInputDialog(
                this, "Name for this probe:", "Save as Probe",
                JOptionPane.PLAIN_MESSAGE, null, null, defaultName);
        if (name != null && !name.isBlank()) {
            SavedProbes.add(name.trim(), text);
        }
    }

    private void addCustomPayload() {
        JTextField nameField = styledField(20);
        JTextField descField = styledField(20);
        JTextArea  textArea  = new JTextArea(5, 20);
        textArea.setBackground(ENTRY_BG);
        textArea.setForeground(FG);
        textArea.setCaretColor(FG);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JPanel form = new JPanel(new GridLayout(4, 1, 0, 4));
        form.setBackground(BG);
        form.add(labeled("Name:", nameField));
        form.add(labeled("Description:", descField));
        form.add(lbl("Payload text:"));
        form.add(new JScrollPane(textArea));

        int result = JOptionPane.showConfirmDialog(
                this, form, "Add Custom Payload",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String text = textArea.getText().trim();
            if (name.isEmpty() || text.isEmpty()) return;
            PayloadLibrary.Payload p = new PayloadLibrary.Payload(
                    name, "Custom", descField.getText().trim(), text);
            PayloadLibrary.addCustom(p);
            populateCategories();
            catList.setSelectedValue("Custom", true);
        }
    }

    private void deleteCustomPayload() {
        PayloadLibrary.Payload p = payList.getSelectedValue();
        if (p == null || !p.custom) return;
        int confirm = JOptionPane.showConfirmDialog(
                this, "Delete \"" + p.name + "\"?", "Delete Payload",
                JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            PayloadLibrary.removeCustom(p);
            populateCategories();
            catList.setSelectedValue("Custom", true);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JButton btn(String text, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(SURFACE);
        b.setForeground(fg);
        b.setFont(new Font("Monospaced", Font.BOLD, BASE_SIZE - 1));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        l.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        return l;
    }

    private static JTextField styledField(int cols) {
        JTextField f = new JTextField(cols);
        f.setBackground(ENTRY_BG);
        f.setForeground(FG);
        f.setCaretColor(FG);
        f.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        f.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        return f;
    }

    private static JPanel labeled(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setBackground(new Color(0, 0, 0, 0));
        p.add(lbl(label), BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        return p;
    }
}
