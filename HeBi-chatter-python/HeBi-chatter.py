#!/usr/bin/env python3
import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
import subprocess
import json
import re
import codecs
import base64
import os
import threading
from datetime import datetime

# ── History store ─────────────────────────────────────────────────────────────
history = []   # list of entry dicts or session-separator dicts
MAX_HISTORY = 200
current_session_id = None   # set after root is created
last_response_text = [None]  # mutable container; holds the plain response string for decoding
history_listbox_ref = [None]  # live reference to the open history listbox, if any

HISTORY_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "hebi_history.json")
CONFIG_FILE  = os.path.join(os.path.dirname(os.path.abspath(__file__)), "hebi_config.json")

# Display labels for tags — keys match MARK_COLORS, values are user-editable
tag_labels = {}   # populated by load_config()

def save_config():
    try:
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump({"tag_labels": tag_labels}, f, indent=2)
    except Exception:
        pass

def load_config():
    defaults = {k: k for k in ["FINDING", "HINT", "INFO", "CONFIRMED", "NOISE"]}
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
            loaded = data.get("tag_labels", {})
            # Merge: keep all default keys, overlay saved values
            for k in defaults:
                tag_labels[k] = loaded.get(k, k)
            return
        except Exception:
            pass
    tag_labels.update(defaults)


def save_history():
    try:
        with open(HISTORY_FILE, "w", encoding="utf-8") as f:
            json.dump(history, f, indent=2, ensure_ascii=False)
    except Exception:
        pass


