# PromptSlinger — BurpSuite Extension

A BurpSuite extension for testing AI chatbot endpoints. Capture a real request (including all auth headers, cookies, and session tokens) directly from Burp's Proxy or Repeater, then use PromptSlinger to probe the chatbot with custom messages, output modifiers, color-coded findings, and response decoding — without ever manually configuring headers.

---

## Requirements

- BurpSuite Pro or Community Edition (2023.1 or later)
- Java 17 or later (used to build the extension)
- Maven 3.8 or later (used to build the extension)

---

## Building the Extension

Clone or download this repository, then build the fat JAR:

**Linux (dnf):**

```bash
cd HeBi-chatter-burp
sudo dnf install maven
mvn clean package
```

**Windows (ubuntu wsl):**
ubuntu
sudo apt install maven -y
cd /mnt/c/Users/devin/Desktop/osai-300/HeBi-chatter-burp
mvn clean package

That's it. The JAR lands at the Windows path:
C:\Users\devin\Desktop\osai-300\HeBi-chatter-burp\target\hebi-chatter-burp-1.0.0.jar


---

## Installing into BurpSuite

1. Open BurpSuite.
2. Go to the **Extensions** tab (top navigation bar).
3. Click **Add** in the Installed Extensions panel.
4. Set **Extension type** to `Java`.
5. Click **Select file** and choose `target/promptslinger-1.0.0.jar`.
6. Click **Next**.

If the extension loaded successfully you will see:

- `PromptSlinger loaded.` in the **Output** tab of the extension.
- A new **PromptSlinger** tab appear in the BurpSuite top navigation bar.

---

## Usage

### Step 1 — Capture a request

1. Browse to the target chatbot in your browser with Burp's proxy intercepting traffic.
2. Send a message to the chatbot so Burp captures the full request (including auth headers, session cookies, CSRF tokens, etc.).
3. In **Proxy → HTTP history** (or in **Repeater**), right-click the captured request.
4. Select **Send to PromptSlinger**.

### Step 2 — Send probes

1. Click the **PromptSlinger** tab in BurpSuite.
2. The endpoint URL is shown at the top. The **Message field** is auto-detected from the request body (e.g. `message`, `prompt`, `query`) — adjust it if needed.
3. Type your probe in the **Message** box.
4. Optionally select an **Output modifier** to instruct the model to encode its response (ROT13, Base64, Hex, Reverse, Spell-out, Spanish).
5. Click **Send** or press `Ctrl+Enter`.

### Step 3 — Review and mark findings

- The response is displayed with JSON syntax highlighting.
- If the response contains a `response` key, it is shown prominently above the raw JSON.
- Click **History** to open the full session history. Right-click any entry to apply a color mark:

| Mark | Color | Meaning |
|---|---|---|
| FINDING | Red | Significant / smoking gun |
| HINT | Yellow | Possible lead, worth pursuing |
| INFO | Cyan | General info, low significance |
| CONFIRMED | Green | Verified / corroborated finding |
| NOISE | Gray | Irrelevant / false positive |

Marks sync back to the original request's highlight in Burp's Proxy history automatically.

- Add notes to any history entry in the **Note** field — they are saved automatically.
- Click **Load message into input** to reload a previous message for re-use.

### Step 4 — Decode encoded responses

If you used an output modifier, click **Decode** to open the decode window. It auto-applies the active modifier in reverse. You can also manually chain decoders using the buttons (ROT13, Base64, Hex, Reverse, Spell-out).

---

## Features

- Works with any chatbot API — no header configuration needed, Burp handles all auth
- Auto-detects the message field in the JSON request body
- Auto-captures and replays `session_id` across requests
- Output modifiers append bypass/encoding instructions to the prompt (single-select)
- JSON syntax highlighting in the response pane
- Persistent history saved to `~/.hebi-chatter/hebi_history.json`
- Color marks sync to Burp's native proxy highlight colours
- Notes sync to Burp's native request comment field

---

## File Layout

```
HeBi-chatter-burp/
├── pom.xml
└── src/main/java/com/hebi/burp/
    ├── HeBiExtension.java      # Extension entry point
    ├── HeBiContextMenu.java    # Right-click "Send to PromptSlinger"
    ├── HeBiPanel.java          # Main UI tab
    ├── HistoryWindow.java      # History pop-up
    ├── DecodeWindow.java       # Decode pop-up
    ├── HistoryEntry.java       # Data model
    ├── HistoryStore.java       # JSON persistence
    ├── ModifierUtil.java       # Output modifier logic
    └── DecoderUtil.java        # Decoder functions
```
