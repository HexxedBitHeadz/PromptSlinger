package com.promptslinger.burp;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class HistoryEntry {

    public String timestamp;
    public String url;
    public String message;
    public String response;
    public String mark;   // null | FINDING | HINT | INFO | CONFIRMED | NOISE
    public String note;

    /** Original proxy item â€” kept in memory only, used to set Burp highlight/notes. */
    @JsonIgnore
    public transient HttpRequestResponse proxyItem;

    /** The request we replay on subsequent sends. */
    @JsonIgnore
    public transient HttpRequest requestForReplay;

    /** Which JSON field holds the chatbot message. */
    @JsonIgnore
    public transient String messageField;

    public HistoryEntry() {}

    public HistoryEntry(String timestamp, String url, String message, String response,
                        HttpRequestResponse proxyItem, HttpRequest requestForReplay,
                        String messageField) {
        this.timestamp       = timestamp;
        this.url             = url;
        this.message         = message;
        this.response        = response;
        this.note            = "";
        this.proxyItem       = proxyItem;
        this.requestForReplay = requestForReplay;
        this.messageField    = messageField;
    }
}