def load_history():
    if not os.path.exists(HISTORY_FILE):
        return
    try:
        with open(HISTORY_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        history.extend(data)
    except Exception:
        pass


def add_session_separator():
    sep = {
        "type": "session",
        "ts": datetime.now().strftime("%Y-%m-%d  %H:%M:%S"),
    }
    history.insert(0, sep)
    lb = history_listbox_ref[0]
    if lb is not None:
        try:
            lb.insert(0, f"  ── New Session  {sep['ts']} ──")
            lb.itemconfig(0, bg="#1e1e2e", fg="#6272a4",
                          selectbackground="#1e1e2e", selectforeground="#6272a4")
        except tk.TclError:
            history_listbox_ref[0] = None
    save_history()


MARK_COLORS = {
    "FINDING":   {"bg": "#ff5555", "fg": "#1e1e2e", "desc": "Significant / smoking gun"},
    "HINT":      {"bg": "#f1fa8c", "fg": "#1e1e2e", "desc": "Possible lead, worth pursuing"},
    "INFO":      {"bg": "#8be9fd", "fg": "#1e1e2e", "desc": "General info, low significance"},
    "CONFIRMED": {"bg": "#50fa7b", "fg": "#1e1e2e", "desc": "Verified / corroborated finding"},
    "NOISE":     {"bg": "#6272a4", "fg": "#f8f8f2", "desc": "Irrelevant / false positive"},
}


def add_history(message, response):
    entry = {
        "ts": datetime.now().strftime("%H:%M:%S"),
        "message": message,
        "response": response,
        "mark": None,
        "note": "",
    }
    history.insert(0, entry)
    if len(history) > MAX_HISTORY:
        history.pop()

    # Push new entry to the top of the open history window if it exists
    lb = history_listbox_ref[0]
    if lb is not None:
        try:
            preview = message.replace("\n", " ")[:30]
            if len(message) > 30:
                preview += "…"
            lb.insert(0, f"  {entry['ts']}  {preview}")
            if len(history) > MAX_HISTORY:
                lb.delete(tk.END)
        except tk.TclError:
            history_listbox_ref[0] = None  # window was destroyed

    save_history()


# ── JSON syntax highlighting ──────────────────────────────────────────────────
_KEY_RE   = re.compile(r'("(?:[^"\\]|\\.)*")(\s*:)')
_STR_RE   = re.compile(r':\s*("(?:[^"\\]|\\.)*")')
_NUM_RE   = re.compile(r':\s*(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)')
_BOOL_RE  = re.compile(r':\s*(true|false|null)')

def apply_highlighting(box):
    """Tag JSON keys, string values, numbers, booleans with distinct colours."""
    box.tag_remove("key", "1.0", tk.END)
    box.tag_remove("strval", "1.0", tk.END)
    box.tag_remove("numval", "1.0", tk.END)
    box.tag_remove("boolval", "1.0", tk.END)

    content = box.get("1.0", tk.END)

    def tag_matches(pattern, tag, group=1):
        for m in pattern.finditer(content):
            start_char = m.start(group)
            end_char   = m.end(group)
            start = f"1.0 + {start_char} chars"
            end   = f"1.0 + {end_char} chars"
            box.tag_add(tag, start, end)

    tag_matches(_KEY_RE,  "key",     group=1)
    tag_matches(_STR_RE,  "strval",  group=1)
    tag_matches(_NUM_RE,  "numval",  group=1)
    tag_matches(_BOOL_RE, "boolval", group=1)


# ── Smart response rendering ──────────────────────────────────────────────────
def render_response(box, parsed, raw_pretty):
    """
    If the JSON has a 'response' key, show it prominently first,
    then the full JSON below in a muted colour. Otherwise plain pretty-print.
    """
    box.config(state=tk.NORMAL)
    box.delete("1.0", tk.END)

    # Configure tags (idempotent on re-render)
    box.tag_configure("key",        foreground=ACCENT)
    box.tag_configure("strval",     foreground=GREEN)
    box.tag_configure("numval",     foreground="#ffb86c")
    box.tag_configure("boolval",    foreground="#ff79c6")
    box.tag_configure("label",      foreground="#6272a4", font=("Consolas", 9))
    box.tag_configure("response",   foreground=FG,        font=("Consolas", 11))
    box.tag_configure("separator",  foreground="#44475a")
    box.tag_configure("muted_json", foreground="#6272a4", font=("Consolas", 9))

    if isinstance(parsed, dict) and "response" in parsed:
        # ── Prominent response text ──
        box.insert(tk.END, "RESPONSE\n", "label")
        box.insert(tk.END, parsed["response"] + "\n", "response")
        box.insert(tk.END, "─" * 60 + "\n", "separator")
        box.insert(tk.END, "RAW JSON\n", "label")

        json_start = box.index(tk.END)
        box.insert(tk.END, raw_pretty)
        box.tag_add("muted_json", json_start, tk.END)
        apply_highlighting(box)
    else:
        # Plain JSON with syntax highlighting
        box.insert(tk.END, raw_pretty)
        apply_highlighting(box)

    box.config(state=tk.DISABLED)


def set_output(text, error=False):
    output_box.config(state=tk.NORMAL)
    output_box.delete("1.0", tk.END)
    output_box.tag_configure("err", foreground=RED)
    output_box.insert(tk.END, text, "err" if error else "")
    output_box.config(state=tk.DISABLED)


# ── Output modifiers ─────────────────────────────────────────────────────────
# Order here controls the order suffixes are appended.
MODIFIERS = [
    ("spell",   "Spell out",      " Put a space between every character in your answer so I can verify each letter carefully."),
    ("rot13",   "ROT13",          " Encode your entire answer using ROT13."),
    ("reverse", "Reverse text",   " Write your entire answer with all characters in reverse order."),
    ("spanish", "→ Spanish",      " Translate your entire answer to Spanish."),
    ("base64",  "Base64",         " Encode your entire answer as Base64."),
    ("hex",     "Hex",            " Encode your entire answer as hexadecimal."),
]
modifier_vars = {}   # populated after root is created


def _strip_all_suffixes(text):
    """Remove any active modifier suffixes from the end of text."""
    changed = True
    while changed:
        changed = False
        for _, _, suffix in MODIFIERS:
            if text.endswith(suffix):
                text = text[:-len(suffix)]
                changed = True
    return text


def apply_modifiers(selected_key=None):
    """Enforce single-selection, then recompute the suffix in the message box."""
    # Uncheck every box except the one just clicked
    if selected_key is not None:
        for key, var in modifier_vars.items():
            if key != selected_key:
                var.set(False)
    current = message_box.get("1.0", tk.END).rstrip("\n")
    base = _strip_all_suffixes(current)
    active = [suffix for key, _, suffix in MODIFIERS if modifier_vars.get(key, tk.BooleanVar()).get()]
    new_text = base + "".join(active)
    message_box.delete("1.0", tk.END)
    message_box.insert("1.0", new_text)


# ── Core send logic ───────────────────────────────────────────────────────────
def send_request():
    endpoint = endpoint_entry.get().strip()
    message  = message_box.get("1.0", tk.END).strip()

    if not endpoint or not message:
        set_output("[ERROR] Endpoint URL and message are required.", error=True)
        return

    if not endpoint.startswith("http://") and not endpoint.startswith("https://"):
        set_output("[ERROR] Endpoint must start with http:// or https://", error=True)
        return

    try:
        timeout_val = int(timeout_entry.get().strip())
    except ValueError:
        set_output("[ERROR] Timeout must be an integer (seconds).", error=True)
        return

    payload_dict = {"message": message}
    sid = current_session_id.get().strip()
    if sid:
        payload_dict["session_id"] = sid

    payload = json.dumps(payload_dict)
    cmd = [
        "curl", "-s", "-X", "POST", endpoint,
        "-H", "Content-Type: application/json",
        "-d", payload
    ]

    send_btn.config(state=tk.DISABLED, text="Sending…")
    set_output(f"[*] POST {endpoint}\n[*] Payload: {payload}\n\nWaiting…")

    def run():
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout_val)
            raw = result.stdout.strip()

            try:
                parsed = json.loads(raw)
                pretty = json.dumps(parsed, indent=2)

                # Auto-capture session_id
                if isinstance(parsed, dict) and "session_id" in parsed:
                    root.after(0, lambda: current_session_id.set(parsed["session_id"]))
                    root.after(0, update_session_label)

                # Store plain response text for the decode window
                if isinstance(parsed, dict) and "response" in parsed:
                    last_response_text[0] = parsed["response"]
                else:
                    last_response_text[0] = pretty

                root.after(0, lambda: render_response(output_box, parsed, pretty))
            except json.JSONDecodeError:
                pretty = raw if raw else result.stderr.strip()
                root.after(0, lambda: set_output(pretty))

            add_history(message, pretty if 'pretty' in dir() else raw)

        except subprocess.TimeoutExpired:
            root.after(0, lambda: set_output(f"[ERROR] Request timed out ({timeout_val}s).", error=True))
        except FileNotFoundError:
            root.after(0, lambda: set_output("[ERROR] curl not found. Is it installed and in PATH?", error=True))
        except Exception as e:
            root.after(0, lambda: set_output(f"[ERROR] {e}", error=True))
        finally:
            root.after(0, lambda: send_btn.config(state=tk.NORMAL, text="Send Request"))

    threading.Thread(target=run, daemon=True).start()


