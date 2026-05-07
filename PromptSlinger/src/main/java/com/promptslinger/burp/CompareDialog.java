package com.promptslinger.burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.promptslinger.burp.PSPanel.*;

public class CompareDialog extends JDialog {

    private final MontoyaApi         api;
    private final HistoryStore        store;
    private final List<EndpointSlot>  slots;
    private final String              message;
    private final String              modifier;

    private JTextPane[] responsePanes;
    private JLabel[]    statusLabels;

    public CompareDialog(Component parent, MontoyaApi api, HistoryStore store,
                         List<EndpointSlot> slots, String message, String modifier) {
        super(SwingUtilities.getWindowAncestor(parent), "Compare Endpoints",
              ModalityType.MODELESS);
        this.api      = api;
        this.store    = store;
        this.slots    = slots;
        this.message  = message;
        this.modifier = modifier;

        int w = Math.min(1600, Math.max(800, 440 * slots.size()));
        setSize(w, 620);
        setLocationRelativeTo(parent);
        buildUI();
        sendAll();
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);

        // Message preview bar
        String preview = message.replace("\n", " ");
        if (preview.length() > 120) preview = preview.substring(0, 120) + "...";
        JLabel msgLabel = new JLabel("  Sending: " + preview);
        msgLabel.setFont(new Font("Monospaced", Font.ITALIC, Math.max(BASE_SIZE - 2, 10)));
        msgLabel.setForeground(MUTED);
        msgLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        root.add(msgLabel, BorderLayout.NORTH);

        // Per-slot columns
        int n = slots.size();
        responsePanes = new JTextPane[n];
        statusLabels  = new JLabel[n];

        JPanel colsPanel = new JPanel(new GridLayout(1, n, 2, 0));
        colsPanel.setBackground(BG);

