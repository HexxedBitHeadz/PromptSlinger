package com.promptslinger.burp;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import static com.promptslinger.burp.PSPanel.*;

public class InlinePayloadPanel extends JPanel {

    private final PSPanel owner;

    private final JComboBox<String>                      catCombo    = new JComboBox<>();
    private final DefaultListModel<PayloadLibrary.Payload> payModel  = new DefaultListModel<>();
    private final JList<PayloadLibrary.Payload>          payList     = new JList<>(payModel);
    private final JTextArea                              previewArea = new JTextArea(3, 0);

    public InlinePayloadPanel(PSPanel owner) {
        this.owner = owner;
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);
        buildUI();
        populateCategories();
        if (catCombo.getItemCount() > 0) catCombo.setSelectedIndex(0);
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        // Top: category picker + add-custom button
        catCombo.setBackground(SURFACE);
        catCombo.setForeground(FG);
        catCombo.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        catCombo.setFocusable(false);
        catCombo.addActionListener(e -> onCategorySelected());

        JButton addBtn = new JButton("+");
        addBtn.setBackground(SURFACE);
        addBtn.setForeground(GREEN);
        addBtn.setFont(new Font("Monospaced", Font.BOLD, BASE_SIZE));
        addBtn.setFocusPainted(false);
        addBtn.setBorderPainted(false);
        addBtn.setToolTipText("Add custom payload");
        addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addBtn.addActionListener(e -> addCustomPayload());

        JPanel topBar = new JPanel(new BorderLayout(4, 0));
        topBar.setBackground(BG);
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        topBar.add(catCombo, BorderLayout.CENTER);
        topBar.add(addBtn,   BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // Center: payload list
        payList.setBackground(SURFACE);
        payList.setForeground(FG);
        payList.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        payList.setSelectionBackground(ACCENT);
        payList.setSelectionForeground(BG);
        payList.setFixedCellHeight(BASE_SIZE + 10);
        payList.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        payList.addListSelectionListener(this::onPayloadSelected);
        payList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) loadSelected();
            }
        });
        // Enter key also loads
        payList.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "load");
        payList.getActionMap().put("load", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { loadSelected(); }
        });

        JScrollPane payScroll = new JScrollPane(payList);
        payScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, SURFACE));

        // Preview panel
        previewArea.setBackground(ENTRY_BG);
        previewArea.setForeground(FG);
        previewArea.setCaretColor(FG);
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JLabel hintLabel = new JLabel("  Preview  (editable)");
        hintLabel.setFont(new Font("Monospaced", Font.ITALIC, Math.max(BASE_SIZE - 3, 9)));
        hintLabel.setForeground(MUTED);
        hintLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 4));

        JButton loadBtn = new JButton("Load into Message");
        loadBtn.setBackground(ACCENT);
        loadBtn.setForeground(BG);
        loadBtn.setFont(new Font("Monospaced", Font.BOLD, Math.max(BASE_SIZE - 1, 10)));
        loadBtn.setFocusPainted(false);
        loadBtn.setBorderPainted(false);
        loadBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loadBtn.addActionListener(e -> loadSelected());

        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setBorder(null);

        JPanel previewPanel = new JPanel(new BorderLayout(0, 2));
        previewPanel.setBackground(BG);
        previewPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        previewPanel.add(hintLabel,    BorderLayout.NORTH);
        previewPanel.add(previewScroll, BorderLayout.CENTER);
        previewPanel.add(loadBtn,       BorderLayout.SOUTH);

        // Split: list on top, preview below — user can drag the divider
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, payScroll, previewPanel);
        splitPane.setBackground(BG);
        splitPane.setBorder(null);
        splitPane.setDividerSize(4);
        splitPane.setResizeWeight(0.55);
        add(splitPane, BorderLayout.CENTER);
    }

    // ── Population ─────────────────────────────────────────────────────────────

    private void populateCategories() {
        Object selected = catCombo.getSelectedItem();
        catCombo.removeAllItems();
        for (String cat : PayloadLibrary.getCategories()) catCombo.addItem(cat);
        if (selected != null) catCombo.setSelectedItem(selected);
    }

    private void onCategorySelected() {
        String cat = (String) catCombo.getSelectedItem();
        payModel.clear();
        previewArea.setText("");
        if (cat == null) return;
        List<PayloadLibrary.Payload> payloads = PayloadLibrary.getByCategory(cat);
        for (PayloadLibrary.Payload p : payloads) payModel.addElement(p);
        if (!payModel.isEmpty()) payList.setSelectedIndex(0);
    }

    private void onPayloadSelected(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        PayloadLibrary.Payload p = payList.getSelectedValue();
        if (p != null) {
            previewArea.setText(p.text);
            previewArea.setCaretPosition(0);
        }
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private void loadSelected() {
        String text = previewArea.getText().trim();
        if (text.isEmpty()) return;
        owner.messageArea.setText(text);
        owner.messageArea.requestFocus();
        owner.messageArea.setCaretPosition(text.length());
    }

    private void addCustomPayload() {
        JTextField nameField = new JTextField(20);
        nameField.setBackground(ENTRY_BG);
        nameField.setForeground(FG);
        nameField.setCaretColor(FG);
        nameField.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        nameField.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

        JTextArea textArea = new JTextArea(5, 20);
        textArea.setBackground(ENTRY_BG);
        textArea.setForeground(FG);
        textArea.setCaretColor(FG);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JLabel nameLabel = new JLabel("Name:");
        nameLabel.setForeground(MUTED);
        nameLabel.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));

        JLabel textLabel = new JLabel("Payload text:");
        textLabel.setForeground(MUTED);
        textLabel.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));

        JPanel nameRow = new JPanel(new BorderLayout(6, 0));
        nameRow.setBackground(BG);
        nameRow.add(nameLabel, BorderLayout.WEST);
        nameRow.add(nameField, BorderLayout.CENTER);

        JPanel form = new JPanel(new GridLayout(4, 1, 0, 4));
        form.setBackground(BG);
        form.add(nameRow);
        form.add(textLabel);
        form.add(new JScrollPane(textArea));
        form.add(new JLabel());

        int result = JOptionPane.showConfirmDialog(
                this, form, "Add Custom Payload",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String text = textArea.getText().trim();
            if (name.isEmpty() || text.isEmpty()) return;
            PayloadLibrary.addCustom(new PayloadLibrary.Payload(name, "Custom", "", text));
            populateCategories();
            catCombo.setSelectedItem("Custom");
        }
    }
}