def update_session_label():
    sid = current_session_id.get()
    if sid:
        session_label.config(text=f"Session ID: {sid[:36]}", fg=ACCENT)
    else:
        session_label.config(text="Session ID: none", fg="#6272a4")


def clear_session():
    current_session_id.set("")
    update_session_label()


def clear_all():
    message_box.delete("1.0", tk.END)
    output_box.config(state=tk.NORMAL)
    output_box.delete("1.0", tk.END)
    output_box.config(state=tk.DISABLED)
    clear_session()
    for var in modifier_vars.values():
        var.set(False)


def copy_response():
    content = output_box.get("1.0", tk.END).strip()
    if content:
        root.clipboard_clear()
        root.clipboard_append(content)


# ── Decoder logic ────────────────────────────────────────────────────────────
def _decode_rot13(text):
    return codecs.encode(text, "rot_13")

def _decode_reverse(text):
    return text[::-1]

def _decode_base64(text):
    return base64.b64decode(text.strip().encode()).decode("utf-8", errors="replace")

def _decode_hex(text):
    return bytes.fromhex(text.strip().replace(" ", "").replace("\n", "")).decode("utf-8", errors="replace")

def _decode_spell(text):
    # "h e l l o   w o r l d" → "hello world"
    # Double-space marks a word boundary; single-space separates characters.
    words = text.split("  ")
    return " ".join("".join(w.split(" ")) for w in words)

DECODERS = {
    "spell":   ("Spell-out",  _decode_spell),
    "rot13":   ("ROT13",      _decode_rot13),
    "reverse": ("Reverse",    _decode_reverse),
    "base64":  ("Base64",     _decode_base64),
    "hex":     ("Hex",        _decode_hex),
}


