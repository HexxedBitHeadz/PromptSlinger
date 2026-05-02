package com.promptslinger.burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PSPanel extends JPanel {

    // â”€â”€ Colours â€” populated from the active theme at construction time â”€â”€â”€â”€â”€â”€â”€â”€
    static Color BG, SURFACE, ENTRY_BG, FG, ACCENT, GREEN, RED, MUTED, ORANGE, PINK;

    static int  BASE_SIZE = 13;
    static Font MONO      = new Font("Monospaced", Font.PLAIN,  BASE_SIZE);
    static Font MONO_BOLD = new Font("Monospaced", Font.BOLD,   BASE_SIZE);

    // Mark labels â€” mutable so users can rename them; persisted to ~/.promptslinger/mark_names.txt
    static String[] MARK_KEYS = {"FINDING", "HINT", "INFO", "CONFIRMED", "NOISE"};
    static final Color[] MARK_BG = {
        new Color(0xff5555), new Color(0xf1fa8c), new Color(0x8be9fd),
        new Color(0x50fa7b), new Color(0x6272a4)
    };
    // Text on mark chips â€” always dark so it reads on any coloured background
    static final Color CHIP_TEXT = new Color(0x1a1a2e);
    static Color[] MARK_FG = {CHIP_TEXT, CHIP_TEXT, CHIP_TEXT, CHIP_TEXT, CHIP_TEXT};

    static void applyTheme(Theme t) {
        BG       = t.BG;
        SURFACE  = t.SURFACE;
        ENTRY_BG = t.ENTRY_BG;
        FG       = t.FG;
        ACCENT   = t.ACCENT;
        GREEN    = t.GREEN;
        RED      = t.RED;
        MUTED    = t.MUTED;
        ORANGE   = t.ORANGE;
        PINK     = t.PINK;
    }

    // â”€â”€ State â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private final MontoyaApi   api;
    private final HistoryStore store;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private HttpRequestResponse currentProxyItem;
    private HttpRequest         currentRequest;
    private String              currentSessionId;
    String                      lastResponseText; // DecodeWindow reads this

    // Currently selected modifier key, or null
    private String activeModifier = null;

    // Active send thread â€” non-null while a request is in flight
    private Thread activeWorker = null;

    // Inline history side panel
    private HistoryPanel historyPanel;
    private JSplitPane   mainSplit;
    private JButton      histToggleBtn;
    private static final int HISTORY_WIDTH = 380;

    private static final java.nio.file.Path MARK_NAMES_FILE =
        java.nio.file.Paths.get(System.getProperty("user.home"), ".promptslinger", "mark_names.txt");

    static void saveMarkNames() {
        try {
            java.nio.file.Files.createDirectories(MARK_NAMES_FILE.getParent());
            java.nio.file.Files.writeString(MARK_NAMES_FILE, String.join("\n", MARK_KEYS));
        } catch (Exception ignored) {}
    }

    static void loadMarkNames() {
        try {
            if (java.nio.file.Files.exists(MARK_NAMES_FILE)) {
                String[] loaded = java.nio.file.Files.readString(MARK_NAMES_FILE).split("\n", -1);
                for (int i = 0; i < Math.min(loaded.length, MARK_KEYS.length); i++)
                    if (!loaded[i].isBlank()) MARK_KEYS[i] = loaded[i].trim();
            }
        } catch (Exception ignored) {}
    }

    // â”€â”€ UI components â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private JLabel     methodLabel;
    private JTextField urlField;
    private JLabel     sessionLabel;
    private JTextField fieldNameInput;
    JTextArea  messageArea;
    private JCheckBox[]modifierBoxes;
    private JTextPane  responsePane;
    private JButton    sendBtn;

    // Named text styles for the response pane
    private Style keyStyle, strStyle, numStyle, boolStyle,
                  labelStyle, prominentStyle, separatorStyle, mutedStyle;

    public PSPanel(MontoyaApi api, HistoryStore store) {
        this.api   = api;
        this.store = store;
        applyTheme(Theme.loadSaved());
        loadMarkNames();
        Font lf = UIManager.getFont("Label.font");
        BASE_SIZE = (lf != null) ? lf.getSize() : 13;
        MONO      = new Font("Monospaced", Font.PLAIN, BASE_SIZE);
        MONO_BOLD = new Font("Monospaced", Font.BOLD,  BASE_SIZE);
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);
        buildUI();
    }

    // â”€â”€ Public API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Called by the context menu when the user picks "Send to PromptSlinger". */
    public void loadRequest(HttpRequestResponse rr) {
        this.currentProxyItem = rr;
        this.currentRequest   = rr.request();

        SwingUtilities.invokeLater(() -> {
            String method = currentRequest.method();
            String url    = currentRequest.url();
            methodLabel.setText(method);
            methodLabel.setForeground(GREEN);
            urlField.setText(url);
            urlField.setForeground(GREEN);
            urlField.setEditable(true);

            String detected = autoDetectField(currentRequest);
            if (detected != null) fieldNameInput.setText(detected);

            sendBtn.setEnabled(true);
            showInfo("Request loaded: " + method + " " + url);
        });
    }

    // â”€â”€ UI construction â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void buildUI() {
        // â”€â”€ Top info bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        JPanel topBar = panel(BG, new BorderLayout(0, 4));
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 14, 6, 14));

        JPanel titleRow = panel(BG, new BorderLayout());
        JPanel titleStack = panel(BG, new BorderLayout(0, 1));
        JLabel title = new JLabel("PromptSlinger");
        title.setFont(new Font("Monospaced", Font.BOLD, BASE_SIZE + 2));
        title.setForeground(ACCENT);
        JLabel byline = new JLabel("  By Hexxed BitHeadz");
        byline.setFont(new Font("Monospaced", Font.ITALIC, Math.max(BASE_SIZE - 3, 9)));
        byline.setForeground(MUTED);
        titleStack.add(title,  BorderLayout.NORTH);
        titleStack.add(byline, BorderLayout.SOUTH);
        titleRow.add(titleStack, BorderLayout.WEST);

        // â”€â”€ Right-side controls: theme picker + history toggle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        JPanel controls = panel(BG, new FlowLayout(FlowLayout.RIGHT, 6, 0));

        JLabel reloadNote = new JLabel("↺ reload to apply");
        reloadNote.setFont(new Font("Monospaced", Font.ITALIC, Math.max(BASE_SIZE - 3, 9)));
        reloadNote.setForeground(ORANGE);
        reloadNote.setVisible(false);

        JComboBox<String> themeCombo = new JComboBox<>();
        Theme savedTheme = Theme.loadSaved();
        for (Theme t : Theme.ALL) themeCombo.addItem(t.name);
        themeCombo.setSelectedItem(savedTheme.name);
        themeCombo.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        themeCombo.setBackground(SURFACE);
        themeCombo.setForeground(FG);
        themeCombo.setFocusable(false);
        themeCombo.addActionListener(e -> {
            String selected = (String) themeCombo.getSelectedItem();
            for (Theme t : Theme.ALL) {
                if (t.name.equals(selected)) {
                    Theme.save(t);
                    reloadNote.setVisible(true);
                    break;
                }
            }
        });

        histToggleBtn = smallButton("◀ History");
        histToggleBtn.setForeground(ACCENT);
        histToggleBtn.addActionListener(e -> toggleHistoryPanel());

        controls.add(reloadNote);
        controls.add(themeCombo);
        controls.add(Box.createHorizontalStrut(8));
        controls.add(histToggleBtn);
        titleRow.add(controls, BorderLayout.EAST);

        topBar.add(titleRow, BorderLayout.NORTH);

        JPanel metaRow = panel(BG, new GridLayout(2, 1, 0, 3));

        JPanel endpointRow = panel(BG, new BorderLayout(6, 0));
        methodLabel = new JLabel("...");
        methodLabel.setFont(MONO_BOLD);
        methodLabel.setForeground(MUTED);
        methodLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        endpointRow.add(methodLabel, BorderLayout.WEST);

        urlField = new JTextField("No request loaded — right-click a request → Send to PromptSlinger");
        urlField.setFont(MONO);
        urlField.setForeground(MUTED);
        urlField.setBackground(BG);
        urlField.setCaretColor(FG);
        urlField.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        urlField.setEditable(false);
        endpointRow.add(urlField, BorderLayout.CENTER);
        metaRow.add(endpointRow);

        JPanel fieldRow = panel(BG, new FlowLayout(FlowLayout.LEFT, 0, 0));
        fieldRow.add(label("Message field: ", MUTED));
        fieldNameInput = new JTextField("message", 14);
        style(fieldNameInput);
        fieldRow.add(fieldNameInput);
        fieldRow.add(Box.createHorizontalStrut(8));
        JButton autoBtn = smallButton("Auto-detect");
        autoBtn.addActionListener(e -> {
            if (currentRequest != null) {
                String f = autoDetectField(currentRequest);
                if (f != null) fieldNameInput.setText(f);
            }
        });
        fieldRow.add(autoBtn);

        fieldRow.add(Box.createHorizontalStrut(20));
        sessionLabel = new JLabel("Session ID: none");
        sessionLabel.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        sessionLabel.setForeground(MUTED);
        fieldRow.add(sessionLabel);
        fieldRow.add(Box.createHorizontalStrut(6));
        JButton clearSessBtn = smallButton("✕ clear session");
        clearSessBtn.setForeground(MUTED);
        clearSessBtn.addActionListener(e -> clearSession());
        fieldRow.add(clearSessBtn);

        metaRow.add(fieldRow);
        topBar.add(metaRow, BorderLayout.CENTER);

        add(topBar, BorderLayout.NORTH);

        // â”€â”€ Input panel (message area + modifiers) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        JPanel inputPanel = panel(BG, new BorderLayout(0, 4));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));

        inputPanel.add(label("Message:", FG), BorderLayout.NORTH);

        messageArea = new JTextArea(5, 40);
        messageArea.setBackground(ENTRY_BG);
        messageArea.setForeground(FG);
        messageArea.setCaretColor(FG);
        messageArea.setFont(MONO);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        // Ctrl+Enter sends, plain Enter inserts newline
        messageArea.getInputMap().put(KeyStroke.getKeyStroke("control ENTER"), "send");
        messageArea.getActionMap().put("send", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { sendRequest(); }
        });

        JScrollPane msgScroll = new JScrollPane(messageArea);
        msgScroll.setBorder(BorderFactory.createLineBorder(SURFACE, 2));
        inputPanel.add(msgScroll, BorderLayout.CENTER);

        // Modifiers row
        JPanel modRow = panel(BG, new FlowLayout(FlowLayout.LEFT, 6, 2));
        modRow.add(label("Output modifiers:", MUTED));
        modifierBoxes = new JCheckBox[ModifierUtil.MODIFIERS.length];
        for (int i = 0; i < ModifierUtil.MODIFIERS.length; i++) {
            ModifierUtil.Modifier m = ModifierUtil.MODIFIERS[i];
            JCheckBox cb = new JCheckBox(m.label());
            cb.setBackground(BG);
            cb.setForeground(MUTED);
            cb.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
            cb.setFocusPainted(false);
            final String key = m.key();
            cb.addActionListener(e -> toggleModifier(key));
            modifierBoxes[i] = cb;
            modRow.add(cb);
        }
        inputPanel.add(modRow, BorderLayout.SOUTH);

        // â”€â”€ Button bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        JPanel btnBar = panel(BG, new FlowLayout(FlowLayout.CENTER, 8, 8));

        sendBtn = new JButton("Send");
        sendBtn.setBackground(ACCENT);
        sendBtn.setForeground(BG);
        sendBtn.setFont(MONO_BOLD);
        sendBtn.setFocusPainted(false);
        sendBtn.setBorderPainted(false);
        sendBtn.setEnabled(false);
        sendBtn.addActionListener(e -> sendRequest());
        btnBar.add(sendBtn);

        JButton decodeBtn = actionButton("Decode",  ORANGE);
        decodeBtn.addActionListener(e -> openDecodeWindow());
        btnBar.add(decodeBtn);

        JButton copyBtn = actionButton("Copy Response", FG);
        copyBtn.addActionListener(e -> copyResponse());
        btnBar.add(copyBtn);

        JButton clearBtn = actionButton("Clear", MUTED);
        clearBtn.addActionListener(e -> clearAll());
        btnBar.add(clearBtn);

        // â”€â”€ Response area â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        JPanel responsePanel = panel(BG, new BorderLayout(0, 4));
        responsePanel.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));
        responsePanel.add(label("Response:", FG), BorderLayout.NORTH);

        responsePane = new JTextPane();
        responsePane.setEditable(false);
        responsePane.setBackground(SURFACE);
        responsePane.setForeground(FG);
        responsePane.setFont(MONO);
        responsePane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        initResponseStyles();

        JScrollPane respScroll = new JScrollPane(responsePane);
        respScroll.setBorder(BorderFactory.createLineBorder(SURFACE, 2));
        responsePanel.add(respScroll, BorderLayout.CENTER);

        // â”€â”€ Assemble with vertical split â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setBackground(BG);
        split.setBorder(null);
        split.setDividerSize(5);
        split.setResizeWeight(0.35);

        JPanel topHalf = panel(BG, new BorderLayout());
        topHalf.add(inputPanel, BorderLayout.CENTER);
        topHalf.add(btnBar, BorderLayout.SOUTH);

        split.setTopComponent(topHalf);
        split.setBottomComponent(responsePanel);

        historyPanel = new HistoryPanel(api, store, this);
        historyPanel.setMinimumSize(new Dimension(0, 0));

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setBackground(BG);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(5);
        mainSplit.setLeftComponent(historyPanel);
        mainSplit.setRightComponent(split);
        mainSplit.setDividerLocation(HISTORY_WIDTH);

        add(mainSplit, BorderLayout.CENTER);
    }

    // â”€â”€ Send logic â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void resetSendButton() {
        activeWorker = null;
        sendBtn.setText("Send");
        sendBtn.setBackground(ACCENT);
        sendBtn.setForeground(BG);
        sendBtn.setEnabled(currentRequest != null);
    }

    private void sendRequest() {
        if (activeWorker != null) {
            activeWorker.interrupt();
            return;
        }
        if (currentRequest == null) {
            showError("No request loaded.");
            return;
        }
        String rawMessage = messageArea.getText().trim();
        if (rawMessage.isEmpty()) {
            showError("Message is empty.");
            return;
        }
        String fieldName = fieldNameInput.getText().trim();
        if (fieldName.isEmpty()) {
            showError("Message field name is empty.");
            return;
        }

        String finalMessage = ModifierUtil.applyModifier(rawMessage, activeModifier);

        sendBtn.setText("✕ Cancel");
        sendBtn.setBackground(RED);
        sendBtn.setForeground(FG);
        sendBtn.setEnabled(true);
        final String targetUrl = urlField.getText().trim();
        showInfo("POST " + targetUrl + "\n\nWaiting…");

        final String capturedMessage = rawMessage;

        Thread worker = new Thread(() -> {
            try {
                // Patch body: replace message field (and session_id if we have one)
                String body   = currentRequest.bodyToString();
                JsonNode root = mapper.readTree(body);
                if (!(root instanceof ObjectNode on))
                    throw new IllegalStateException("Request body is not a JSON object.");

                on.put(fieldName, finalMessage);
                if (currentSessionId != null && on.has("session_id"))
                    on.put("session_id", currentSessionId);

                java.net.URI uri = new java.net.URI(targetUrl);
                String host = uri.getHost();
                int port = uri.getPort() == -1 ? (uri.getScheme().equalsIgnoreCase("https") ? 443 : 80) : uri.getPort();
                boolean secure = uri.getScheme().equalsIgnoreCase("https");
                String path = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
                if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
                String hostHeader = (port == 80 || port == 443) ? host : host + ":" + port;

                HttpRequest modified = currentRequest
                        .withService(HttpService.httpService(host, port, secure))
                        .withPath(path)
                        .withUpdatedHeader("Host", hostHeader)
                        .withBody(mapper.writeValueAsString(on));
                HttpRequestResponse result = api.http().sendRequest(modified);

                if (Thread.currentThread().isInterrupted()) {
                    SwingUtilities.invokeLater(() -> { showInfo("Request cancelled."); resetSendButton(); });
                    return;
                }

                String rawResp = result.response() != null
                        ? result.response().bodyToString() : "(no response body)";

                String pretty;
                JsonNode parsed;
                try {
                    parsed = mapper.readTree(rawResp);
                    pretty = mapper.writeValueAsString(parsed);
                } catch (Exception ex) {
                    parsed = null;
                    pretty = rawResp;
                }

                // Auto-capture session_id from response
                String newSessionId = null;
                if (parsed instanceof ObjectNode op && op.has("session_id"))
                    newSessionId = op.get("session_id").asText();

                String displayText     = pretty;
                JsonNode parsedFinal   = parsed;
                String newSess         = newSessionId;
                String ts              = new SimpleDateFormat("HH:mm:ss").format(new Date());

                // Determine plain response text for the decode window
                String plainResp = (parsed instanceof ObjectNode op2 && op2.has("response"))
                        ? op2.get("response").asText() : pretty;

                HistoryEntry entry = new HistoryEntry(ts, currentRequest.url(),
                        capturedMessage, displayText, currentProxyItem, currentRequest, fieldName);
                store.add(entry);

                final String pr = plainResp;
                SwingUtilities.invokeLater(() -> {
                    lastResponseText = pr;
                    if (newSess != null) updateSession(newSess);
                    renderResponse(parsedFinal, displayText);
                    resetSendButton();
                    if (mainSplit.getDividerLocation() > 10) historyPanel.refresh();
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    showError("Error: " + ex.getMessage());
                    resetSendButton();
                });
            }
        }, "promptslinger-send");
        worker.setDaemon(true);
        activeWorker = worker;
        worker.start();
    }

    // â”€â”€ Response rendering â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void initResponseStyles() {
        StyledDocument doc = responsePane.getStyledDocument();
        Style base = doc.addStyle("base", null);
        StyleConstants.setFontFamily(base, "Monospaced");
        StyleConstants.setFontSize(base, BASE_SIZE);
        StyleConstants.setForeground(base, FG);

        keyStyle       = doc.addStyle("key",       base); StyleConstants.setForeground(keyStyle,       ACCENT);
        strStyle       = doc.addStyle("str",       base); StyleConstants.setForeground(strStyle,       GREEN);
        numStyle       = doc.addStyle("num",       base); StyleConstants.setForeground(numStyle,       ORANGE);
        boolStyle      = doc.addStyle("bool",      base); StyleConstants.setForeground(boolStyle,      PINK);
        labelStyle     = doc.addStyle("lbl",       base);
        StyleConstants.setForeground(labelStyle, MUTED);
        StyleConstants.setFontSize(labelStyle, BASE_SIZE - 2);
        prominentStyle = doc.addStyle("prom",      base);
        StyleConstants.setForeground(prominentStyle, FG);
        StyleConstants.setFontSize(prominentStyle, BASE_SIZE + 1);
        separatorStyle = doc.addStyle("sep",       base); StyleConstants.setForeground(separatorStyle, new Color(0x44475a));
        mutedStyle     = doc.addStyle("muted",     base);
        StyleConstants.setForeground(mutedStyle, MUTED);
        StyleConstants.setFontSize(mutedStyle, BASE_SIZE - 2);
    }

    private static final Pattern P_KEY  = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"(?=\\s*:)");
    private static final Pattern P_STR  = Pattern.compile("(?<=:\\s{0,10})\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern P_NUM  = Pattern.compile("(?<=:\\s{0,10})-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");
    private static final Pattern P_BOOL = Pattern.compile("(?<=:\\s{0,10})(?:true|false|null)\\b");

    private void renderResponse(JsonNode parsed, String pretty) {
        StyledDocument doc = responsePane.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());

            if (parsed != null && parsed.has("response")) {
                doc.insertString(doc.getLength(), "RESPONSE\n",                     labelStyle);
                doc.insertString(doc.getLength(), parsed.get("response").asText() + "\n", prominentStyle);
                doc.insertString(doc.getLength(), "─".repeat(60) + "\n",           separatorStyle);
                doc.insertString(doc.getLength(), "RAW JSON\n",                    labelStyle);
            }

            int jsonStart = doc.getLength();
            doc.insertString(jsonStart, pretty, mutedStyle);
            applyJsonHighlighting(doc, jsonStart, pretty);

        } catch (BadLocationException ignored) {}

        responsePane.setCaretPosition(0);
    }

    private void applyJsonHighlighting(StyledDocument doc, int off, String text) {
        applyStyle(doc, off, text, P_KEY,  keyStyle,  0);
        applyStyle(doc, off, text, P_STR,  strStyle,  0);
        applyStyle(doc, off, text, P_NUM,  numStyle,  0);
        applyStyle(doc, off, text, P_BOOL, boolStyle, 0);
    }

    private void applyStyle(StyledDocument doc, int off, String text,
                             Pattern p, Style style, int group) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            int s = group == 0 ? m.start() : m.start(group);
            int e = group == 0 ? m.end()   : m.end(group);
            doc.setCharacterAttributes(off + s, e - s, style, false);
        }
    }

    // â”€â”€ Modifier checkboxes (single-select) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void toggleModifier(String key) {
        if (key.equals(activeModifier)) {
            // Clicking the active one again deactivates it
            activeModifier = null;
        } else {
            activeModifier = key;
        }
        // Enforce single selection
        for (int i = 0; i < ModifierUtil.MODIFIERS.length; i++) {
            boolean active = ModifierUtil.MODIFIERS[i].key().equals(activeModifier);
            modifierBoxes[i].setSelected(active);
            modifierBoxes[i].setForeground(active ? ACCENT : MUTED);
        }
        // Rewrite any existing suffix in the message box
        String cur  = messageArea.getText();
        String base = ModifierUtil.stripAllSuffixes(cur);
        String next = ModifierUtil.applyModifier(base, activeModifier);
        if (!next.equals(cur)) {
            messageArea.setText(next);
            messageArea.setCaretPosition(next.length());
        }
    }

    // â”€â”€ Session management â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void updateSession(String sid) {
        currentSessionId = sid;
        sessionLabel.setText("Session ID: " + sid.substring(0, Math.min(36, sid.length())));
        sessionLabel.setForeground(ACCENT);
    }

    private void clearSession() {
        currentSessionId = null;
        sessionLabel.setText("Session ID: none");
        sessionLabel.setForeground(MUTED);
    }

    // â”€â”€ Auxiliary actions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void copyResponse() {
        String text = responsePane.getText();
        if (!text.isBlank()) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                   .setContents(new java.awt.datatransfer.StringSelection(text), null);
        }
    }

    private void clearAll() {
        if (activeWorker != null) {
            activeWorker.interrupt();
        }
        messageArea.setText("");
        clearSession();
        for (JCheckBox cb : modifierBoxes) cb.setSelected(false);
        activeModifier = null;
        StyledDocument doc = responsePane.getStyledDocument();
        try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {}
    }

    private void openDecodeWindow() {
        if (lastResponseText == null || lastResponseText.isBlank()) {
            showInfo("No response to decode yet.");
            return;
        }
        new DecodeWindow(lastResponseText, activeModifier).setVisible(true);
    }

    private void toggleHistoryPanel() {
        if (mainSplit.getDividerLocation() > 10) {
            mainSplit.setDividerLocation(0);
            histToggleBtn.setText("▶ History");
        } else {
            historyPanel.refresh();
            mainSplit.setDividerLocation(HISTORY_WIDTH);
            histToggleBtn.setText("◀ History");
        }
    }

    // â”€â”€ Info / error display in response pane â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    void showInfo(String msg) {
        StyledDocument doc = responsePane.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, msg, null);
        } catch (BadLocationException ignored) {}
    }

    private void showError(String msg) {
        StyledDocument doc = responsePane.getStyledDocument();
        Style err = doc.addStyle("err", null);
        StyleConstants.setForeground(err, RED);
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, msg, err);
        } catch (BadLocationException ignored) {}
    }

    // â”€â”€ Field auto-detection â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private String autoDetectField(HttpRequest request) {
        String[] candidates = {"message", "prompt", "query", "input", "text",
                               "content", "msg", "user_input", "user_message"};
        try {
            JsonNode root = mapper.readTree(request.bodyToString());
            for (String c : candidates) {
                if (root.has(c)) return c;
            }
            // Fall back to first string-valued field
            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> f = it.next();
                if (f.getValue().isTextual()) return f.getKey();
            }
        } catch (Exception ignored) {}
        return null;
    }

    // â”€â”€ Swing helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static JPanel panel(Color bg, LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setBackground(bg);
        return p;
    }

    private static JLabel label(String text, Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setFont(MONO);
        return l;
    }

    private static void style(JTextField tf) {
        tf.setBackground(new Color(0x313244));
        tf.setForeground(GREEN);
        tf.setCaretColor(FG);
        tf.setFont(MONO);
        tf.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
    }

    private static JButton smallButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(0x313244));
        b.setForeground(FG);
        b.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JButton actionButton(String text, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(new Color(0x2a2a3d));
        b.setForeground(fg);
        b.setFont(MONO_BOLD);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
