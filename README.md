# PromptSlinger

A Burp Suite extension for AI/LLM endpoint security testing. Built on the Montoya API.

---

## Features

- **Send** — inject prompts into any JSON field of a loaded request
- **Enumerate** — sweep common AI/A2A ports for agent cards (`/.well-known/agent.json`)
- **Batch Send** — fire a list of payloads sequentially, auto-mark keyword hits, view full responses inline; optional multi-turn chaining
- **Fuzz** — batch-send payload lists with delay control and live results table
- **Compare** — send the same message to multiple saved endpoints side-by-side
- **Multi-turn** — maintain conversation history across sends, injecting a `messages[]` array
- **Payload Library** — built-in categorized prompt injection payloads
- **Saved Probes** — save and reuse your own prompts
- **Decode** — decode/encode response text (Base64, URL, JWT, etc.)
- **History** — full session history with marking, notes, keyword alerts, and export
- **Keyword Alerts** — auto-mark responses that match configured keywords
- **Themes** — multiple built-in color themes; syncs font size with Burp's UI settings
- **Help** — built-in tutorial wiki covering every feature

---

## Requirements

- Burp Suite Professional or Community Edition (2023.12.1+)
- Java 17+

---

## Installation

### Option A — Download the pre-built JAR (easiest)

1. Go to the [Releases](../../releases) page
2. Download `promptslinger-1.0.0.jar`
3. In Burp Suite: **Extensions → Add → Select JAR**

### Option B — Build from source

**Prerequisites:** Java 17+, Maven 3.8+

```bash
git clone https://github.com/HexxedBitHeadz/PromptSlinger.git
cd PromptSlinger/PromptSlinger
mvn clean package
```

The JAR will be at `PromptSlinger/target/promptslinger-1.0.0.jar`.

Then load it in Burp Suite: **Extensions → Add → Select JAR**

---

## Quick Start

1. Browse to an AI/LLM endpoint in Burp Proxy
2. Right-click the request → **Send to PromptSlinger**
3. Set the **Message field** name (auto-detect usually finds it)
4. Type a message and click **Send**, or use **Enumerate** first to discover agent endpoints
5. Use **Batch Send** to run payload lists; check **Multi-turn** to chain payloads as a conversation

See the built-in **?** help button for a full feature walkthrough.

---

## Usage Notes

- The extension talks directly to target endpoints via Burp's HTTP stack — all traffic goes through Burp's proxy and appears in the HTTP history
- Session IDs are captured automatically from responses and injected into subsequent requests
- Batch Send results are tagged `[Batch:hhmmss]` in History for easy filtering
- Keyword Alerts auto-mark responses — useful for spotting successful injections in large runs

---

## Project Structure

```
PromptSlinger/
├── src/main/java/com/promptslinger/burp/   # All source files
├── pom.xml                                 # Maven build descriptor
└── target/                                 # Build output (git-ignored)
```

---

## By Hexxed BitHeadz