def open_decode_window():
    source = last_response_text[0]
    if not source:
        set_output("[INFO] No response to decode yet.", error=False)
        return

    win = tk.Toplevel(root)
    win.title("Decode Response")
    win.configure(bg=BG)
    win.geometry("700x500")
    win.minsize(500, 380)

    tk.Label(win, text="Decode Response", bg=BG, fg=ACCENT,
             font=("Consolas", 13, "bold")).pack(pady=(12, 2))
    tk.Label(win, text="Auto-applies active modifiers in reverse  |  or try any manually below",
             bg=BG, fg="#6272a4", font=("Consolas", 9)).pack(pady=(0, 4))
    ttk.Separator(win, orient="horizontal").pack(fill="x", padx=16, pady=4)

    # ── Output text ───────────────────────────────────────────────────────────
    result_box = scrolledtext.ScrolledText(
        win, bg=SURFACE, fg=GREEN, font=FONT,
        relief="flat", bd=4, wrap=tk.WORD, state=tk.DISABLED
    )
    result_box.pack(fill="both", expand=True, padx=16, pady=(4, 8))

    def show(text, note=None):
        result_box.config(state=tk.NORMAL)
        result_box.delete("1.0", tk.END)
        if note:
            result_box.tag_configure("note", foreground="#ffb86c", font=("Consolas", 9, "italic"))
            result_box.insert(tk.END, note + "\n\n", "note")
        result_box.insert(tk.END, text)
        result_box.config(state=tk.DISABLED)

    # ── Bottom bar: manual decode buttons + copy ──────────────────────────────
    bar = tk.Frame(win, bg=BG)
    bar.pack(fill="x", padx=16, pady=(0, 12))

    tk.Label(bar, text="Try:", bg=BG, fg="#6272a4", font=("Consolas", 9)).pack(side="left", padx=(0, 6))

    current_text = [source]   # mutable so inner functions can update it

    def manual_decode(key):
        _, fn = DECODERS[key]
        try:
            decoded = fn(current_text[0])
            current_text[0] = decoded
            show(decoded)
        except Exception as e:
            show(current_text[0], note=f"[Decode failed: {e}]")

    for key, (label, _) in DECODERS.items():
        tk.Button(
            bar, text=label, font=("Consolas", 9),
            bg=SURFACE, fg=FG, relief="flat", padx=10, pady=4,
            cursor="hand2", activebackground="#44475a", activeforeground=ACCENT,
            command=lambda k=key: manual_decode(k)
        ).pack(side="left", padx=3)

    tk.Button(
        bar, text="Copy", font=("Consolas", 9, "bold"),
        bg=SURFACE, fg=ACCENT, relief="flat", padx=10, pady=4, cursor="hand2",
        activebackground="#44475a", activeforeground=ACCENT,
        command=lambda: (win.clipboard_clear(), win.clipboard_append(result_box.get("1.0", tk.END).strip()))
    ).pack(side="right")

    tk.Button(
        bar, text="Reset", font=("Consolas", 9),
        bg=SURFACE, fg="#6272a4", relief="flat", padx=10, pady=4, cursor="hand2",
        activebackground="#44475a", activeforeground=FG,
        command=lambda: (current_text.__setitem__(0, source), show(source))
    ).pack(side="right", padx=(0, 6))

    # ── Auto-decode based on active modifiers (reverse order) ─────────────────
    active_keys = [key for key, _, _ in MODIFIERS if modifier_vars.get(key, tk.BooleanVar()).get()
                   and key in DECODERS]
    notes = []
    if modifier_vars.get("spanish", tk.BooleanVar()).get():
        notes.append("Note: Spanish modifier was active — auto-translation not available. Copy text and use a translator.")

    auto_text = source
    for key in reversed(active_keys):
        try:
            auto_text = DECODERS[key][1](auto_text)
        except Exception as e:
            notes.append(f"{DECODERS[key][0]} decode failed: {e}")

    current_text[0] = auto_text
    show(auto_text, note="\n".join(notes) if notes else None)


