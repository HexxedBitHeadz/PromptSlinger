package com.promptslinger.burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.promptslinger.burp.PSPanel.*;

public class AgentEnumeratorDialog extends JDialog {

    // ── Probe path list ───────────────────────────────────────────────────────

    private static final String[] PROBE_PATHS = {
        // Root — header fingerprinting probe (mirrors curl -I /)
        "/",
        // A2A Agent Card (Google A2A protocol)
        "/.well-known/agent.json",
        // OpenAI Plugin Manifest
        "/.well-known/ai-plugin.json",
        // Model Context Protocol
        "/.well-known/mcp.json",
        // LLM configuration hints
        "/llms.txt",
        "/.well-known/llms.txt",
        // OpenAPI / Swagger specifications
        "/openapi.json",
        "/openapi.yaml",
        "/api/openapi.json",
        "/v1/openapi.json",
        "/swagger.json",
        "/swagger.yaml",
        "/api-docs",
        "/api/docs",
        "/api/schema",
        "/api/swagger",
        // OpenAI-compatible models list
        "/v1/models",
        "/api/v1/models",
        "/api/models",
        "/models",
        // Assistants API
        "/v1/assistants",
        "/api/v1/assistants",
        "/assistants",
        // Agents endpoints
        "/v1/agents",
        "/api/agents",
        "/agents",
        // MCP tool and resource endpoints
        "/mcp",
        "/mcp/tools",
        "/mcp/resources",
        "/api/mcp/tools",
        "/api/mcp/resources",
        // Capability and manifest endpoints
        "/api/info",
        "/api/capabilities",
        "/capabilities",
        "/api/manifest",
        "/manifest.json",
        "/chat/capabilities",
        // Health / version (reveals tech stack)
        "/health",
        "/api/health",
        "/version",
        "/api/version",
        "/api/status",
        // Configuration endpoints
        "/api/config",
        "/config",
        "/api/configuration",
        "/configuration",
        "/api/settings",
        "/settings",
        "/api/debug",
        "/debug",
        // WebFinger and discovery
        "/.well-known/webfinger",
        "/.well-known/openid-configuration",
    };

    // Built-in wordlist for fuzz mode (no leading slash, no prefix)
    private static final String[] BUILTIN_ENDPOINTS = {
        "account", "accounts", "admin", "agent", "agent.json", "assistant",
        "auth", "chat", "completions", "config", "configuration", "context",
        "data", "debug", "docs", "embeddings", "embed", "files", "generate",
        "health", "info", "infer", "inference", "keys", "limits", "login",
        "logout", "me", "metrics", "model", "models", "openapi.json",
        "ping", "profile", "prompts", "query", "refresh", "register",
        "route", "schema", "search", "settings", "status", "system",
        "token", "tokens", "tools", "upload", "user", "users", "version",
        "workflow", "whoami",
        ".well-known/agent.json", ".well-known/ai-plugin.json",
        ".well-known/llms.txt", ".well-known/openid-configuration",
    };

    // Prefixes used at depth 2 and 3
    private static final String[] FUZZ_PREFIXES = {
        "api", "api/v1", "api/v2", "api/v3",
        "v1", "v2", "v3", "a2a", "rest", "rest/v1",
        "service", "internal", "public",
    };

    // Common A2A/agent ports to sweep in addition to the base port
    private static final int[] EXTRA_PORTS = {8000, 8001, 8002, 8003, 8080};

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final String[] COLS = {"Path", "Status", "Type", "Summary"};

    // ── State ─────────────────────────────────────────────────────────────────

    private final MontoyaApi  api;
    private final PSPanel     owner;

    private final JTextField       urlField     = new JTextField();
    private final JCheckBox        showAllCheck = new JCheckBox("Show 404s");
    private final JProgressBar     progressBar  = new JProgressBar();
    private final JLabel           statusLabel  = new JLabel("Ready");
    private final JButton          scanBtn      = btn("Scan", GREEN);
    private final JButton          stopBtn      = btn("Stop", RED);
    private final DefaultTableModel tableModel  = buildModel();
    private final JTable           table        = buildTable();
    private final JTextPane        detailPane   = new JTextPane();

