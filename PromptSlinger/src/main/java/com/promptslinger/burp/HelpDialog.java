package com.promptslinger.burp;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

import static com.promptslinger.burp.PSPanel.*;

public class HelpDialog extends JDialog {

    private static final String[][] SECTIONS = {
        {"Overview",             "overview"},
        {"Quick Start",          "quickstart"},
        {"AI Enumerator",        "enumerator"},
        {"Sending Messages",     "sending"},
        {"Output Modifiers",     "modifiers"},
        {"Saved Probes",         "probes"},
        {"Payload Library",      "payloads"},
        {"Batch Send",           "fuzz"},
        {"Compare Endpoints",    "compare"},
        {"Multi-turn",           "multiturn"},
        {"History",              "history"},
        {"Decode Window",        "decode"},
        {"Themes & Font",        "themes"},
        {"Keyboard Shortcuts",   "shortcuts"},
    };

    private final JEditorPane contentPane = new JEditorPane();

    public HelpDialog(Component parent) {
        super(SwingUtilities.getWindowAncestor(parent), "PromptSlinger Help",
              ModalityType.MODELESS);
        setSize(1000, 680);
        setLocationRelativeTo(parent);
        buildUI();
        showSection("overview");
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(SURFACE);
        header.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        JLabel title = new JLabel("PromptSlinger  —  Help & Reference");
        title.setFont(new Font("Monospaced", Font.BOLD, BASE_SIZE + 2));
        title.setForeground(ACCENT);
        JLabel sub = new JLabel("  AI/Agent Security Testing Extension for Burp Suite  |  By Hexxed BitHeadz");
        sub.setFont(new Font("Monospaced", Font.ITALIC, Math.max(BASE_SIZE - 2, 10)));
        sub.setForeground(MUTED);
        header.add(title, BorderLayout.WEST);
        header.add(sub, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Left nav
        DefaultListModel<String> navModel = new DefaultListModel<>();
        for (String[] s : SECTIONS) navModel.addElement(s[0]);
        JList<String> navList = new JList<>(navModel);
        navList.setBackground(SURFACE);
        navList.setForeground(FG);
        navList.setFont(new Font("Monospaced", Font.PLAIN, BASE_SIZE - 1));
        navList.setSelectionBackground(ACCENT);
        navList.setSelectionForeground(BG);
        navList.setFixedCellHeight(BASE_SIZE + 12);
        navList.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        navList.setSelectedIndex(0);
        navList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = navList.getSelectedIndex();
                if (idx >= 0) showSection(SECTIONS[idx][1]);
            }
        });

        JScrollPane navScroll = new JScrollPane(navList);
        navScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BG));
        navScroll.setPreferredSize(new Dimension(190, 0));

        // Content pane
        contentPane.setEditable(false);
        contentPane.setContentType("text/html");
        contentPane.setBackground(BG);
        contentPane.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        contentPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String ref = e.getDescription();
                for (int i = 0; i < SECTIONS.length; i++) {
                    if (SECTIONS[i][1].equals(ref)) {
                        navList.setSelectedIndex(i);
                        break;
                    }
                }
            }
        });

        JScrollPane contentScroll = new JScrollPane(contentPane);
        contentScroll.setBorder(null);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navScroll, contentScroll);
        split.setBorder(null);
        split.setDividerSize(4);
        split.setDividerLocation(190);
        split.setBackground(BG);
        root.add(split, BorderLayout.CENTER);

        // Bottom bar
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        bottom.setBackground(BG);
        JButton closeBtn = new JButton("Close");
        closeBtn.setBackground(SURFACE);
        closeBtn.setForeground(MUTED);
        closeBtn.setFont(new Font("Monospaced", Font.BOLD, BASE_SIZE - 1));
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.addActionListener(e -> dispose());
        bottom.add(closeBtn);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void showSection(String key) {
        contentPane.setText(buildHtml(key));
        contentPane.setCaretPosition(0);
    }

    // ── HTML builder ──────────────────────────────────────────────────────────

    private static String buildHtml(String key) {
        String bg     = toHex(BG);
        String fg     = toHex(FG);
        String accent = toHex(ACCENT);
        String green  = toHex(GREEN);
        String muted  = toHex(MUTED);
        String surf   = toHex(SURFACE);
        String orange = toHex(ORANGE);
        String pink   = toHex(PINK);

        String css = "<style>"
            + "body{background:" + bg + ";color:" + fg + ";font-family:monospace;font-size:13px;margin:0;padding:0}"
            + "h1{color:" + accent + ";font-size:17px;margin:0 0 6px 0;padding-bottom:6px;border-bottom:1px solid " + surf + "}"
            + "h2{color:" + accent + ";font-size:14px;margin:18px 0 6px 0}"
            + "h3{color:" + green + ";font-size:13px;margin:12px 0 4px 0}"
            + "p{margin:4px 0 10px 0;line-height:1.6}"
            + "ul,ol{margin:4px 0 10px 0;padding-left:22px;line-height:1.7}"
            + "li{margin-bottom:2px}"
            + "code,kbd{background:" + surf + ";color:" + green + ";padding:1px 5px;border-radius:3px;font-family:monospace}"
            + "kbd{color:" + orange + "}"
            + "table{border-collapse:collapse;width:100%;margin:8px 0}"
            + "th{background:" + surf + ";color:" + muted + ";text-align:left;padding:5px 8px;font-size:11px}"
            + "td{padding:5px 8px;border-bottom:1px solid " + surf + ";vertical-align:top}"
            + ".tag{background:" + surf + ";color:" + pink + ";padding:1px 6px;border-radius:10px;font-size:11px}"
            + ".warn{color:" + orange + "}"
            + ".good{color:" + green + "}"
            + ".muted{color:" + muted + ";font-size:11px}"
            + "a{color:" + accent + ";text-decoration:none}"
            + "hr{border:none;border-top:1px solid " + surf + ";margin:16px 0}"
            + "</style>";

        return "<html><head>" + css + "</head><body>" + body(key) + "</body></html>";
    }

    private static String body(String key) {
        switch (key) {

        case "overview": return
            "<h1>PromptSlinger</h1>"
            + "<p>PromptSlinger is a Burp Suite extension for testing AI and LLM-powered endpoints. "
            + "It lets you craft prompt injection attacks, fuzz chatbot APIs, enumerate agent infrastructure, "
            + "compare multiple endpoints side-by-side, and track findings — all without leaving Burp.</p>"
            + "<h2>Feature Map</h2>"
            + "<table><tr><th>Feature</th><th>What it does</th></tr>"
            + "<tr><td><span class='tag'>Enumerate</span></td><td>Probes 50+ paths across 5 common AI/agent ports, fingerprints AI stack from response headers, and supports wordlist fuzzing with depth control</td></tr>"
            + "<tr><td><span class='tag'>Send</span></td><td>Injects your message into the loaded request's target field and fires it through Burp's HTTP engine</td></tr>"
            + "<tr><td><span class='tag'>Batch</span></td><td>Batch-sends a list of payloads, records responses, auto-marks keyword hits</td></tr>"
            + "<tr><td><span class='tag'>Compare</span></td><td>Sends the same message to multiple saved endpoint slots simultaneously, side-by-side</td></tr>"
            + "<tr><td><span class='tag'>Multi-turn</span></td><td>Maintains conversation history across sends, injecting a messages[] array for context-aware testing</td></tr>"
            + "<tr><td><span class='tag'>Payloads</span></td><td>Built-in library of prompt injection, jailbreak, and recon payloads organized by category</td></tr>"
            + "<tr><td><span class='tag'>Probes</span></td><td>Save and recall your own favourite prompt templates</td></tr>"
            + "<tr><td><span class='tag'>History</span></td><td>Full request/response log with search, mark, notes, and replay</td></tr>"
            + "<tr><td><span class='tag'>Decode</span></td><td>Post-process the last response with decoding and transformation utilities</td></tr>"
            + "</table>"
            + "<h2>Recommended Workflow</h2>"
            + "<ol>"
            + "<li>Load a target request from Burp Proxy (<b>right-click → Send to PromptSlinger</b>)</li>"
            + "<li>Click <b>Enumerate</b> to map the agent infrastructure on the target host</li>"
            + "<li>Set the message field name (usually auto-detected) and craft your first prompt</li>"
            + "<li>Use the <b>Payloads</b> tab for injection templates, or type your own</li>"
            + "<li>Hit <b>Send</b> (or <kbd>Ctrl+Enter</kbd>), review the response</li>"
            + "<li>Use <b>Batch</b> for automated payload lists or <b>Compare</b> to test multiple endpoints</li>"
            + "<li>Review findings in <b>History</b>, add marks and notes, export the report</li>"
            + "</ol>";

        case "quickstart": return
            "<h1>Quick Start</h1>"
            + "<h2>Step 1 — Load a Request</h2>"
            + "<p>In Burp's Proxy, Repeater, or Target tree, right-click any request to an AI/chatbot API "
            + "and choose <b>Send to PromptSlinger</b>. The URL and method appear at the top of the panel in green.</p>"
            + "<h2>Step 2 — Enumerate the Target</h2>"
            + "<p>Click <b>Enumerate</b>. The AI Endpoint Enumerator opens pre-populated with your target's host "
            + "and automatically scans ports 8000, 8001, 8002, 8003, and 8080 alongside the base port. "
            + "Look for agent cards, config endpoints, and AI-relevant response headers to understand "
            + "the stack before you start injecting.</p>"
            + "<h2>Step 3 — Set the Message Field</h2>"
            + "<p>PromptSlinger auto-detects the JSON field that carries the user message "
            + "(<code>message</code>, <code>prompt</code>, <code>query</code>, etc.). "
            + "If it guesses wrong, type the correct field name in the <b>Message field</b> box or click <b>Auto-detect</b>.</p>"
            + "<h2>Step 4 — Send Your First Prompt</h2>"
            + "<p>Type a message in the <b>Message</b> box and press <kbd>Ctrl+Enter</kbd> or click <b>Send</b>. "
            + "The response appears in the <b>Response</b> pane with syntax highlighting. "
            + "Latency and token estimates are shown top-right.</p>"
            + "<h2>Step 5 — Explore Further</h2>"
            + "<p>Pick a payload from the <b>Payloads</b> tab on the left, double-click to load it, and send. "
            + "Use <b>Batch</b> for automated lists. Use <b>Multi-turn</b> to maintain conversation context across sends. "
            + "Save interesting endpoints with <b>Save Slot</b> then compare them with <b>Compare</b>.</p>";

        case "enumerator": return
            "<h1>AI Endpoint Enumerator</h1>"
            + "<p>Reconnaissance tool for mapping AI/agent infrastructure. Click <b>Enumerate</b> — "
            + "the dialog opens pre-filled with your loaded target's host and port. "
            + "Use it to understand what's running <i>before</i> you start injecting. "
            + "The main PromptSlinger panel handles the actual prompt testing via requests loaded from Burp Proxy.</p>"
            + "<h2>Built-in probe paths</h2>"
            + "<table><tr><th>Type</th><th>Paths probed</th></tr>"
            + "<tr><td>Root / Header fingerprint</td><td><code>/</code> — always probed first for AI-revealing headers</td></tr>"
            + "<tr><td>A2A Agent Cards</td><td><code>/.well-known/agent.json</code></td></tr>"
            + "<tr><td>OpenAI Plugin</td><td><code>/.well-known/ai-plugin.json</code></td></tr>"
            + "<tr><td>MCP</td><td><code>/.well-known/mcp.json</code>, <code>/mcp</code>, <code>/mcp/tools</code></td></tr>"
            + "<tr><td>OpenAPI / Swagger</td><td><code>/openapi.json</code>, <code>/swagger.json</code>, <code>/api-docs</code>…</td></tr>"
            + "<tr><td>Models / Assistants</td><td><code>/v1/models</code>, <code>/v1/assistants</code>, <code>/agents</code>…</td></tr>"
            + "<tr><td>Health / Info</td><td><code>/health</code>, <code>/api/health</code>, <code>/version</code>…</td></tr>"
            + "<tr><td>Config / Settings</td><td><code>/api/config</code>, <code>/config</code>, <code>/api/settings</code>…</td></tr>"
            + "</table>"
            + "<h2>HTTP header fingerprinting</h2>"
            + "<p>Every response is inspected for AI-revealing headers. Headers matching known patterns "
            + "(<code>X-AI-Backend</code>, <code>X-RAG-Provider</code>, <code>X-LLM-Provider</code>, "
            + "<code>X-Model</code>, and AI-related values in <code>Server</code> / <code>X-Powered-By</code>) "
            + "are extracted and shown at the top of the Response Detail pane in <b>orange</b>. "
            + "Detected headers also appear inline in the Summary column.</p>"
            + "<h2>Wordlist fuzz mode</h2>"
            + "<p>Tick <b>Fuzz paths</b> to generate additional paths beyond the built-in probe list.</p>"
            + "<table><tr><th>Depth</th><th>Pattern</th><th>Example</th></tr>"
            + "<tr><td>1</td><td><code>/{word}</code></td><td><code>/config</code></td></tr>"
            + "<tr><td>2</td><td><code>/{prefix}/{word}</code></td><td><code>/api/v1/config</code></td></tr>"
            + "<tr><td>3</td><td><code>/{prefix}/{word}/{word}</code></td><td><code>/api/v1/admin/config</code> — slow</td></tr>"
            + "</table>"
            + "<p>A built-in wordlist of common API endpoint names is used by default. "
            + "Click <b>Load wordlist</b> to supply your own (one word per line, no leading slash, <code>#</code> lines are comments). "
            + "A large scan warning appears in the status bar if the total request count exceeds 5,000.</p>"
            + "<h2>Multi-port sweep</h2>"
            + "<p>In addition to the base URL's port, the scanner automatically probes ports "
            + "<code>8000</code>, <code>8001</code>, <code>8002</code>, <code>8003</code>, and <code>8080</code> — "
            + "common ports for A2A agent frameworks. The Path column shows only the path — the base host is already visible in the URL bar.</p>"
            + "<h2>Result table</h2>"
            + "<p>Click any column header to sort. Click a row to see headers and response body on the right. "
            + "404 / 400 / 405 responses are hidden by default — tick <b>Show 404s</b> to reveal them.</p>"
            + "<h2>Actions</h2>"
            + "<ul>"
            + "<li><b>Export Findings</b> — saves a markdown report with all interesting endpoints, headers, and response bodies</li>"
            + "<li><b>Maximize / Restore</b> — expands the window to fill the screen</li>"
            + "</ul>";

        case "sending": return
            "<h1>Sending Messages</h1>"
            + "<h2>Loading a request</h2>"
            + "<p>Right-click any request in Burp → <b>Send to PromptSlinger</b>. The request must have a JSON body "
            + "with a string field that carries the user message.</p>"
            + "<h2>Message field detection</h2>"
            + "<p>PromptSlinger checks for common field names: <code>message</code>, <code>prompt</code>, <code>query</code>, "
            + "<code>input</code>, <code>text</code>, <code>content</code>, <code>msg</code>, <code>user_input</code>. "
            + "If none match, it picks the first string-valued field. Click <b>Auto-detect</b> to re-run detection, "
            + "or type the correct field name manually.</p>"
            + "<h2>Session ID tracking</h2>"
            + "<p>If the response contains a <code>session_id</code> field, PromptSlinger captures it automatically "
            + "and replays it on subsequent requests to maintain session continuity. Click <b>✕ clear session</b> to reset.</p>"
            + "<h2>Sending</h2>"
            + "<p>Press <kbd>Ctrl+Enter</kbd> or click <b>Send</b>. While in flight, the button turns red and shows "
            + "<b>✕ Cancel</b> — click it to interrupt. Latency (ms) and estimated token count appear after the response.</p>"
            + "<h2>SSE / streaming responses</h2>"
            + "<p>If the response body contains multiple <code>data:</code> lines (Server-Sent Events), "
            + "PromptSlinger automatically assembles the streamed chunks into a single readable response. "
            + "OpenAI, Anthropic, and generic streaming formats are supported.</p>";

        case "modifiers": return
            "<h1>Output Modifiers</h1>"
            + "<p>Modifiers append a suffix instruction to your message before sending, asking the model to respond in a specific format. "
            + "Only one modifier can be active at a time. Click the active checkbox again to deactivate it.</p>"
            + "<table><tr><th>Modifier</th><th>Effect</th></tr>"
            + "<tr><td><code>Spell out</code></td><td>Asks the model to spell each word character by character (evades keyword filters)</td></tr>"
            + "<tr><td><code>ROT13</code></td><td>Asks the model to respond in ROT13 encoding</td></tr>"
            + "<tr><td><code>Reverse text</code></td><td>Asks the model to respond with each word reversed</td></tr>"
            + "<tr><td><code>Spanish</code></td><td>Asks the model to respond in Spanish</td></tr>"
            + "<tr><td><code>Base64</code></td><td>Asks the model to encode its response in Base64</td></tr>"
            + "<tr><td><code>Hex</code></td><td>Asks the model to encode its response in hex</td></tr>"
            + "</table>"
            + "<p class='muted'>Use the Decode window to decode modifier-encoded responses.</p>";

        case "probes": return
            "<h1>Saved Probes</h1>"
            + "<p>Probes are reusable message templates you save for quick access. "
            + "They differ from Payloads in that they are personal saves — your own crafted prompts you want to reuse.</p>"
            + "<h2>Saving a probe</h2>"
            + "<p>Type your message, then click <b>Probes → Save current message as probe...</b>. Give it a name.</p>"
            + "<h2>Loading a probe</h2>"
            + "<p>Click <b>Probes</b> and select a saved probe from the menu. It loads directly into the message box.</p>"
            + "<h2>Deleting a probe</h2>"
            + "<p>Open the Probes menu, then <b>right-click</b> any probe to delete it.</p>"
            + "<p class='muted'>Probes are saved to <code>~/.promptslinger/</code> and persist across sessions.</p>";

        case "payloads": return
            "<h1>Payload Library</h1>"
            + "<p>The Payloads tab on the left side panel contains a built-in library of prompt injection, "
            + "jailbreak, recon, and encoding test payloads organized by category.</p>"
            + "<h2>Using payloads</h2>"
            + "<ol>"
            + "<li>Click the <b>Payloads</b> tab in the left panel</li>"
            + "<li>Select a category from the dropdown</li>"
            + "<li>Click a payload in the list to preview it</li>"
            + "<li><b>Double-click</b> the payload (or press <kbd>Enter</kbd>) to load it into the message box</li>"
            + "<li>Edit in the preview area before loading if needed, then click <b>Load into Message</b></li>"
            + "</ol>"
            + "<h2>Adding custom payloads</h2>"
            + "<p>Click the <b>+</b> button next to the category dropdown. Enter a name and payload text. "
            + "Custom payloads are saved under a <b>Custom</b> category and persist across sessions.</p>";

        case "fuzz": return
            "<h1>Batch Send</h1>"
            + "<p>Batch Send sends a list of payloads to the loaded endpoint one by one, recording each response. "
            + "Useful for automated prompt injection testing.</p>"
            + "<h2>Setup</h2>"
            + "<ol>"
            + "<li>Load a request first (Batch Send uses the same target as the main panel)</li>"
            + "<li>Click <b>Batch</b> to open the Batch Send dialog</li>"
            + "<li>Enter payloads in the text area (one per line) or click <b>Load File</b> to import a wordlist</li>"
            + "<li>Click <b>Start</b></li>"
            + "</ol>"
            + "<h2>Results table</h2>"
            + "<p>Each row shows: payload number, the payload text, HTTP status, latency, a response preview, and auto-mark. "
            + "Click a row to see the full response. Results are also saved to History automatically.</p>"
            + "<h2>Auto-marking</h2>"
            + "<p>If a response contains a keyword configured in Keyword Alerts, the row is automatically "
            + "marked with the corresponding mark label. Useful for spotting successful injections in large runs.</p>"
            + "<h2>Multi-turn mode</h2>"
            + "<p>The <b>Multi-turn</b> checkbox (bottom-right of the dialog) chains each payload into a "
            + "running conversation. When enabled:</p>"
            + "<ul>"
            + "<li>Payload 1 fires normally</li>"
            + "<li>Payload 2 is sent with the payload 1 / response 1 exchange prepended as a <code>messages[]</code> array</li>"
            + "<li>Each subsequent payload sees the full prior history</li>"
            + "</ul>"
            + "<p>This is useful for escalation chains — e.g. softening the model across several turns before "
            + "attempting an extraction, or testing whether injected context persists across a session.</p>"
            + "<p>The conversation resets automatically each time you click <b>Start</b>. "
            + "Turn multi-turn off to send each payload independently again.</p>"
            + "<h2>Controls</h2>"
            + "<p>Bottom bar (left to right): <b>Multi-turn</b> checkbox — <b>Delay (ms)</b> spinner (pause between payloads) "
            + "— <b>Start</b> — <b>Stop</b> — <b>Maximize</b> — <b>Close</b>.</p>";

        case "compare": return
            "<h1>Compare Endpoints</h1>"
            + "<p>Compare sends the same message to multiple endpoints simultaneously and shows the responses side-by-side. "
            + "Useful for comparing responses across agent roles, models, or configurations.</p>"
            + "<h2>Saving a slot</h2>"
            + "<p>Load a request, then click <b>Save Slot</b>. You will be prompted for a name (e.g. <code>agent1</code>). "
            + "Up to 4 slots can be saved per session. The slot count label updates in the button bar.</p>"
            + "<h2>Running a comparison</h2>"
            + "<ol>"
            + "<li>Save at least 2 slots with different endpoints</li>"
            + "<li>Type the message you want to send to all of them</li>"
            + "<li>Click <b>Compare</b> (enabled when 2+ slots are saved)</li>"
            + "</ol>"
            + "<p>A non-modal dialog opens with one column per slot. All requests fire in parallel. "
            + "Each result is also saved to History with a <code>[Compare:name]</code> note.</p>"
            + "<h2>Tips</h2>"
            + "<ul>"
            + "<li>Save the same endpoint with different session IDs to compare multi-turn state</li>"
            + "<li>Save different agents in a multi-agent system to see how each responds to the same prompt</li>"
            + "</ul>";

        case "multiturn": return
            "<h1>Multi-turn Conversations</h1>"
            + "<p>Multi-turn mode maintains a running conversation history and injects it into each request "
            + "as a <code>messages</code> array — useful for testing context retention, conversation hijacking, "
            + "and multi-step prompt injection chains.</p>"
            + "<h2>Enabling</h2>"
            + "<p>Click <b>Multi-turn: OFF</b> — it toggles to <span class='good'>Multi-turn: ON</span> and shows a turn counter.</p>"
            + "<h2>How it works</h2>"
            + "<p>Each time you send a message in multi-turn mode, PromptSlinger:</p>"
            + "<ol>"
            + "<li>Takes a snapshot of the current conversation history</li>"
            + "<li>Injects it as a <code>messages</code> array into the request body:<br>"
            + "<code>[{\"role\":\"user\",\"content\":\"...\"}, {\"role\":\"assistant\",\"content\":\"...\"}]</code></li>"
            + "<li>Appends the new user message to the array</li>"
            + "<li>After receiving the response, adds both the user message and assistant response to history</li>"
            + "</ol>"
            + "<h2>Clearing the conversation</h2>"
            + "<p>Click <b>Clear Convo</b> to reset the turn history without disabling multi-turn mode. "
            + "Turning multi-turn off does not clear the history — it just stops injecting it.</p>";

        case "history": return
            "<h1>History</h1>"
            + "<p>Every request PromptSlinger sends is automatically saved to the History tab on the left.</p>"
            + "<h2>History panel features</h2>"
            + "<ul>"
            + "<li><b>Search</b> — filters by message content, URL, or response text as you type</li>"
            + "<li><b>Marks</b> — color-coded labels (FINDING, HINT, INFO, CONFIRMED, NOISE) — right-click a row to mark it</li>"
            + "<li><b>Notes</b> — right-click a row → <b>Edit Note</b> to attach freetext notes</li>"
            + "<li><b>Replay</b> — right-click → <b>Replay</b> to re-send an exact historical request</li>"
            + "<li><b>Copy Response</b> — right-click → <b>Copy Response</b></li>"
            + "<li><b>Export</b> — right-click → <b>Export All to Markdown</b></li>"
            + "</ul>"
            + "<p>Newest entries appear at the top. Latency is shown for each entry. "
            + "Auto-marked entries (from keyword alerts) show their mark label in the table.</p>"
            + "<p><b>Clear All History</b> removes all entries and also clears the response and note fields.</p>"
            + "<p class='muted'>History is stored in memory only and does not persist across Burp restarts.</p>";

        case "decode": return
            "<h1>Decode Window</h1>"
            + "<p>Click <b>Decode</b> to open the decode window for the most recent response. "
            + "Requires at least one response to have been received.</p>"
            + "<h2>Available decoders</h2>"
            + "<table><tr><th>Decoder</th><th>Use case</th></tr>"
            + "<tr><td><code>Base64 decode</code></td><td>Decodes Base64-encoded model output</td></tr>"
            + "<tr><td><code>Hex decode</code></td><td>Decodes hex-encoded output</td></tr>"
            + "<tr><td><code>ROT13</code></td><td>Reverses ROT13 encoding</td></tr>"
            + "<tr><td><code>Reverse words</code></td><td>Reverses each word in the response</td></tr>"
            + "<tr><td><code>URL decode</code></td><td>Decodes percent-encoded strings</td></tr>"
            + "<tr><td><code>HTML decode</code></td><td>Decodes HTML entities</td></tr>"
            + "<tr><td><code>Extract JSON strings</code></td><td>Pulls all string values from a JSON response</td></tr>"
            + "</table>"
            + "<p>Decoders can be chained — apply one, then apply another to the result.</p>";

        case "themes": return
            "<h1>Themes &amp; Font</h1>"
            + "<h2>Themes</h2>"
            + "<p>Use the theme dropdown in the top-right corner to switch between colour schemes. "
            + "The UI rebuilds instantly without requiring an extension reload — all state (loaded request, "
            + "message, conversation history, saved slots) is preserved across theme changes.</p>"
            + "<h2>Available themes</h2>"
            + "<ul>"
            + "<li><b>Dracula</b> — purple/pink dark theme (default)</li>"
            + "<li><b>Monokai</b> — warm dark theme</li>"
            + "<li><b>Nord</b> — cool blue-grey dark theme</li>"
            + "<li><b>Solarized Dark</b> — muted green on dark theme</li>"
            + "<li><b>One Dark</b> — Atom-inspired dark theme</li>"
            + "<li><b>Gruvbox</b> — warm retro dark theme</li>"
            + "<li><b>Light</b> — clean light theme</li>"
            + "</ul>"
            + "<h2>Font size</h2>"
            + "<p>PromptSlinger reads Burp Suite's display font setting via the Montoya API on load, "
            + "and automatically rebuilds the UI when you change the font size in "
            + "<b>Burp → Settings → User Interface → Font</b>. No extension reload needed.</p>";

        case "shortcuts": return
            "<h1>Keyboard Shortcuts</h1>"
            + "<table><tr><th>Shortcut</th><th>Action</th></tr>"
            + "<tr><td><kbd>Ctrl+Enter</kbd></td><td>Send the current message</td></tr>"
            + "<tr><td><kbd>Enter</kbd> (payload list)</td><td>Load selected payload into message box</td></tr>"
            + "<tr><td><kbd>Double-click</kbd> (payload)</td><td>Load selected payload into message box</td></tr>"
            + "<tr><td><kbd>Double-click</kbd> (history row)</td><td>Load historical message into message box</td></tr>"
            + "<tr><td><kbd>Right-click</kbd> (history row)</td><td>Context menu: Mark / Note / Replay / Copy / Export</td></tr>"
            + "<tr><td><kbd>Right-click</kbd> (probe menu item)</td><td>Delete that saved probe</td></tr>"
            + "</table>"
            + "<hr>"
            + "<p class='muted'>PromptSlinger v1.0.3  |  By Hexxed BitHeadz  |  "
            + "<a href='overview'>Back to Overview</a></p>";

        default: return "<h1>Not found</h1><p>Section not found: " + key + "</p>";
        }
    }

    private static String toHex(Color c) {
        if (c == null) return "#888888";
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