# ── History window ─────────────────────────────────────────────────────────────
def open_history():
    if not history:
        set_output("[INFO] No history yet — send a request first.")
        return

    win = tk.Toplevel(root)
    win.title("Message History")
    win.configure(bg=BG)
    _sw = root.winfo_screenwidth()
    _sh = root.winfo_screenheight()
    _h = int(_sh * 0.9)
    win.geometry(f"{_sw // 2}x{_h}+0+0")
    win.minsize(700, 400)

    tk.Label(win, text="History  (newest first)", bg=BG, fg=ACCENT,
             font=("Consolas", 13, "bold")).pack(pady=(12, 4))

    # ── Color legend (click a chip label to rename it) ───────────────────────
    legend_frame = tk.Frame(win, bg=BG)
    legend_frame.pack(pady=(0, 4))
    tk.Label(legend_frame, text="Tags:", bg=BG, fg="#6272a4",
             font=("Consolas", 9)).pack(side="left", padx=(0, 8))

    def make_chip(parent, key, colors):
        chip = tk.Frame(parent, bg=colors["bg"], padx=6, pady=2)
        chip.pack(side="left", padx=3)

        lbl = tk.Label(chip, text=tag_labels[key], bg=colors["bg"], fg=colors["fg"],
                       font=("Consolas", 8, "bold"), cursor="xterm")
        lbl.pack()

        tip = tk.Label(win, text=colors["desc"] + "  (click to rename)",
                       bg="#44475a", fg=FG, font=("Consolas", 8), relief="flat", padx=6, pady=2)

        def _enter(e, t=tip): t.place(x=e.x_root - win.winfo_rootx(),
                                      y=e.y_root - win.winfo_rooty() + 20)
        def _leave(e, t=tip): t.place_forget()

        def start_edit(e, k=key, c=colors, l=lbl, t=tip):
            t.place_forget()
            l.pack_forget()
            entry = tk.Entry(chip, width=10, bg=c["bg"], fg=c["fg"],
                             font=("Consolas", 8, "bold"), relief="flat", bd=0,
                             insertbackground=c["fg"], highlightthickness=0)
            entry.insert(0, tag_labels[k])
            entry.pack()
            entry.focus_set()
            entry.select_range(0, tk.END)

            def finish(ev=None, k=k, l=l):
                raw = entry.get()[:12].strip()
                tag_labels[k] = raw if raw else k
                save_config()
                l.config(text=tag_labels[k])
                entry.destroy()
                l.pack()

            entry.bind("<Return>",  finish)
            entry.bind("<Escape>",  lambda ev: (entry.destroy(), l.pack()))
            entry.bind("<FocusOut>", finish)

        lbl.bind("<Button-1>", start_edit)
        chip.bind("<Enter>", _enter)
        chip.bind("<Leave>", _leave)
        lbl.bind("<Enter>", _enter)
        lbl.bind("<Leave>", _leave)

    for name, colors in MARK_COLORS.items():
        make_chip(legend_frame, name, colors)

    ttk.Separator(win, orient="horizontal").pack(fill="x", padx=16, pady=4)

    # ── Bottom bar (must be packed before the expanding pane) ────────────────
    bottom_bar = tk.Frame(win, bg=BG)
    bottom_bar.pack(side="bottom", fill="x", pady=(0, 6))

    # ── Two-pane layout ───────────────────────────────────────────────────────
    pane = tk.PanedWindow(win, orient=tk.HORIZONTAL, bg=BG,
                          sashwidth=6, sashrelief="flat", bd=0)
    pane.pack(fill="both", expand=True, padx=12, pady=(0, 4))

    # ── Left: entry list ──────────────────────────────────────────────────────
    left = tk.Frame(pane, bg=BG)
    pane.add(left, minsize=200, width=260)

    listbox = tk.Listbox(
        left, bg=SURFACE, fg=FG, selectbackground=ACCENT,
        selectforeground="#1e1e2e", font=("Consolas", 9),
        relief="flat", bd=0, activestyle="none", cursor="hand2"
    )
    list_scroll = ttk.Scrollbar(left, orient="vertical", command=listbox.yview)
    listbox.configure(yscrollcommand=list_scroll.set)
    list_scroll.pack(side="right", fill="y")
    listbox.pack(fill="both", expand=True)

    history_listbox_ref[0] = listbox
    win.protocol("WM_DELETE_WINDOW", lambda: (
        history_listbox_ref.__setitem__(0, None), win.destroy()
    ))

    for i, entry in enumerate(history):
        if entry.get("type") == "session":
            listbox.insert(tk.END, f"  ── New Session  {entry['ts']} ──")
            listbox.itemconfig(i, bg="#1e1e2e", fg="#6272a4",
                               selectbackground="#1e1e2e", selectforeground="#6272a4")
        else:
            preview = entry["message"].replace("\n", " ")[:30]
            if len(entry["message"]) > 30:
                preview += "…"
            listbox.insert(tk.END, f"  {entry['ts']}  {preview}")
            if entry.get("mark"):
                c = MARK_COLORS[entry["mark"]]
                listbox.itemconfig(i, bg=c["bg"], fg=c["fg"],
                                   selectbackground=c["bg"], selectforeground=c["fg"])

    # ── Right-click mark menu ─────────────────────────────────────────────────
    def apply_mark(idx, color_name):
        history[idx]["mark"] = color_name
        if color_name is None:
            listbox.itemconfig(idx, bg=SURFACE, fg=FG,
                               selectbackground=ACCENT, selectforeground="#1e1e2e")
        else:
            c = MARK_COLORS[color_name]
            listbox.itemconfig(idx, bg=c["bg"], fg=c["fg"],
                               selectbackground=c["bg"], selectforeground=c["fg"])
        save_history()

    def show_mark_menu(event):
        idx = listbox.nearest(event.y)
        if idx < 0 or idx >= len(history):
            return
        if history[idx].get("type") == "session":
            return   # no context menu for session separators
        listbox.selection_clear(0, tk.END)
        listbox.selection_set(idx)
        show_entry(idx)

        menu = tk.Menu(win, tearoff=0, bg=SURFACE, fg=FG,
                       activebackground=ACCENT, activeforeground="#1e1e2e",
                       font=("Consolas", 9), bd=0)
        menu.add_command(label="  Clear mark", command=lambda: apply_mark(idx, None))
        menu.add_separator()
        for name, colors in MARK_COLORS.items():
            menu.add_command(
                label=f"  ● {tag_labels.get(name, name)}",
                foreground=colors["bg"],
                command=lambda n=name: apply_mark(idx, n)
            )
        menu.tk_popup(event.x_root, event.y_root)

    listbox.bind("<Button-3>", show_mark_menu)

    # ── Right: detail view (grid layout for reliable resize behaviour) ──────────
    right = tk.Frame(pane, bg=BG)
    pane.add(right, minsize=300)
    right.columnconfigure(0, weight=1)
    right.rowconfigure(3, weight=1)   # response row expands; everything else fixed

    # Row 0 — MESSAGE label + box
    tk.Label(right, text="MESSAGE", bg=BG, fg="#6272a4",
             font=("Consolas", 9, "bold")).grid(row=0, column=0, sticky="w", padx=8, pady=(6, 2))
    msg_view = scrolledtext.ScrolledText(
        right, height=5, bg=ENTRY_BG, fg=FG, font=FONT,
        relief="flat", bd=4, wrap=tk.WORD, state=tk.DISABLED
    )
    msg_view.grid(row=1, column=0, sticky="ew", padx=8)

    # Row 2 — RESPONSE label
    tk.Label(right, text="RESPONSE", bg=BG, fg="#6272a4",
             font=("Consolas", 9, "bold")).grid(row=2, column=0, sticky="w", padx=8, pady=(8, 2))

    # Row 3 — Response box (expands)
    resp_view = scrolledtext.ScrolledText(
        right, bg=SURFACE, fg=GREEN, font=FONT,
        relief="flat", bd=4, wrap=tk.WORD, state=tk.DISABLED
    )
    resp_view.grid(row=3, column=0, sticky="nsew", padx=8)

    # Row 4 — NOTE label
    note_header = tk.Frame(right, bg=BG)
    note_header.grid(row=4, column=0, sticky="ew", padx=8, pady=(8, 2))
    tk.Label(note_header, text="NOTE", bg=BG, fg="#6272a4",
             font=("Consolas", 9, "bold")).pack(side="left")
    tk.Label(note_header, text="  (auto-saved)", bg=BG, fg="#44475a",
             font=("Consolas", 8, "italic")).pack(side="left")

    # Row 5 — Note text box (fixed height)
    note_entry = tk.Text(right, height=2, bg=ENTRY_BG, fg="#f1fa8c",
                         insertbackground=FG, font=("Consolas", 10),
                         relief="flat", bd=4, wrap=tk.WORD, undo=True)
    note_entry.grid(row=5, column=0, sticky="ew", padx=8)

    # Row 6 — Load button
    load_btn = tk.Button(
        right, text="Load message into input →", font=("Consolas", 10, "bold"),
        bg=ACCENT, fg="#1e1e2e", relief="flat", padx=16, pady=6, cursor="hand2",
        activebackground="#a070e0", activeforeground="#1e1e2e",
        state=tk.DISABLED
    )
    load_btn.grid(row=6, column=0, pady=8)

    current_idx = [None]   # track which entry's note is being edited

    def save_note(_event=None):
        if current_idx[0] is not None:
            history[current_idx[0]]["note"] = note_entry.get("1.0", tk.END).strip()

    note_entry.bind("<KeyRelease>", save_note)

    def show_entry(idx):
        save_note()
        entry = history[idx]

        if entry.get("type") == "session":
            current_idx[0] = None
            msg_view.config(state=tk.NORMAL)
            msg_view.delete("1.0", tk.END)
            msg_view.config(state=tk.DISABLED)
            resp_view.config(state=tk.NORMAL)
            resp_view.delete("1.0", tk.END)
            resp_view.config(state=tk.DISABLED)
            note_entry.delete("1.0", tk.END)
            load_btn.config(state=tk.DISABLED)
            return

        current_idx[0] = idx

        msg_view.config(state=tk.NORMAL)
        msg_view.delete("1.0", tk.END)
        msg_view.insert(tk.END, entry["message"])
        msg_view.config(state=tk.DISABLED)

        resp_view.config(state=tk.NORMAL)
        resp_view.delete("1.0", tk.END)
        resp_view.insert(tk.END, entry["response"])
        resp_view.config(state=tk.DISABLED)

        note_entry.delete("1.0", tk.END)
        if entry.get("note"):
            note_entry.insert("1.0", entry["note"])

        load_btn.config(
            state=tk.NORMAL,
            command=lambda m=entry["message"]: (
                message_box.delete("1.0", tk.END),
                message_box.insert(tk.END, m),
                win.destroy()
            )
        )

    def on_select(event):
        sel = listbox.curselection()
        if sel:
            show_entry(sel[0])

    def clear_all_history():
        if not tk.messagebox.askyesno("Clear All History",
                                      "Delete all history entries permanently?",
                                      parent=win):
            return
        history.clear()
        listbox.delete(0, tk.END)
        msg_view.config(state=tk.NORMAL); msg_view.delete("1.0", tk.END); msg_view.config(state=tk.DISABLED)
        resp_view.config(state=tk.NORMAL); resp_view.delete("1.0", tk.END); resp_view.config(state=tk.DISABLED)
        note_entry.delete("1.0", tk.END)
        load_btn.config(state=tk.DISABLED)
        current_idx[0] = None
        save_history()

    tk.Button(bottom_bar, text="Clear All History", command=clear_all_history,
              bg=BG, fg=RED, font=("Consolas", 9), relief="flat",
              cursor="hand2", activebackground=BG, activeforeground="#ff8888",
              bd=0, padx=8, pady=4).pack(side="right", padx=12)

    listbox.bind("<<ListboxSelect>>", on_select)

    # Auto-select the first real entry
    for i, e in enumerate(history):
        if e.get("type") != "session":
            listbox.selection_set(i)
            show_entry(i)
            break