    // Wordlist fuzz controls
    private final JCheckBox    fuzzCheck     = new JCheckBox("Fuzz paths");
    private final JComboBox<String> depthBox = new JComboBox<>(new String[]{
        "Depth 1  —  /{word}",
        "Depth 2  —  /{prefix}/{word}",
        "Depth 3  —  /{prefix}/{word}/{word}  (slow)",
    });
    private final JLabel       wordlistLabel = new JLabel("  built-in wordlist");
    private List<String>       customWordlist = null;

    // allRows: {fullUrl, probePath, status(int or "ERR"), type, summary, body, headersAll, aiHeaders}
    private final List<Object[]>   allRows  = new ArrayList<>();
    private final AtomicBoolean    stopped  = new AtomicBoolean(false);
    private Thread                 worker;

    // ── Construction ──────────────────────────────────────────────────────────

    public AgentEnumeratorDialog(Component parent, MontoyaApi api,
                                 String initialUrl, PSPanel owner) {
        super(SwingUtilities.getWindowAncestor(parent), "AI Endpoint Enumerator",
              ModalityType.MODELESS);
        this.api   = api;
        this.owner = owner;
        setSize(1040, 640);
        setLocationRelativeTo(parent);
        buildUI(initialUrl);
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI(String initialUrl) {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);

        // ── Top: URL bar ────────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout(6, 0));
        topBar.setBackground(BG);
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));

        JLabel urlLbl = new JLabel("Base URL:");
        urlLbl.setForeground(MUTED);
        urlLbl.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        urlLbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

        urlField.setText(initialUrl);
        urlField.setBackground(ENTRY_BG);
        urlField.setForeground(FG);
        urlField.setCaretColor(FG);
        urlField.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        urlField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SURFACE, 1),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        urlField.addActionListener(e -> startScan());

        JPanel urlRow = new JPanel(new BorderLayout(4, 0));
        urlRow.setBackground(BG);
        urlRow.add(urlLbl,   BorderLayout.WEST);
        urlRow.add(urlField, BorderLayout.CENTER);
        topBar.add(urlRow, BorderLayout.CENTER);

        showAllCheck.setBackground(BG);
        showAllCheck.setForeground(MUTED);
        showAllCheck.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        showAllCheck.setFocusPainted(false);
        showAllCheck.addActionListener(e -> refreshTableFilter());

        stopBtn.setEnabled(false);
        scanBtn.addActionListener(e -> startScan());
        stopBtn.addActionListener(e -> stopScan());

        JPanel ctrlRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        ctrlRow.setBackground(BG);
        ctrlRow.add(showAllCheck);
        ctrlRow.add(Box.createHorizontalStrut(6));
        ctrlRow.add(scanBtn);
        ctrlRow.add(stopBtn);
        topBar.add(ctrlRow, BorderLayout.EAST);

        // ── Fuzz row ────────────────────────────────────────────────────────
        JPanel fuzzRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        fuzzRow.setBackground(BG);
        fuzzRow.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, SURFACE));

        fuzzCheck.setBackground(BG);
        fuzzCheck.setForeground(ACCENT);
        fuzzCheck.setFont(new Font("Monospaced", Font.BOLD, Math.max(BASE_SIZE - 2, 10)));
        fuzzCheck.setFocusPainted(false);

        JLabel depthLbl = new JLabel("Depth:");
        depthLbl.setForeground(MUTED);
        depthLbl.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));

        depthBox.setBackground(ENTRY_BG);
        depthBox.setForeground(FG);
        depthBox.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        depthBox.setEnabled(false);

        JButton browseBtn = btn("Load wordlist", MUTED);
        browseBtn.setEnabled(false);
        browseBtn.addActionListener(e -> browseWordlist());

        wordlistLabel.setFont(new Font("Monospaced", Font.ITALIC, Math.max(BASE_SIZE - 3, 9)));
        wordlistLabel.setForeground(MUTED);

        fuzzCheck.addActionListener(e -> {
            boolean on = fuzzCheck.isSelected();
            depthBox.setEnabled(on);
            browseBtn.setEnabled(on);
        });

        fuzzRow.add(fuzzCheck);
        fuzzRow.add(Box.createHorizontalStrut(6));
        fuzzRow.add(depthLbl);
        fuzzRow.add(depthBox);
        fuzzRow.add(Box.createHorizontalStrut(10));
        fuzzRow.add(browseBtn);
        fuzzRow.add(wordlistLabel);
        topBar.add(fuzzRow, BorderLayout.SOUTH);

        root.add(topBar, BorderLayout.NORTH);

        // ── Centre: results table + detail pane ─────────────────────────────
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, SURFACE));

        JLabel detailHdr = new JLabel("  Response Detail");
        detailHdr.setFont(new Font("Monospaced", Font.BOLD, Math.max(BASE_SIZE - 2, 10)));
        detailHdr.setForeground(MUTED);
        detailHdr.setBorder(BorderFactory.createEmptyBorder(6, 6, 4, 6));

        detailPane.setEditable(false);
        detailPane.setBackground(SURFACE);
        detailPane.setForeground(FG);
        detailPane.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        detailPane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel detailPanel = new JPanel(new BorderLayout());
        detailPanel.setBackground(BG);
        detailPanel.add(detailHdr,                    BorderLayout.NORTH);
        detailPanel.add(new JScrollPane(detailPane),  BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                tableScroll, detailPanel);
        split.setBackground(BG);
        split.setBorder(null);
        split.setDividerSize(4);
        split.setResizeWeight(0.75);
        split.setDividerLocation(780);
        root.add(split, BorderLayout.CENTER);

        // ── Bottom: progress + actions ───────────────────────────────────────
        JPanel bottomBar = new JPanel(new BorderLayout(8, 0));
        bottomBar.setBackground(BG);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));

        JPanel progressPanel = new JPanel(new BorderLayout(0, 2));
        progressPanel.setBackground(BG);
        progressBar.setBackground(SURFACE);
        progressBar.setForeground(GREEN);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 3, 9)));
        statusLabel.setFont(new Font("Monospaced", Font.ITALIC, Math.max(BASE_SIZE - 2, 10)));
        statusLabel.setForeground(MUTED);
        progressPanel.add(statusLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        bottomBar.add(progressPanel, BorderLayout.CENTER);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actionRow.setBackground(BG);
        JButton exportBtn = btn("Export Findings", GREEN);
        JButton maxBtn    = btn("⛶ Maximize",      MUTED);
        JButton closeBtn  = btn("Close",           MUTED);
        exportBtn.addActionListener(e -> exportFindings());
        maxBtn   .addActionListener(e -> toggleMaximize(maxBtn));
        closeBtn .addActionListener(e -> dispose());
        actionRow.add(exportBtn);
        actionRow.add(maxBtn);
        actionRow.add(closeBtn);
        bottomBar.add(actionRow, BorderLayout.EAST);
        root.add(bottomBar, BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ── Scan logic ────────────────────────────────────────────────────────────

    private void startScan() {
        String base = urlField.getText().trim();
        if (base.isEmpty()) { setStatus("Enter a base URL first."); return; }
        if (!base.startsWith("http://") && !base.startsWith("https://"))
            base = "https://" + base;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        allRows.clear();
        tableModel.setRowCount(0);
        detailPane.setText("");
        progressBar.setValue(0);
        stopped.set(false);
        scanBtn.setEnabled(false);
        stopBtn.setEnabled(true);

        final String baseUrl    = base;
        final List<String> scanPaths = buildScanPaths(); // capture on EDT before thread starts

        worker = new Thread(() -> {
            try {
                java.net.URI uri  = new java.net.URI(baseUrl);
                String  host     = uri.getHost();
                int     basePort = uri.getPort() == -1
                        ? (uri.getScheme().equalsIgnoreCase("https") ? 443 : 80)
                        : uri.getPort();
                boolean baseSec  = uri.getScheme().equalsIgnoreCase("https");

                // Build port list: base port first, then extra A2A ports (skip if same)
                java.util.List<Integer> ports = new java.util.ArrayList<>();
                ports.add(basePort);
                for (int p : EXTRA_PORTS) {
                    if (p != basePort) ports.add(p);
                }
                final int total = ports.size() * scanPaths.size();
                SwingUtilities.invokeLater(() -> {
                    progressBar.setMaximum(total);
                    if (total > 5000)
                        setStatus("Large scan — " + total + " requests across "
                                + ports.size() + " ports (this may take a while)");
                });

                AtomicInteger completed = new AtomicInteger(0);
                ExecutorService pool = Executors.newFixedThreadPool(20);

                for (int port : ports) {
                    boolean secure  = (port == basePort) ? baseSec : false;
                    String  scheme  = secure ? "https" : "http";
                    String  hostHdr = (port == 80 || port == 443) ? host : host + ":" + port;
                    HttpService service = HttpService.httpService(host, port, secure);

                    for (String probePath : scanPaths) {
                        if (stopped.get()) break;
                        final String fp = probePath;
                        final String fs = scheme, fh = hostHdr;
                        final HttpService fsvc = service;
                        final int fport = port;

                        pool.submit(() -> {
                            if (stopped.get()) { completed.incrementAndGet(); return; }
                            try {
                                String rawReq = "GET " + fp + " HTTP/1.1\r\n"
                                        + "Host: " + fh + "\r\n"
                                        + "Accept: application/json, text/plain, */*\r\n"
                                        + "User-Agent: PromptSlinger-Enumerator/1.0\r\n"
                                        + "Connection: close\r\n"
                                        + "\r\n";

                                HttpRequestResponse rr = api.http().sendRequest(
                                        HttpRequest.httpRequest(fsvc, rawReq));

                                int    status  = rr.response() != null ? rr.response().statusCode() : 0;
                                String body    = rr.response() != null ? rr.response().bodyToString() : "";
                                String ct      = rr.response() != null
                                        ? (rr.response().headerValue("Content-Type") != null
                                           ? rr.response().headerValue("Content-Type") : "") : "";
                                List<HttpHeader> hdrs = rr.response() != null
                                        ? rr.response().headers() : List.of();
                                String headersAll = formatHeaders(hdrs);
                                String aiHeaders  = extractAiHeaders(hdrs);

                                String type    = detectType(fp, body, ct);
                                String summary = extractSummary(fp, body);
                                if (!aiHeaders.isEmpty())
                                    summary = (summary.isEmpty() ? "" : summary + "  ")
                                            + "[" + aiHeaders.replace("\n", ", ") + "]";
                                String fullUrl = fs + "://" + fh + fp;

                                Object[] row = {fullUrl, fp, status, type, summary, body, headersAll, aiHeaders};
                                synchronized (allRows) { allRows.add(row); }

                                final int    st = status;
                                final String fu = fullUrl, fb = body, ty = type, su = summary;
                                SwingUtilities.invokeLater(() -> {
                                    progressBar.setValue(completed.incrementAndGet());
                                    setStatus("Scanning port " + fport + "  (" + completed.get() + "/" + total + ")");
                                    if (showAllCheck.isSelected() || (isInteresting(st) && !isBurpProxy(fb))) {
                                        tableModel.addRow(new Object[]{fu, st, ty, su});
                                        if (table.getRowCount() == 1) table.setRowSelectionInterval(0, 0);
                                    }
                                });
                            } catch (Exception ex) {
                                Object[] row = {fs + "://" + fh + fp, fp, 0, "Error",
                                        ex.getClass().getSimpleName(), "", "", ""};
                                synchronized (allRows) { allRows.add(row); }
                                SwingUtilities.invokeLater(() -> progressBar.setValue(completed.incrementAndGet()));
                            }
                        });
                    }
                }

                pool.shutdown();
                pool.awaitTermination(5, TimeUnit.MINUTES);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> setStatus("Error: " + ex.getMessage()));
            }

            SwingUtilities.invokeLater(() -> {
                scanBtn.setEnabled(true);
                stopBtn.setEnabled(false);
                long hits;
                synchronized (allRows) {
                    hits = allRows.stream().filter(r -> isInteresting(statusOf(r)) && !isBurpProxy((String) r[5])).count();
                }
                setStatus("Done — " + hits + " interesting endpoint"
                        + (hits == 1 ? "" : "s") + " found across all ports");
                progressBar.setValue(progressBar.getMaximum());
            });
        }, "promptslinger-enum");
        worker.setDaemon(true);
        worker.start();
    }

    private void stopScan() {
        stopped.set(true);
        if (worker != null) worker.interrupt();
        stopBtn.setEnabled(false);
        setStatus("Stopped.");
    }

    // ── Wordlist / path building ──────────────────────────────────────────────

    private List<String> buildScanPaths() {
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>(List.of(PROBE_PATHS));
        if (fuzzCheck.isSelected()) {
            List<String> words = customWordlist != null ? customWordlist : List.of(BUILTIN_ENDPOINTS);
            int depth = depthBox.getSelectedIndex() + 1;
            paths.addAll(buildFuzzPaths(words, depth));
        }
        return new ArrayList<>(paths);
    }

    private static List<String> buildFuzzPaths(List<String> words, int depth) {
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
        if (depth >= 1) {
            for (String w : words) paths.add("/" + w.replaceAll("^/+", ""));
        }
        if (depth >= 2) {
            for (String p : FUZZ_PREFIXES)
                for (String w : words)
                    paths.add("/" + p + "/" + w.replaceAll("^/+", ""));
        }
        if (depth >= 3) {
            for (String p : FUZZ_PREFIXES)
                for (String w1 : words)
                    for (String w2 : words)
                        paths.add("/" + p + "/" + w1.replaceAll("^/+", "")
                                + "/" + w2.replaceAll("^/+", ""));
        }
        return new ArrayList<>(paths);
    }

    private void browseWordlist() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Load Wordlist (one endpoint per line, no leading slash)");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File f = fc.getSelectedFile();
        try {
            customWordlist = java.nio.file.Files.readAllLines(f.toPath()).stream()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .collect(java.util.stream.Collectors.toList());
            wordlistLabel.setText("  " + f.getName() + " (" + customWordlist.size() + " words)");
            wordlistLabel.setForeground(GREEN);
        } catch (Exception ex) {
            wordlistLabel.setText("  Error: " + ex.getMessage());
            wordlistLabel.setForeground(RED);
            customWordlist = null;
        }
    }

    // ── Header fingerprinting helpers ─────────────────────────────────────────

    private static final String[] AI_HEADER_PREFIXES = {
        "x-ai-", "x-llm-", "x-rag-", "x-model", "x-inference",
        "x-embedding", "x-orchestrator", "x-backend", "x-engine"
    };
    private static final String[] AI_VALUE_KEYWORDS = {
        "ollama", "litellm", "localai", "vllm", "openai", "anthropic",
        "llama", "mistral", "deepseek", "huggingface", "langchain", "fastapi",
        "chromadb", "pinecone", "weaviate", "qdrant", "llamacpp", "koboldcpp"
    };

    private static String formatHeaders(List<HttpHeader> headers) {
        if (headers == null || headers.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (HttpHeader h : headers) sb.append(h.name()).append(": ").append(h.value()).append("\n");
        return sb.toString();
    }

    private static String extractAiHeaders(List<HttpHeader> headers) {
        if (headers == null || headers.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (HttpHeader h : headers) {
            String nameLow = h.name().toLowerCase();
            String val     = h.value();
            boolean flag   = false;
            for (String pfx : AI_HEADER_PREFIXES) if (nameLow.startsWith(pfx)) { flag = true; break; }
            if (!flag && (nameLow.equals("x-powered-by") || nameLow.equals("server") || nameLow.equals("via"))) {
                String valL = val.toLowerCase();
                for (String kw : AI_VALUE_KEYWORDS) if (valL.contains(kw)) { flag = true; break; }
            }
            if (flag) { if (sb.length() > 0) sb.append("\n"); sb.append(h.name()).append(": ").append(val); }
        }
        return sb.toString();
    }

    // ── Classification helpers ────────────────────────────────────────────────

    private static String detectType(String path, String body, String ct) {
        if (path.contains("agent.json"))             return "A2A Agent Card";
        if (path.contains("ai-plugin"))              return "OpenAI Plugin";
        if (path.contains("mcp"))                    return "MCP";
        if (path.contains("llms.txt"))               return "LLMs.txt";
        if (path.contains("openapi") || path.contains("swagger") || ct.contains("yaml"))
                                                     return "OpenAPI Spec";
        if (path.contains("openid-configuration"))   return "OIDC Discovery";
        if (path.contains("webfinger"))              return "WebFinger";
        if (path.contains("/models"))                return "Models API";
        if (path.contains("/assistants"))            return "Assistants API";
        if (path.contains("/agents"))                return "Agents API";
        if (path.contains("/tools"))                 return "Tool Manifest";
        if (path.contains("health") || path.contains("status") || path.contains("version"))
                                                     return "Health / Info";
        if (path.contains("capabilities") || path.contains("manifest"))
                                                     return "Capabilities";
        if (ct.contains("text/html"))                return "HTML Page";
        // Body-based fallbacks
        if (body.contains("\"tools\""))              return "Tool Manifest";
        if (body.contains("\"models\""))             return "Models API";
        if (body.contains("\"capabilities\""))       return "Capabilities";
        return "JSON Endpoint";
    }

    private static String extractSummary(String path, String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode root = MAPPER.readTree(body);
            // A2A agent card: has "name" + "capabilities"
            if (root.has("name") && root.has("capabilities"))
                return root.get("name").asText()
                        + (root.has("description")
                           ? "  —  " + trunc(root.get("description").asText(), 55) : "");
            // OpenAI plugin
            if (root.has("name_for_human"))
                return root.get("name_for_human").asText()
                        + (root.has("description_for_human")
                           ? "  —  " + trunc(root.get("description_for_human").asText(), 45) : "");
            // OpenAPI info block
            if (root.has("info")) {
                String title = root.get("info").path("title").asText("");
                String ver   = root.get("info").path("version").asText("");
                int    paths = root.has("paths") ? root.get("paths").size() : 0;
                return title + (ver.isEmpty() ? "" : " v" + ver)
                        + (paths > 0 ? "  (" + paths + " paths)" : "");
            }
            // Models list
            if (root.has("data") && root.get("data").isArray()) {
                int n = root.get("data").size();
                StringBuilder sb = new StringBuilder(n + " model" + (n == 1 ? "" : "s") + ": ");
                for (int i = 0; i < Math.min(n, 4); i++) {
                    JsonNode m = root.get("data").get(i);
                    String id = m.has("id") ? m.get("id").asText() : m.asText();
                    if (i > 0) sb.append(", ");
                    sb.append(trunc(id, 25));
                }
                if (n > 4) sb.append("...");
                return sb.toString();
            }
            // MCP tools
            if (root.has("tools") && root.get("tools").isArray()) {
                int n = root.get("tools").size();
                StringBuilder sb = new StringBuilder(n + " tool" + (n == 1 ? "" : "s") + ": ");
                for (int i = 0; i < Math.min(n, 4); i++) {
                    JsonNode t = root.get("tools").get(i);
                    String nm = t.has("name") ? t.get("name").asText() : "?";
                    if (i > 0) sb.append(", ");
                    sb.append(nm);
                }
                if (n > 4) sb.append("...");
                return sb.toString();
            }
            // OIDC discovery
            if (root.has("issuer"))
                return "Issuer: " + root.get("issuer").asText();
            // Generic named fields
            for (String key : new String[]{"name", "title", "message", "description"})
                if (root.has(key)) return trunc(root.get(key).asText(), 80);
        } catch (Exception ignored) {}
        // Plain-text (llms.txt etc.)
        return trunc(body.trim().replace("\n", " "), 80);
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    private void refreshTableFilter() {
        tableModel.setRowCount(0);
        synchronized (allRows) {
            for (Object[] r : allRows) {
                if (showAllCheck.isSelected() || (isInteresting(statusOf(r)) && !isBurpProxy((String) r[5]))) {
                    tableModel.addRow(new Object[]{r[0], r[2], r[3], r[4]});
                }
            }
        }
        if (table.getRowCount() > 0) table.setRowSelectionInterval(0, 0);
    }

    // ── Detail pane ───────────────────────────────────────────────────────────

    private void showDetail() {
        int sel = table.getSelectedRow();
        if (sel < 0) return;
        String fullUrl = (String) tableModel.getValueAt(sel, 0);
        synchronized (allRows) {
            for (Object[] r : allRows) {
                if (fullUrl.equals(r[0])) {
                    String body       = r.length > 5 ? (String) r[5] : "";
                    String headersAll = r.length > 6 ? (String) r[6] : "";
                    String aiHeaders  = r.length > 7 ? (String) r[7] : "";
                    renderDetail(aiHeaders, headersAll, body);
                    return;
                }
            }
        }
    }

    private void renderDetail(String aiHeaders, String headersAll, String body) {
        StyledDocument doc = detailPane.getStyledDocument();
        detailPane.setText("");

        // Style definitions
        SimpleAttributeSet aiSection = new SimpleAttributeSet();
        StyleConstants.setForeground(aiSection, ACCENT);
        StyleConstants.setBold(aiSection, true);

        SimpleAttributeSet aiKey = new SimpleAttributeSet();
        StyleConstants.setForeground(aiKey, MUTED);

        SimpleAttributeSet aiValue = new SimpleAttributeSet();
        StyleConstants.setForeground(aiValue, ORANGE);
        StyleConstants.setBold(aiValue, true);

        SimpleAttributeSet mutedSection = new SimpleAttributeSet();
        StyleConstants.setForeground(mutedSection, MUTED);

        SimpleAttributeSet normal = new SimpleAttributeSet();
        StyleConstants.setForeground(normal, FG);

        try {
            if (aiHeaders != null && !aiHeaders.isBlank()) {
                doc.insertString(doc.getLength(), "=== AI-RELEVANT HEADERS ===\n", aiSection);
                for (String line : aiHeaders.split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        doc.insertString(doc.getLength(), line.substring(0, colon + 1), aiKey);
                        doc.insertString(doc.getLength(), line.substring(colon + 1) + "\n", aiValue);
                    } else {
                        doc.insertString(doc.getLength(), line + "\n", normal);
                    }
                }
                doc.insertString(doc.getLength(), "\n", normal);
            }
            if (headersAll != null && !headersAll.isBlank()) {
                doc.insertString(doc.getLength(), "=== RESPONSE HEADERS ===\n", mutedSection);
                doc.insertString(doc.getLength(), headersAll + "\n", normal);
            }
            if (body != null && !body.isBlank()) {
                doc.insertString(doc.getLength(), "=== RESPONSE BODY ===\n", mutedSection);
                String pretty = body;
                try { pretty = MAPPER.writeValueAsString(MAPPER.readTree(body)); }
                catch (Exception ignored) {}
                doc.insertString(doc.getLength(), pretty, normal);
            }
            if (doc.getLength() == 0)
                doc.insertString(0, "(empty response)", mutedSection);
        } catch (BadLocationException ignored) {}

        detailPane.setCaretPosition(0);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void applySelectedUrl() {
        int sel = table.getSelectedRow();
        if (sel < 0) { setStatus("Select a row first."); return; }
        String fullUrl = (String) tableModel.getValueAt(sel, 0);
        synchronized (allRows) {
            for (Object[] r : allRows) {
                if (fullUrl.equals(r[0])) {
                    owner.applyEndpoint((String) r[0]);
                    setStatus("URL applied in PromptSlinger: " + r[0]);
                    return;
                }
            }
        }
    }

    private void copySelectedUrl() {
        int sel = table.getSelectedRow();
        if (sel < 0) { setStatus("Select a row first."); return; }
        String fullUrl = (String) tableModel.getValueAt(sel, 0);
        synchronized (allRows) {
            for (Object[] r : allRows) {
                if (fullUrl.equals(r[0])) {
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                           .setContents(new StringSelection((String) r[0]), null);
                    setStatus("Copied: " + r[0]);
                    return;
                }
            }
        }
    }

    private void exportFindings() {
        List<Object[]> hits = new ArrayList<>();
        synchronized (allRows) {
            for (Object[] r : allRows)
                if (isInteresting(statusOf(r)) && !isBurpProxy((String) r[5])) hits.add(r);
        }
        if (hits.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No interesting findings to export yet.",
                    "Nothing to Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save Enumeration Report");
        fc.setSelectedFile(new File("agent_enum_report.md"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Markdown files", "md"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File out = fc.getSelectedFile();
        if (!out.getName().endsWith(".md")) out = new File(out.getPath() + ".md");

        try {
            String ts = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            StringBuilder sb = new StringBuilder();
            sb.append("# PromptSlinger — Agent Enumeration Report\n");
            sb.append("Generated: ").append(ts).append("\n");
            sb.append("Target: ").append(urlField.getText().trim()).append("\n\n---\n\n");
            sb.append("## Interesting Endpoints\n\n");
            sb.append("| Path | Status | Type | Summary |\n");
            sb.append("|---|---|---|---|\n");
            for (Object[] r : hits)
                sb.append("| ").append(r[1]).append(" | ")
                  .append(r[2]).append(" | ")
                  .append(r[3]).append(" | ")
                  .append(r[4]).append(" |\n");
            sb.append("\n---\n\n## Response Bodies\n\n");
            for (Object[] r : hits) {
                sb.append("### ").append(r[1]).append("  (HTTP ").append(r[2]).append(")\n\n");
                String headersAll = r.length > 6 ? (String) r[6] : "";
                String aiHeaders  = r.length > 7 ? (String) r[7] : "";
                if (aiHeaders != null && !aiHeaders.isBlank()) {
                    sb.append("**AI-Relevant Headers:**\n```\n")
                      .append(aiHeaders.trim()).append("\n```\n\n");
                }
                if (headersAll != null && !headersAll.isBlank()) {
                    sb.append("**Response Headers:**\n```\n")
                      .append(headersAll.trim()).append("\n```\n\n");
                }
                String body = (String) r[5];
                if (body != null && !body.isBlank()) {
                    try { body = MAPPER.writeValueAsString(MAPPER.readTree(body)); }
                    catch (Exception ignored) {}
                    sb.append("**Body:**\n```\n").append(body.trim()).append("\n```\n\n");
                }
            }
            Files.writeString(out.toPath(), sb.toString());
            JOptionPane.showMessageDialog(this,
                    "Exported " + hits.size() + " finding"
                    + (hits.size() == 1 ? "" : "s") + " to:\n" + out.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Table construction ────────────────────────────────────────────────────

    private DefaultTableModel buildModel() {
        return new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return c == 1 ? Integer.class : String.class;
            }
        };
    }

    private JTable buildTable() {
        JTable t = new JTable(tableModel);
        t.setBackground(SURFACE);
        t.setForeground(FG);
        t.setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
        t.setSelectionBackground(ACCENT);
        t.setSelectionForeground(BG);
        t.setRowHeight(BASE_SIZE + 8);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setDefaultRenderer(Object.class, new StatusCellRenderer());
        t.getTableHeader().setBackground(BG);
        t.getTableHeader().setForeground(MUTED);
        t.getTableHeader().setFont(new Font("Monospaced", Font.BOLD,
                Math.max(BASE_SIZE - 2, 10)));
        t.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDetail();
        });
        // Column widths: Path | Status | Type | Summary
        int[] w = {160, 56, 130, 430};
        for (int i = 0; i < w.length; i++)
            t.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        // Click column headers to sort
        javax.swing.table.TableRowSorter<DefaultTableModel> sorter =
                new javax.swing.table.TableRowSorter<>(tableModel);
        t.setRowSorter(sorter);
        return t;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isInteresting(int status) {
        return status > 0 && status != 404 && status != 400 && status != 405;
    }

    private static boolean isBurpProxy(String body) {
        return body != null && body.contains("Burp Suite Community Edition");
    }

    private static int statusOf(Object[] row) {
        if (row[2] instanceof Integer) return (Integer) row[2];
        try { return Integer.parseInt(row[2].toString()); } catch (Exception e) { return 0; }
    }

    private static String trunc(String s, int max) {
        if (s == null || s.isEmpty()) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private void setStatus(String msg) { statusLabel.setText(msg); }

    private java.awt.Rectangle savedBounds = null;

    private void toggleMaximize(JButton btn) {
        if (savedBounds == null) {
            savedBounds = getBounds();
            java.awt.Rectangle screen =
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                                            .getMaximumWindowBounds();
            setBounds(screen);
            btn.setText("⛶ Restore");
        } else {
            setBounds(savedBounds);
            savedBounds = null;
            btn.setText("⛶ Maximize");
        }
    }

    void setBaseUrl(String url) {
        if (url != null && !url.isBlank())
            SwingUtilities.invokeLater(() -> urlField.setText(url));
    }

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

    // ── Row colour renderer ───────────────────────────────────────────────────

    private class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            // For the URL column, show only the path — host is already visible in the Base URL bar
            if (col == 0 && value != null) {
                String url = value.toString();
                try {
                    String path = new java.net.URI(url).getRawPath();
                    String query = new java.net.URI(url).getRawQuery();
                    value = (path == null || path.isEmpty() ? "/" : path)
                            + (query != null ? "?" + query : "");
                } catch (Exception ignored) {}
            }
            super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
            setFont(new Font("Monospaced", Font.PLAIN, Math.max(BASE_SIZE - 2, 10)));
            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            if (!isSelected) {
                Object statusObj = tableModel.getRowCount() > row
                        ? tableModel.getValueAt(row, 1) : null;
                int status = statusObj != null ? statusOf(new Object[]{"", "", statusObj}) : 0;
                // Only colour the Status column (col 1); rest of row is normal
                if (col == 1) {
                    if (status >= 200 && status < 300) {
                        setBackground(SURFACE); setForeground(GREEN);
                    } else if (status == 401 || status == 403) {
                        setBackground(SURFACE); setForeground(ORANGE);
                    } else if (status >= 500) {
                        setBackground(SURFACE); setForeground(RED);
                    } else {
                        setBackground(SURFACE); setForeground(MUTED);
                    }
                } else {
                    setBackground(isSelected ? ACCENT : SURFACE);
                    setForeground(isSelected ? BG : FG);
                }
            }
            return this;
        }
    }
}