        for (int i = 0; i < n; i++) {
            EndpointSlot slot = slots.get(i);

            Color accentColor = MARK_BG[i % MARK_BG.length];

            JLabel header = new JLabel("  [" + (i + 1) + "]  " + slot.name
                    + "  " + slot.method);
            header.setFont(new Font("Monospaced", Font.BOLD, Math.max(BASE_SIZE - 1, 10)));
            header.setForeground(FG);
            header.setBackground(accentColor.darker().darker());
            header.setOpaque(true);
            header.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));

            JLabel urlLabel = new JLabel("  " + truncate(slot.url, 60));
            urlLabel.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 3, 9)));
            urlLabel.setForeground(MUTED);
            urlLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

            JLabel statusLbl = new JLabel("  Sending...");
            statusLbl.setFont(new Font("Monospaced", Font.ITALIC, Math.max(BASE_SIZE - 3, 9)));
            statusLbl.setForeground(ORANGE);
            statusLbl.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
            statusLabels[i] = statusLbl;

            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(BG);
            headerPanel.add(header,    BorderLayout.NORTH);
            headerPanel.add(urlLabel,  BorderLayout.CENTER);
            headerPanel.add(statusLbl, BorderLayout.SOUTH);

            responsePanes[i] = new JTextPane();
            responsePanes[i].setEditable(false);
            responsePanes[i].setBackground(SURFACE);
            responsePanes[i].setForeground(FG);
            responsePanes[i].setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 1, 11)));
            responsePanes[i].setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            JScrollPane scroll = new JScrollPane(responsePanes[i]);
            scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, SURFACE));

            JPanel col = new JPanel(new BorderLayout());
            col.setBackground(BG);
            if (i > 0) col.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, SURFACE));
            col.add(headerPanel, BorderLayout.NORTH);
            col.add(scroll,      BorderLayout.CENTER);

            colsPanel.add(col);
        }
        root.add(colsPanel, BorderLayout.CENTER);

        // Bottom bar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        bottomBar.setBackground(BG);
        JButton closeBtn = new JButton("Close");
        closeBtn.setBackground(SURFACE);
        closeBtn.setForeground(MUTED);
        closeBtn.setFont(new Font("Monospaced", Font.BOLD, BASE_SIZE - 1));
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.addActionListener(e -> dispose());
        bottomBar.add(closeBtn);
        root.add(bottomBar, BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ── Send all slots in parallel ─────────────────────────────────────────────

    private void sendAll() {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String finalMessage = ModifierUtil.applyModifier(message, modifier);

        for (int i = 0; i < slots.size(); i++) {
            final int idx  = i;
            EndpointSlot slot = slots.get(i);

            Thread t = new Thread(() -> {
                try {
                    String   body = slot.request.bodyToString();
                    JsonNode root = mapper.readTree(body);
                    if (!(root instanceof ObjectNode on))
                        throw new IllegalStateException("Request body is not a JSON object.");
                    PSPanel.injectAtPath(root, slot.fieldName, finalMessage);
                    if (slot.sessionId != null && on.has("session_id"))
                        on.put("session_id", slot.sessionId);

                    java.net.URI uri    = new java.net.URI(slot.url);
                    String       host   = uri.getHost();
                    int          port   = uri.getPort() == -1
                            ? (uri.getScheme().equalsIgnoreCase("https") ? 443 : 80)
                            : uri.getPort();
                    boolean      secure = uri.getScheme().equalsIgnoreCase("https");
                    String       path   = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
                    if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
                    String hostHeader = (port == 80 || port == 443) ? host : host + ":" + port;

                    HttpRequest modified = slot.request
                            .withService(HttpService.httpService(host, port, secure))
                            .withPath(path)
                            .withUpdatedHeader("Host", hostHeader)
                            .withBody(mapper.writeValueAsString(on));

                    long t0 = System.currentTimeMillis();
                    burp.api.montoya.http.message.HttpRequestResponse result =
                            api.http().sendRequest(modified);
                    long latencyMs = System.currentTimeMillis() - t0;

                    String rawResp = result.response() != null
                            ? result.response().bodyToString() : "(no response)";

                    String pretty;
                    String plainResp;
                    try {
                        JsonNode parsed = mapper.readTree(rawResp);
                        pretty    = mapper.writeValueAsString(parsed);
                        plainResp = (parsed instanceof ObjectNode op && op.has("response"))
                                ? op.get("response").asText() : pretty;
                    } catch (Exception ex) {
                        pretty    = rawResp;
                        plainResp = rawResp;
                    }

                    // SSE assembly
                    String sseText = SseParser.assemble(rawResp);
                    boolean isSse  = (sseText != null && !sseText.isBlank());
                    String display = isSse ? sseText : plainResp;

                    String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
                    HistoryEntry entry = new HistoryEntry(ts, slot.url, message,
                            pretty, slot.proxyItem, slot.request, slot.fieldName);
                    entry.latencyMs = latencyMs;
                    entry.note      = "[Compare:" + slot.name + "]";
                    String autoMark = KeywordAlerts.check(display);
                    if (autoMark != null) entry.mark = autoMark;
                    store.add(entry);

                    final String dc     = display;
                    final long   lat    = latencyMs;
                    final boolean wasSse = isSse;
                    final String  mark   = entry.mark;
                    SwingUtilities.invokeLater(() -> {
                        responsePanes[idx].setText(dc);
                        responsePanes[idx].setCaretPosition(0);
                        String statusText = "  " + lat + "ms"
                                + (wasSse ? "  [SSE assembled]" : "")
                                + (mark != null ? "  [" + mark + "]" : "");
                        statusLabels[idx].setText(statusText);
                        statusLabels[idx].setForeground(GREEN);
                    });

                } catch (Exception ex) {
                    final String err = ex.getMessage() != null
                            ? ex.getMessage() : ex.getClass().getSimpleName();
                    SwingUtilities.invokeLater(() -> {
                        responsePanes[idx].setText("ERROR: " + err);
                        statusLabels[idx].setText("  Error");
                        statusLabels[idx].setForeground(RED);
                    });
                }
            }, "compare-slot-" + idx);
            t.setDaemon(true);
            t.start();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