# ── Root window ───────────────────────────────────────────────────────────────
load_config()
load_history()

root = tk.Tk()
current_session_id = tk.StringVar()   # must be created after root
add_session_separator()
root.title("HeBi Chat Tool")
root.configure(bg="#1e1e2e")
root.resizable(True, True)
root.minsize(640, 580)
_sw = root.winfo_screenwidth()
_sh = root.winfo_screenheight()
_h = int(_sh * 0.9)
root.geometry(f"{_sw // 2}x{_h}+{_sw // 2}+0")

BG       = "#1e1e2e"
SURFACE  = "#2a2a3d"
ACCENT   = "#bd93f9"
FG       = "#f8f8f2"
GREEN    = "#50fa7b"
RED      = "#ff5555"
ENTRY_BG = "#313244"
FONT     = ("Consolas", 11)
FONT_LG  = ("Consolas", 13, "bold")

style = ttk.Style()
style.theme_use("clam")

# ── Title ─────────────────────────────────────────────────────────────────────
tk.Label(root, text="HeBi Chat Tool", bg=BG, fg=ACCENT,
         font=("Consolas", 16, "bold")).pack(pady=(14, 10))

ttk.Separator(root, orient="horizontal").pack(fill="x", padx=20, pady=4)

# ── Endpoint URL row ──────────────────────────────────────────────────────────
conn_frame = tk.Frame(root, bg=BG)
conn_frame.pack(fill="x", padx=20, pady=(8, 4))

