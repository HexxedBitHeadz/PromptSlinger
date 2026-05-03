package com.promptslinger.burp;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

import static com.promptslinger.burp.PSPanel.*;

public class DecodeWindow extends JFrame {

    private final String[] currentText;   // mutable so lambdas can update it
    private final JTextArea resultArea;

    private static final DecoderEntry[] DECODERS = {
        new DecoderEntry("Spell-out", DecoderUtil::fromSpell),
        new DecoderEntry("ROT13",     DecoderUtil::rot13),
        new DecoderEntry("Reverse",   DecoderUtil::reverse),
        new DecoderEntry("Base64",    DecoderUtil::fromBase64),
        new DecoderEntry("Hex",       DecoderUtil::fromHex),
    };

    @FunctionalInterface
    private interface ThrowingFn { String apply(String s) throws Exception; }
    private record DecoderEntry(String label, ThrowingFn fn) {}

    public DecodeWindow(String responseText, String activeModifierKey) {
        super("Decode Response — PromptSlinger");
        currentText = new String[]{responseText};

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(700, 500);
        setMinimumSize(new Dimension(500, 360));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(0, 0));

        // â”€â”€ Title â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        JLabel title = new JLabel("Decode Response");
        title.setFont(new Font("Monospaced", Font.BOLD, 13));
        title.setForeground(ACCENT);
        title.setBorder(BorderFactory.createEmptyBorder(10, 14, 2, 14));
        add(title, BorderLayout.NORTH);

        // â”€â”€ Result area â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        resultArea = new JTextArea();
        resultArea.setBackground(SURFACE);
        resultArea.setForeground(GREEN);
        resultArea.setFont(MONO);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setEditable(false);
        resultArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JScrollPane scroll = new JScrollPane(resultArea);
        scroll.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        scroll.setBackground(BG);
        add(scroll, BorderLayout.CENTER);

        // â”€â”€ Bottom button bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8));
        bar.setBackground(BG);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

        JLabel tryLabel = new JLabel("Try:");
        tryLabel.setForeground(MUTED);
        tryLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));
        bar.add(tryLabel);

        for (DecoderEntry d : DECODERS) {
            JButton btn = smallBtn(d.label());
            btn.addActionListener(e -> {
                try {
                    currentText[0] = d.fn().apply(currentText[0]);
                    resultArea.setText(currentText[0]);
                } catch (Exception ex) {
                    resultArea.setText("[Decode failed: " + ex.getMessage() + "]\n\n" + currentText[0]);
                }
            });
            bar.add(btn);
        }

        // Spacer
        bar.add(Box.createHorizontalGlue());

        JButton resetBtn = smallBtn("Reset");
        resetBtn.addActionListener(e -> {
            currentText[0] = responseText;
            resultArea.setText(responseText);
        });

        JButton copyBtn = smallBtn("Copy");
        copyBtn.setForeground(ACCENT);
        copyBtn.addActionListener(e ->
            Toolkit.getDefaultToolkit().getSystemClipboard()
                   .setContents(new StringSelection(resultArea.getText()), null));

        bar.add(resetBtn);
        bar.add(copyBtn);
        add(bar, BorderLayout.SOUTH);

        // â”€â”€ Auto-decode based on the active modifier (reverse order) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        String autoDecoded = tryAutoDecode(responseText, activeModifierKey);
        currentText[0] = autoDecoded;
        resultArea.setText(autoDecoded);
    }

    private String tryAutoDecode(String text, String modifierKey) {
        if (modifierKey == null) return text;
        try {
            return switch (modifierKey) {
                case "rot13"   -> DecoderUtil.rot13(text);
                case "reverse" -> DecoderUtil.reverse(text);
                case "base64"  -> DecoderUtil.fromBase64(text);
                case "hex"     -> DecoderUtil.fromHex(text);
                case "spell"   -> DecoderUtil.fromSpell(text);
                default        -> text;
            };
        } catch (Exception e) {
            return text;
        }
    }

    private static JButton smallBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(SURFACE);
        b.setForeground(FG);
        b.setFont(new Font("Monospaced", Font.PLAIN, 10));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
