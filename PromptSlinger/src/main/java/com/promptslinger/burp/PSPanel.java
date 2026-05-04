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
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PSPanel extends JPanel {

    // â"€â"€ Colours â€" populated from the active theme at construction time â"€â"€â"€â"€â"€â"€â"€â"€
    static Color BG, SURFACE, ENTRY_BG, FG, ACCENT, GREEN, RED, MUTED, ORANGE, PINK;

    static int  BASE_SIZE = 13;
    static Font MONO      = new Font("Monospaced", Font.PLAIN,  BASE_SIZE);
    static Font MONO_BOLD = new Font("Monospaced", Font.BOLD,   BASE_SIZE);

    // Mark labels â€" mutable so users can rename them; persisted to ~/.promptslinger/mark_names.txt
    static String[] MARK_KEYS = {"FINDING", "HINT", "INFO", "CONFIRMED", "NOISE"};
    static final Color[] MARK_BG = {
        new Color(0xff5555), new Color(0xf1fa8c), new Color(0x8be9fd),
        new Color(0x50fa7b), new Color(0x6272a4)
    };
    // Text on mark chips â€" always dark so it reads on any coloured background
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

    // â"€â"€ State â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
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

    // Active send thread â€" non-null while a request is in flight
    private Thread activeWorker = null;

    // Left side panels
    private HistoryPanel       historyPanel;
    private InlinePayloadPanel inlinePayloadPanel;
    private JSplitPane         mainSplit;
    private JButton            histToggleBtn;
    private static final int   HISTORY_WIDTH = 380;

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

    // â"€â"€ UI components â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
    private JLabel     methodLabel;
    private JTextField urlField;
    private JLabel     sessionLabel;
    private JTextField fieldNameInput;
    JTextArea  messageArea;
    private JCheckBox[]modifierBoxes;
    private JTextPane  responsePane;
    private JButton    sendBtn;
    private JLabel     msgTokenLabel;
    private JLabel     respTokenLabel;

    // Multi-turn conversation state
    private JButton                multiTurnBtn;
    private JLabel                 turnCountLabel;
    private final List<Map<String,String>> conversationTurns = new ArrayList<>();
    private boolean                multiTurnMode     = false;
    private BatchFuzzDialog        batchDialog;

    // Endpoint slots (for Compare)
    private final List<EndpointSlot> slots = new ArrayList<>();
    private JButton                  compareBtn;
    private JLabel                   slotCountLabel;

    // Agent enumerator
    private AgentEnumeratorDialog enumeratorDialog;
    private HelpDialog            helpDialog;

    // Named text styles for the response pane
    private Style keyStyle, strStyle, numStyle, boolStyle,
                  labelStyle, prominentStyle, separatorStyle, mutedStyle;

    public PSPanel(MontoyaApi api, HistoryStore store) {
        this.api   = api;
        this.store = store;
        applyTheme(Theme.loadSaved());
        loadMarkNames();
        syncFontFromBurp();
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);
        buildUI();
        // Rebuild whenever Burp changes its font size in settings
        UIManager.addPropertyChangeListener(evt -> {
            java.awt.Font current = api.userInterface().currentDisplayFont();
            if (current != null && current.getSize() != BASE_SIZE) {
                SwingUtilities.invokeLater(() -> rebuildWithTheme(Theme.loadSaved()));
            }
        });
    }

    private void syncFontFromBurp() {
        java.awt.Font burpFont = api.userInterface().currentDisplayFont();
        java.awt.Font lf = (burpFont != null) ? burpFont : UIManager.getFont("Label.font");
        BASE_SIZE = (lf != null) ? lf.getSize() : 13;
        MONO      = new Font("Monospaced", Font.PLAIN, BASE_SIZE);
        MONO_BOLD = new Font("Monospaced", Font.BOLD,  BASE_SIZE);
    }

    // â"€â"€ Public API â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

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

    // â"€â"€ UI construction â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

    private void buildUI() {
        // â"€â"€ Top info bar â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
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

        // â"€â"€ Right-side controls: theme picker + history toggle â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
        JPanel controls = panel(BG, new FlowLayout(FlowLayout.RIGHT, 6, 0));

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
                    SwingUtilities.invokeLater(() -> rebuildWithTheme(t));
                    break;
                }
            }
        });

        histToggleBtn = smallButton("◄ Tools");
        histToggleBtn.setForeground(ACCENT);
        histToggleBtn.addActionListener(e -> toggleHistoryPanel());

        JButton helpBtn = smallButton("?");
        helpBtn.setForeground(GREEN);
        helpBtn.setToolTipText("PromptSlinger Help & Tutorial");
        helpBtn.addActionListener(e -> openHelp());

        controls.add(themeCombo);
        controls.add(Box.createHorizontalStrut(8));
        controls.add(helpBtn);
        controls.add(Box.createHorizontalStrut(4));
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

        // â"€â"€ Input panel (message area + modifiers) â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
        JPanel inputPanel = panel(BG, new BorderLayout(0, 4));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));

        JPanel msgHeader = panel(BG, new BorderLayout());
        msgHeader.add(label("Message:", FG), BorderLayout.WEST);
        msgTokenLabel = new JLabel("~0 tokens");
        msgTokenLabel.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 3, 9)));
        msgTokenLabel.setForeground(MUTED);
        msgHeader.add(msgTokenLabel, BorderLayout.EAST);
        inputPanel.add(msgHeader, BorderLayout.NORTH);

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

        messageArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateMsgTokens(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateMsgTokens(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
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

        // â"€â"€ Button bar â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
        JPanel btnBar = panel(BG, new FlowLayout(FlowLayout.CENTER, 8, 8));

        JButton enumBtn = actionButton("Enumerate", ACCENT);
        enumBtn.addActionListener(e -> openEnumerator());
        btnBar.add(enumBtn);

        sendBtn = new JButton("Send");
        sendBtn.setBackground(ACCENT);
        sendBtn.setForeground(BG);
        sendBtn.setFont(MONO_BOLD);
        sendBtn.setFocusPainted(false);
        sendBtn.setBorderPainted(false);
        sendBtn.setEnabled(false);
        sendBtn.addActionListener(e -> sendRequest());
        btnBar.add(sendBtn);

        JButton decodeBtn = actionButton("Decode", ORANGE);
        decodeBtn.addActionListener(e -> openDecodeWindow());
        btnBar.add(decodeBtn);

        JButton probesBtn = actionButton("Probes", PINK);
        probesBtn.addActionListener(e -> showProbesMenu(probesBtn));
        btnBar.add(probesBtn);

        JButton copyBtn = actionButton("Copy Response", FG);
        copyBtn.addActionListener(e -> copyResponse());
        btnBar.add(copyBtn);

        JButton clearBtn = actionButton("Clear", MUTED);
        clearBtn.addActionListener(e -> clearAll());
        btnBar.add(clearBtn);

        JButton fuzzBtn = actionButton("Batch", ORANGE);
        fuzzBtn.addActionListener(e -> openBatch());
        btnBar.add(fuzzBtn);

        multiTurnBtn = actionButton("Multi-turn: OFF", MUTED);
        multiTurnBtn.addActionListener(e -> toggleMultiTurn());
        btnBar.add(multiTurnBtn);

        turnCountLabel = new JLabel("0 turns");
        turnCountLabel.setFont(new Font("Monospaced", Font.ITALIC, Math.max(BASE_SIZE - 2, 10)));
        turnCountLabel.setForeground(GREEN);
        turnCountLabel.setVisible(false);
        btnBar.add(turnCountLabel);

        JButton clearConvoBtn = actionButton("Clear Convo", RED);
        clearConvoBtn.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        clearConvoBtn.addActionListener(e -> clearConversation());
        clearConvoBtn.setVisible(false);
        btnBar.add(clearConvoBtn);

        multiTurnBtn.putClientProperty("clearConvoBtn", clearConvoBtn);

        JButton saveSlotBtn = actionButton("Save Slot", ACCENT);
        saveSlotBtn.addActionListener(e -> saveCurrentSlot());
        btnBar.add(saveSlotBtn);

        compareBtn = actionButton("Compare", GREEN);
        compareBtn.setEnabled(false);
        compareBtn.addActionListener(e -> openCompareDialog());
        btnBar.add(compareBtn);

        slotCountLabel = new JLabel("0 slots");
        slotCountLabel.setFont(new Font("Monospaced", Font.ITALIC, Math.max(BASE_SIZE - 2, 10)));
        slotCountLabel.setForeground(MUTED);
        btnBar.add(slotCountLabel);


        // â"€â"€ Response area â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
        JPanel responsePanel = panel(BG, new BorderLayout(0, 4));
        responsePanel.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));
        JPanel respHeader = panel(BG, new BorderLayout());
        respHeader.add(label("Response:", FG), BorderLayout.WEST);
        respTokenLabel = new JLabel("");
        respTokenLabel.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 3, 9)));
        respTokenLabel.setForeground(MUTED);
        respHeader.add(respTokenLabel, BorderLayout.EAST);
        responsePanel.add(respHeader, BorderLayout.NORTH);

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

        // â"€â"€ Assemble with vertical split â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
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

        inlinePayloadPanel = new InlinePayloadPanel(this);
        inlinePayloadPanel.setMinimumSize(new Dimension(0, 0));

        // Custom tab bar for the left pane
        CardLayout leftCardLayout = new CardLayout();
        JPanel leftCards = new JPanel(leftCardLayout);
        leftCards.add(historyPanel,       "history");
        leftCards.add(inlinePayloadPanel, "payloads");

        JButton histTabBtn    = leftTabButton("History");
        JButton payloadTabBtn = leftTabButton("Payloads");
        histTabBtn.setBackground(ACCENT);
        histTabBtn.setForeground(BG);

        histTabBtn.addActionListener(e -> {
            leftCardLayout.show(leftCards, "history");
            histTabBtn.setBackground(ACCENT);      histTabBtn.setForeground(BG);
            payloadTabBtn.setBackground(SURFACE);  payloadTabBtn.setForeground(MUTED);
        });
        payloadTabBtn.addActionListener(e -> {
            leftCardLayout.show(leftCards, "payloads");
            payloadTabBtn.setBackground(ACCENT);   payloadTabBtn.setForeground(BG);
            histTabBtn.setBackground(SURFACE);     histTabBtn.setForeground(MUTED);
        });

        JPanel leftTabBar = panel(SURFACE, new GridLayout(1, 2, 1, 0));
        leftTabBar.add(histTabBtn);
        leftTabBar.add(payloadTabBtn);

        JPanel leftPane = new JPanel(new BorderLayout(0, 0));
        leftPane.setBackground(BG);
        leftPane.setMinimumSize(new Dimension(150, 0));
        leftPane.add(leftTabBar, BorderLayout.NORTH);
        leftPane.add(leftCards,  BorderLayout.CENTER);

        boolean isLinux = System.getProperty("os.name", "").toLowerCase().contains("linux");

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setBackground(BG);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(isLinux ? 0 : 8);
        mainSplit.setContinuousLayout(true);
        mainSplit.setEnabled(!isLinux);
        mainSplit.setLeftComponent(leftPane);
        mainSplit.setRightComponent(split);
        SwingUtilities.invokeLater(() -> {
            mainSplit.setDividerLocation(HISTORY_WIDTH);
            mainSplit.setLastDividerLocation(HISTORY_WIDTH);
        });

        add(mainSplit, BorderLayout.CENTER);
    }

    // â"€â"€ Send logic â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

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

        // Snapshot conversation before the worker thread starts (EDT-safe)
        final List<Map<String,String>> convoSnapshot = multiTurnMode
                ? new ArrayList<>(conversationTurns) : null;

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

                // Inject messages array for multi-turn mode
                if (convoSnapshot != null) {
                    com.fasterxml.jackson.databind.node.ArrayNode msgArr = mapper.createArrayNode();
                    for (Map<String,String> turn : convoSnapshot) {
                        com.fasterxml.jackson.databind.node.ObjectNode m = mapper.createObjectNode();
                        m.put("role",    turn.get("role"));
                        m.put("content", turn.get("content"));
                        msgArr.add(m);
                    }
                    com.fasterxml.jackson.databind.node.ObjectNode curMsg = mapper.createObjectNode();
                    curMsg.put("role",    "user");
                    curMsg.put("content", finalMessage);
                    msgArr.add(curMsg);
                    on.set("messages", msgArr);
                }

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
                long t0 = System.currentTimeMillis();
                HttpRequestResponse result = api.http().sendRequest(modified);
                final long latencyMs = System.currentTimeMillis() - t0;

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

                // Determine plain response text — try SSE assembly, then JSON field, then raw
                String plainResp = (parsed instanceof ObjectNode op2 && op2.has("response"))
                        ? op2.get("response").asText() : pretty;
                String sseAssembled = SseParser.assemble(rawResp);
                if (sseAssembled != null && !sseAssembled.isBlank()) plainResp = sseAssembled;

                HistoryEntry entry = new HistoryEntry(ts, currentRequest.url(),
                        capturedMessage, displayText, currentProxyItem, currentRequest, fieldName);
                entry.latencyMs = latencyMs;

                // Auto-mark if a keyword alert matches
                String autoMark = KeywordAlerts.check(plainResp);
                if (autoMark != null && entry.mark == null) entry.mark = autoMark;

                store.add(entry);

                final String pr         = plainResp;
                final int    respTokens = Math.max(1, displayText.length() / 4);
                SwingUtilities.invokeLater(() -> {
                    lastResponseText = pr;
                    respTokenLabel.setText("~" + respTokens + " tokens  " + latencyMs + "ms");
                    if (newSess != null) updateSession(newSess);
                    renderResponse(parsedFinal, displayText);
                    resetSendButton();
                    if (mainSplit.getDividerLocation() > 10) historyPanel.refresh();
                    // Update conversation history for multi-turn mode
                    if (convoSnapshot != null) {
                        conversationTurns.add(Map.of("role", "user",      "content", capturedMessage));
                        conversationTurns.add(Map.of("role", "assistant", "content", pr));
                        updateTurnLabel();
                    }
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

    // â"€â"€ Response rendering â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

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

    // â"€â"€ Modifier checkboxes (single-select) â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

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

    // â"€â"€ Session management â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

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

    // â"€â"€ Multi-turn conversation â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

    private void toggleMultiTurn() {
        multiTurnMode = !multiTurnMode;
        JButton clearConvoBtn = (JButton) multiTurnBtn.getClientProperty("clearConvoBtn");
        if (multiTurnMode) {
            multiTurnBtn.setText("Multi-turn: ON");
            multiTurnBtn.setForeground(GREEN);
            turnCountLabel.setVisible(true);
            if (clearConvoBtn != null) clearConvoBtn.setVisible(true);
            updateTurnLabel();
        } else {
            multiTurnBtn.setText("Multi-turn: OFF");
            multiTurnBtn.setForeground(MUTED);
            turnCountLabel.setVisible(false);
            if (clearConvoBtn != null) clearConvoBtn.setVisible(false);
        }
    }

    private void clearConversation() {
        conversationTurns.clear();
        updateTurnLabel();
    }

    private void updateTurnLabel() {
        int turns = conversationTurns.size() / 2;
        turnCountLabel.setText(turns + " turn" + (turns == 1 ? "" : "s"));
    }

    // â"€â"€ Batch fuzz â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

    private void openBatch() {
        if (currentRequest == null) {
            showInfo("Load a request first before opening Batch Send.");
            return;
        }
        if (batchDialog == null || !batchDialog.isDisplayable()) {
            batchDialog = new BatchFuzzDialog(
                    this, api, store, currentRequest,
                    urlField.getText().trim(),
                    fieldNameInput.getText().trim(),
                    historyPanel);
        }
        batchDialog.setVisible(true);
        batchDialog.toFront();
    }

    // â"€â"€ Endpoint slots & compare â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

    private void saveCurrentSlot() {
        if (currentRequest == null) { showInfo("Load a request first."); return; }
        String url         = urlField.getText().trim();
        String defaultName = extractHostname(url);
        String name = (String) JOptionPane.showInputDialog(
                this, "Slot name (max 4 slots):", "Save Endpoint Slot",
                JOptionPane.PLAIN_MESSAGE, null, null, defaultName);
        if (name == null || name.isBlank()) return;
        String trimmed = name.trim();
        slots.removeIf(s -> trimmed.equalsIgnoreCase(s.name));
        if (slots.size() >= 4) {
            JOptionPane.showMessageDialog(this,
                    "Maximum 4 slots reached. Use an existing slot name to overwrite it.",
                    "Slots Full", JOptionPane.WARNING_MESSAGE);
            return;
        }
        slots.add(new EndpointSlot(trimmed, url, currentRequest.method(),
                fieldNameInput.getText().trim(), currentRequest, currentProxyItem, currentSessionId));
        updateSlotLabel();
        showInfo("Saved slot: " + trimmed + "  (" + slots.size() + " total)");
    }

    private void updateSlotLabel() {
        int n = slots.size();
        slotCountLabel.setText(n + " slot" + (n == 1 ? "" : "s"));
        slotCountLabel.setForeground(n > 0 ? ACCENT : MUTED);
        compareBtn.setEnabled(n >= 2);
    }

    private String extractHostname(String url) {
        try { return new java.net.URI(url).getHost(); } catch (Exception e) { return "slot"; }
    }

    private void openCompareDialog() {
        List<EndpointSlot> loaded = new ArrayList<>();
        for (EndpointSlot s : slots) { if (s.hasRequest()) loaded.add(s); }
        if (loaded.size() < 2) { showInfo("Need at least 2 saved slots to compare."); return; }
        String msg = messageArea.getText().trim();
        if (msg.isEmpty()) { showInfo("Enter a message to send for comparison."); return; }
        new CompareDialog(this, api, store, loaded, msg, activeModifier).setVisible(true);
    }

    // â"€â"€ Auxiliary actions â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

    private PayloadBrowserDialog payloadBrowser;

    private void openPayloadBrowser() {
        if (payloadBrowser == null || !payloadBrowser.isDisplayable()) {
            payloadBrowser = new PayloadBrowserDialog(this);
        }
        payloadBrowser.setVisible(true);
        payloadBrowser.toFront();
    }

    private void showProbesMenu(JButton anchor) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(SURFACE);

        java.util.List<SavedProbes.Probe> probes = SavedProbes.getAll();
        if (probes.isEmpty()) {
            JMenuItem empty = new JMenuItem("(no saved probes)");
            empty.setBackground(SURFACE);
            empty.setForeground(MUTED);
            empty.setFont(MONO);
            empty.setEnabled(false);
            menu.add(empty);
        } else {
            for (int i = 0; i < probes.size(); i++) {
                final int idx = i;
                SavedProbes.Probe p = probes.get(i);
                JMenuItem item = new JMenuItem(p.name);
                item.setBackground(SURFACE);
                item.setForeground(FG);
                item.setFont(MONO);
                item.addActionListener(e -> {
                    messageArea.setText(p.text);
                    messageArea.requestFocus();
                });
                // Right-click on item to delete
                item.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mousePressed(java.awt.event.MouseEvent e) {
                        if (SwingUtilities.isRightMouseButton(e)) {
                            menu.setVisible(false);
                            int confirm = JOptionPane.showConfirmDialog(
                                    PSPanel.this, "Delete probe \"" + p.name + "\"?",
                                    "Delete Probe", JOptionPane.YES_NO_OPTION);
                            if (confirm == JOptionPane.YES_OPTION) SavedProbes.remove(idx);
                        }
                    }
                });
                menu.add(item);
            }
        }

        menu.addSeparator();
        JMenuItem saveItem = new JMenuItem("Save current message as probe...");
        saveItem.setBackground(SURFACE);
        saveItem.setForeground(ACCENT);
        saveItem.setFont(MONO);
        saveItem.addActionListener(e -> saveCurrentProbe());
        menu.add(saveItem);

        menu.show(anchor, 0, anchor.getHeight());
    }

    private void saveCurrentProbe() {
        String text = messageArea.getText().trim();
        if (text.isEmpty()) { showInfo("Message is empty — nothing to save."); return; }
        String name = (String) JOptionPane.showInputDialog(
                this, "Name for this probe:", "Save Probe",
                JOptionPane.PLAIN_MESSAGE, null, null,
                text.length() > 40 ? text.substring(0, 40) + "..." : text);
        if (name != null && !name.isBlank()) SavedProbes.add(name.trim(), text);
    }

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
            mainSplit.setLastDividerLocation(HISTORY_WIDTH);
            mainSplit.setDividerLocation(0);
            histToggleBtn.setText("► Tools");
        } else {
            historyPanel.refresh();
            mainSplit.setDividerLocation(HISTORY_WIDTH);
            mainSplit.setLastDividerLocation(HISTORY_WIDTH);
            histToggleBtn.setText("◄ Tools");
        }
    }

    // ── Live theme rebuild ────────────────────────────────────────────────────

    private void rebuildWithTheme(Theme t) {
        // Snapshot all transient state that survives a UI rebuild
        String              savedMsg     = messageArea  != null ? messageArea.getText()  : "";
        String              savedLastRsp = lastResponseText;
        String              savedUrl     = urlField     != null ? urlField.getText()     : "";
        String              savedMethod  = methodLabel  != null ? methodLabel.getText()  : "...";
        String              savedSession = currentSessionId;
        String              savedMod     = activeModifier;
        boolean             wasMTOn      = multiTurnMode;
        HttpRequest         savedReq     = currentRequest;
        HttpRequestResponse savedProxy   = currentProxyItem;
        List<Map<String,String>> savedTurns = new ArrayList<>(conversationTurns);
        List<EndpointSlot>       savedSlots = new ArrayList<>(slots);

        // Apply new palette to the shared static fields and rebuild
        Theme.save(t);
        applyTheme(t);
        setBackground(BG);
        removeAll();
        buildUI();

        // Restore loaded request
        if (savedReq != null) {
            currentRequest   = savedReq;
            currentProxyItem = savedProxy;
            methodLabel.setText(savedMethod);
            methodLabel.setForeground(GREEN);
            urlField.setText(savedUrl);
            urlField.setForeground(GREEN);
            urlField.setEditable(true);
            sendBtn.setEnabled(true);
        }

        // Restore session, message, modifier
        if (savedSession != null) updateSession(savedSession);
        if (!savedMsg.isEmpty())  messageArea.setText(savedMsg);
        lastResponseText = savedLastRsp;
        if (savedMod != null)     toggleModifier(savedMod);

        // Restore multi-turn
        conversationTurns.clear();
        conversationTurns.addAll(savedTurns);
        if (wasMTOn) toggleMultiTurn();
        updateTurnLabel();

        // Restore endpoint slots
        slots.clear();
        slots.addAll(savedSlots);
        updateSlotLabel();

        // Refresh history with new colors
        historyPanel.refresh();

        revalidate();
        repaint();
    }

    // â"€â"€ Info / error display in response pane â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

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

    // â"€â"€ Field auto-detection â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

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

    private void updateMsgTokens() {
        int tokens = Math.max(0, messageArea.getText().length() / 4);
        msgTokenLabel.setText("~" + tokens + " tokens");
    }

    // â"€â"€ Swing helpers â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

    private static JButton leftTabButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Monospaced", Font.BOLD, Math.max(BASE_SIZE - 1, 10)));
        b.setForeground(MUTED);
        b.setBackground(SURFACE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

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

    // -- Agent enumerator -------------------------------------------------------

    private void openEnumerator() {
        // Strip path — enumerator needs scheme://host:port only
        String raw = urlField.getText().trim();
        String base = raw;
        try {
            java.net.URI u = new java.net.URI(raw);
            if (u.getScheme() != null && u.getHost() != null) {
                int p = u.getPort();
                base = u.getScheme() + "://" + u.getHost() + (p != -1 ? ":" + p : "");
            }
        } catch (Exception ignored) {}
        if (enumeratorDialog == null || !enumeratorDialog.isDisplayable()) {
            enumeratorDialog = new AgentEnumeratorDialog(this, api, base, this);
        } else {
            enumeratorDialog.setBaseUrl(base);
        }
        enumeratorDialog.setVisible(true);
        enumeratorDialog.toFront();
    }

    private void openHelp() {
        if (helpDialog == null || !helpDialog.isDisplayable()) {
            helpDialog = new HelpDialog(this);
        }
        helpDialog.setVisible(true);
        helpDialog.toFront();
    }

    void applyEndpoint(String url) {
        urlField.setText(url);
        urlField.setForeground(GREEN);
        showInfo("Endpoint set: " + url + "\n\nLoad a matching request via Burp Proxy to enable sending.");
    }

}