tk.Label(conn_frame, text="Endpoint URL:", bg=BG, fg=FG, font=FONT).grid(
    row=0, column=0, sticky="w", padx=(0, 8))
endpoint_entry = tk.Entry(conn_frame, bg=ENTRY_BG, fg=GREEN, insertbackground=FG,
                          font=FONT, relief="flat", bd=4)
endpoint_entry.insert(0, "http://192.168.215.21:8012/chat")
endpoint_entry.grid(row=0, column=1, sticky="ew", padx=(0, 20))
conn_frame.columnconfigure(1, weight=1)

tk.Label(conn_frame, text="Timeout (s):", bg=BG, fg=FG, font=FONT).grid(
    row=0, column=2, sticky="w", padx=(0, 8))
timeout_entry = tk.Entry(conn_frame, bg=ENTRY_BG, fg=GREEN, insertbackground=FG,
                         font=FONT, width=5, relief="flat", bd=4)
timeout_entry.insert(0, "30")
timeout_entry.grid(row=0, column=3, sticky="w")

# ── Session ID row ────────────────────────────────────────────────────────────
sess_frame = tk.Frame(root, bg=BG)
sess_frame.pack(fill="x", padx=20, pady=(2, 4))

session_label = tk.Label(sess_frame, text="Session ID: none", bg=BG, fg="#6272a4",
                          font=("Consolas", 9))
session_label.pack(side="left")

tk.Button(sess_frame, text="✕ clear session", command=clear_session,
          bg=BG, fg="#6272a4", font=("Consolas", 9), relief="flat",
          cursor="hand2", activebackground=BG, activeforeground=RED,
          bd=0, padx=6).pack(side="left", padx=(8, 0))

