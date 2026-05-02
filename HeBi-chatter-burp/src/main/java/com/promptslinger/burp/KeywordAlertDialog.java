package com.promptslinger.burp;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.promptslinger.burp.PSPanel.*;

public class KeywordAlertDialog extends JDialog {

    private final DefaultListModel<KeywordAlerts.Alert> model = new DefaultListModel<>();
    private final JList<KeywordAlerts.Alert>             list  = new JList<>(model);

    public KeywordAlertDialog(Component parent) {
        super(SwingUtilities.getWindowAncestor(parent), "Keyword Alerts", ModalityType.APPLICATION_MODAL);
        setSize(480, 400);
        setLocationRelativeTo(parent);
        setBackground(BG);
        buildUI();
        loadAlerts();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        // Explanation
        JLabel info = new JLabel(
            "<html>When a response contains a keyword, the entry is auto-marked.<br>" +
            "Double-click an entry to edit it.</html>");
        info.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        info.setForeground(MUTED);
        root.add(info, BorderLayout.NORTH);

        // List
        list.setBackground(SURFACE);
        list.setForeground(FG);
        list.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        list.setSelectionBackground(ACCENT);
        list.setSelectionForeground(BG);
        list.setCellRenderer(new AlertCellRenderer());
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelected();
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(SURFACE, 2));
        root.add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnRow.setBackground(BG);

        JButton addBtn = btn("Add", ACCENT);
        addBtn.addActionListener(e -> addAlert());
        btnRow.add(addBtn);

        JButton removeBtn = btn("Remove", RED);
        removeBtn.addActionListener(e -> removeSelected());
        btnRow.add(removeBtn);

        JButton saveBtn = btn("Save & Close", GREEN);
        saveBtn.addActionListener(e -> { saveAlerts(); dispose(); });
        btnRow.add(saveBtn);

        root.add(btnRow, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void loadAlerts() {
        model.clear();
        for (KeywordAlerts.Alert a : KeywordAlerts.getAlerts()) model.addElement(a);
    }

    private void addAlert() {
        showAlertForm(null, -1);
    }

    private void editSelected() {
        int idx = list.getSelectedIndex();
        if (idx >= 0) showAlertForm(model.getElementAt(idx), idx);
    }

    private void removeSelected() {
        int idx = list.getSelectedIndex();
        if (idx >= 0) model.remove(idx);
    }

    private void showAlertForm(KeywordAlerts.Alert existing, int replaceIdx) {
        JTextField kwField   = new JTextField(existing != null ? existing.keyword : "", 22);
        JComboBox<String> markBox = new JComboBox<>(PSPanel.MARK_KEYS);
        if (existing != null) markBox.setSelectedItem(existing.markKey);
        JCheckBox ciBox = new JCheckBox("Case-insensitive", existing == null || existing.caseInsensitive);

        kwField.setBackground(ENTRY_BG);
        kwField.setForeground(FG);
        kwField.setCaretColor(FG);
        kwField.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE));
        markBox.setBackground(SURFACE);
        markBox.setForeground(FG);
        markBox.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE));
        ciBox.setBackground(BG);
        ciBox.setForeground(FG);
        ciBox.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE));

        JPanel form = new JPanel(new GridLayout(3, 2, 6, 6));
        form.setBackground(BG);
        form.add(lbl("Keyword:"));     form.add(kwField);
        form.add(lbl("Auto-mark as:")); form.add(markBox);
        form.add(new JLabel());        form.add(ciBox);

        int result = JOptionPane.showConfirmDialog(
                this, form, existing == null ? "Add Alert" : "Edit Alert",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String kw = kwField.getText().trim();
            if (kw.isEmpty()) return;
            KeywordAlerts.Alert a = new KeywordAlerts.Alert(
                    kw, (String) markBox.getSelectedItem(), ciBox.isSelected());
            if (replaceIdx >= 0) model.set(replaceIdx, a);
            else model.addElement(a);
        }
    }

    private void saveAlerts() {
        List<KeywordAlerts.Alert> updated = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) updated.add(model.getElementAt(i));
        KeywordAlerts.setAlerts(updated);
    }

    private static JButton btn(String text, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(SURFACE);
        b.setForeground(fg);
        b.setFont(new Font("Monospaced", Font.BOLD, BASE_SIZE - 1));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        return b;
    }

    private static JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        l.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE));
        return l;
    }

    private class AlertCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            KeywordAlerts.Alert a = (KeywordAlerts.Alert) value;
            super.getListCellRendererComponent(list, a.toString(), index, isSelected, cellHasFocus);
            setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
            if (!isSelected) {
                setBackground(SURFACE);
                // colour the mark label
                Color markColor = null;
                for (int i = 0; i < MARK_KEYS.length; i++) {
                    if (MARK_KEYS[i].equals(a.markKey)) { markColor = MARK_BG[i]; break; }
                }
                setForeground(markColor != null ? markColor : FG);
            }
            setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            return this;
        }
    }
}
