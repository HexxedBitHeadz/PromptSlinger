package com.promptslinger.burp;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.promptslinger.burp.PSPanel.*;

public class ExportDialog extends JDialog {

    private final HistoryStore store;

    private final JRadioButton mdRadio  = radio("Markdown  (.md)");
    private final JRadioButton csvRadio = radio("CSV  (.csv)");
    private final JCheckBox    allCheck = check("All entries");
    private final JCheckBox[]  markChecks = new JCheckBox[MARK_KEYS.length];
    private final JLabel       countLabel = new JLabel();

    public ExportDialog(Component parent, HistoryStore store) {
        super(SwingUtilities.getWindowAncestor(parent), "Export History",
              ModalityType.APPLICATION_MODAL);
        this.store = store;
        setSize(400, 360);
        setLocationRelativeTo(parent);
        setResizable(false);
        buildUI();
        updateCount();
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));

        // Format section
        JPanel fmtPanel = section("Format");
        ButtonGroup grp = new ButtonGroup();
        grp.add(mdRadio); grp.add(csvRadio);
        mdRadio.setSelected(true);
        JPanel fmtRow = row(mdRadio, csvRadio);
        fmtPanel.add(fmtRow);
        root.add(fmtPanel, BorderLayout.NORTH);

        // Filter section
        JPanel filterPanel = section("Filter");
        allCheck.setSelected(true);
        allCheck.addActionListener(e -> { syncMarkChecks(); updateCount(); });
        filterPanel.add(wrap(allCheck));

        JPanel marksRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        marksRow.setBackground(BG);
        for (int i = 0; i < MARK_KEYS.length; i++) {
            markChecks[i] = check(MARK_KEYS[i]);
            markChecks[i].setForeground(MARK_BG[i]);
            markChecks[i].setEnabled(false);
            markChecks[i].addActionListener(e -> updateCount());
            marksRow.add(markChecks[i]);
        }
        filterPanel.add(marksRow);

        countLabel.setFont(new Font("Monospaced", Font.ITALIC, Math.max(BASE_SIZE - 2, 10)));
        countLabel.setForeground(MUTED);
        countLabel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        filterPanel.add(wrap(countLabel));

        root.add(filterPanel, BorderLayout.CENTER);

        // Buttons
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setBackground(BG);

        JButton cancelBtn = btn("Cancel", MUTED);
        cancelBtn.addActionListener(e -> dispose());

        JButton exportBtn = btn("Export...", ACCENT);
        exportBtn.addActionListener(e -> doExport());

        btnRow.add(cancelBtn);
        btnRow.add(exportBtn);
        root.add(btnRow, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void syncMarkChecks() {
        boolean specific = !allCheck.isSelected();
        for (JCheckBox cb : markChecks) cb.setEnabled(specific);
    }

    private void updateCount() {
        int n = getFilteredEntries().size();
        countLabel.setText(n + " entr" + (n == 1 ? "y" : "ies") + " will be exported");
    }

    // ── Export logic ──────────────────────────────────────────────────────────

    private void doExport() {
        List<HistoryEntry> entries = getFilteredEntries();
        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No entries match the current filter.",
                    "Nothing to Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String ext  = mdRadio.isSelected() ? ".md" : ".csv";
        String desc = mdRadio.isSelected() ? "Markdown files" : "CSV files";

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save Export");
        fc.setSelectedFile(new File("promptslinger_export" + ext));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(desc, ext.substring(1)));

        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File out = fc.getSelectedFile();
        if (!out.getName().endsWith(ext)) out = new File(out.getPath() + ext);

        try {
            String content = mdRadio.isSelected()
                    ? buildMarkdown(entries) : buildCsv(entries);
            Files.writeString(out.toPath(), content);
            JOptionPane.showMessageDialog(this,
                    "Exported " + entries.size() + " entries to:\n" + out.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<HistoryEntry> getFilteredEntries() {
        List<HistoryEntry> all = store.getEntries();
        if (allCheck.isSelected()) return new ArrayList<>(all);

        List<HistoryEntry> out = new ArrayList<>();
        for (HistoryEntry e : all) {
            for (int i = 0; i < MARK_KEYS.length; i++) {
                if (markChecks[i].isSelected() && MARK_KEYS[i].equals(e.mark)) {
                    out.add(e);
                    break;
                }
            }
        }
        return out;
    }

    // ── Markdown builder ──────────────────────────────────────────────────────

    private static String buildMarkdown(List<HistoryEntry> entries) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        StringBuilder sb = new StringBuilder();
        sb.append("# PromptSlinger — Export Report\n");
        sb.append("Generated: ").append(ts).append("\n\n---\n\n");

        for (HistoryEntry e : entries) {
            String markTag  = e.mark != null ? "[" + e.mark + "] " : "";
            String latency  = e.latencyMs > 0 ? " · " + e.latencyMs + "ms" : "";
            sb.append("## ").append(markTag).append(e.timestamp).append(latency).append("\n\n");

            if (e.url != null)
                sb.append("**Endpoint:** ").append(e.url).append("\n\n");

            if (e.message != null) {
                sb.append("**Message:**\n```\n").append(e.message.trim()).append("\n```\n\n");
            }
            if (e.response != null) {
                sb.append("**Response:**\n```json\n").append(e.response.trim()).append("\n```\n\n");
            }
            if (e.note != null && !e.note.isBlank())
                sb.append("**Note:** ").append(e.note.trim()).append("\n\n");

            sb.append("---\n\n");
        }
        return sb.toString();
    }

    // ── CSV builder ───────────────────────────────────────────────────────────

    private static String buildCsv(List<HistoryEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("timestamp,url,mark,latency_ms,message,response,note\n");
        for (HistoryEntry e : entries) {
            sb.append(csvCell(e.timestamp)).append(",");
            sb.append(csvCell(e.url)).append(",");
            sb.append(csvCell(e.mark)).append(",");
            sb.append(e.latencyMs).append(",");
            sb.append(csvCell(e.message)).append(",");
            sb.append(csvCell(e.response)).append(",");
            sb.append(csvCell(e.note)).append("\n");
        }
        return sb.toString();
    }

    private static String csvCell(String v) {
        if (v == null) return "\"\"";
        return "\"" + v.replace("\"", "\"\"").replace("\n", "\\n") + "\"";
    }

    // ── Swing helpers ─────────────────────────────────────────────────────────

    private static JPanel section(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(SURFACE, 1), title,
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                javax.swing.border.TitledBorder.DEFAULT_POSITION,
                new Font("Monospaced", Font.BOLD, Math.max(BASE_SIZE - 2, 10)), MUTED));
        return p;
    }

    private static JPanel wrap(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        p.setBackground(BG);
        p.add(c);
        return p;
    }

    private static JPanel row(JComponent... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        p.setBackground(BG);
        for (JComponent c : items) p.add(c);
        return p;
    }

    private static JRadioButton radio(String text) {
        JRadioButton b = new JRadioButton(text);
        b.setBackground(BG);
        b.setForeground(FG);
        b.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        b.setFocusPainted(false);
        return b;
    }

    private static JCheckBox check(String text) {
        JCheckBox cb = new JCheckBox(text);
        cb.setBackground(BG);
        cb.setForeground(FG);
        cb.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        cb.setFocusPainted(false);
        return cb;
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
}