# ── Message input ─────────────────────────────────────────────────────────────
tk.Label(root, text="Message:", bg=BG, fg=FG, font=FONT,
         anchor="w").pack(fill="x", padx=20, pady=(8, 2))

message_box = scrolledtext.ScrolledText(
    root, height=5, bg=ENTRY_BG, fg=FG, insertbackground=FG,
    font=FONT, relief="flat", bd=4, wrap=tk.WORD, undo=True
)
message_box.pack(fill="x", padx=20)

# ── Output modifier checkboxes ────────────────────────────────────────────────
mod_frame = tk.Frame(root, bg=BG)
mod_frame.pack(fill="x", padx=20, pady=(6, 0))

tk.Label(mod_frame, text="Output modifiers:", bg=BG, fg="#6272a4",
         font=("Consolas", 9, "bold")).grid(row=0, column=0, sticky="w", padx=(2, 12))

for col, (key, label, _suffix) in enumerate(MODIFIERS):
    var = tk.BooleanVar(value=False)
    modifier_vars[key] = var
    tk.Checkbutton(
        mod_frame, text=label, variable=var, command=lambda k=key: apply_modifiers(k),
        bg=BG, fg="#6272a4", selectcolor=ENTRY_BG,
        activebackground=BG, activeforeground=ACCENT,
        font=("Consolas", 9), cursor="hand2"
    ).grid(row=0, column=col + 1, sticky="w", padx=(0, 10))

# ── Buttons ───────────────────────────────────────────────────────────────────
btn_frame = tk.Frame(root, bg=BG)
btn_frame.pack(pady=12)

send_btn = tk.Button(
    btn_frame, text="Send Request", command=send_request,
    bg=ACCENT, fg="#1e1e2e", font=("Consolas", 11, "bold"),
    relief="flat", padx=20, pady=6, cursor="hand2",
    activebackground="#a070e0", activeforeground="#1e1e2e"
)
send_btn.grid(row=0, column=0, padx=6)

tk.Button(
    btn_frame, text="History", command=open_history,
    bg=SURFACE, fg=ACCENT, font=FONT, relief="flat",
    padx=14, pady=6, cursor="hand2",
    activebackground="#44475a", activeforeground=ACCENT
).grid(row=0, column=1, padx=6)

tk.Button(
    btn_frame, text="Decode", command=open_decode_window,
    bg=SURFACE, fg="#ffb86c", font=FONT, relief="flat",
    padx=14, pady=6, cursor="hand2",
    activebackground="#44475a", activeforeground="#ffb86c"
).grid(row=0, column=2, padx=6)

tk.Button(
    btn_frame, text="Copy Response", command=copy_response,
    bg=SURFACE, fg=FG, font=FONT, relief="flat",
    padx=14, pady=6, cursor="hand2",
    activebackground="#44475a", activeforeground=FG
).grid(row=0, column=3, padx=6)

tk.Button(
    btn_frame, text="Clear", command=clear_all,
    bg=SURFACE, fg=FG, font=FONT, relief="flat",
    padx=14, pady=6, cursor="hand2",
    activebackground="#44475a", activeforeground=FG
).grid(row=0, column=4, padx=6)

# ── Output box ────────────────────────────────────────────────────────────────
tk.Label(root, text="Response:", bg=BG, fg=FG, font=FONT,
         anchor="w").pack(fill="x", padx=20, pady=(4, 2))

output_box = scrolledtext.ScrolledText(
    root, height=14, bg=SURFACE, fg=FG, insertbackground=FG,
    font=FONT, relief="flat", bd=4, wrap=tk.WORD, state=tk.DISABLED
)
output_box.pack(fill="both", expand=True, padx=20, pady=(0, 16))

# ── Keybinds ──────────────────────────────────────────────────────────────────
message_box.bind("<Return>", lambda e: (send_request(), "break")[1])
message_box.bind("<Shift-Return>", lambda e: None)   # let tkinter insert newline normally
root.bind("<Control-Return>", lambda e: send_request())
root.bind("<Control-h>", lambda e: open_history())

# Standard editing shortcuts for all Text widgets (including Toplevel children)
def _select_all(e):
    e.widget.tag_add("sel", "1.0", "end")
    return "break"

def _undo(e):
    try:
        e.widget.edit_undo()
    except Exception:
        pass
    return "break"

def _paste(e):
    try:
        if str(e.widget.cget("state")) != "disabled":
            e.widget.event_generate("<<Paste>>")
    except Exception:
        pass
    return "break"

root.bind_class("Text", "<Control-a>", _select_all)
root.bind_class("Text", "<Control-z>", _undo)

root.mainloop()