package com.promptslinger.burp;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Strips Socket.IO / Open WebUI UI-orchestration fields from a request body
 * before sending via the standard OpenAI-compatible HTTP API.
 *
 * When these fields are present, Open WebUI routes the AI response over a
 * Socket.IO WebSocket instead of returning it in the HTTP response body,
 * which makes PromptSlinger see only {"status":true,"task_id":"..."}.
 * Removing them forces Open WebUI to fall back to plain HTTP streaming/sync.
 */
public class RequestSanitizer {

    private static final String[] OPENWEBUI_FIELDS = {
        "session_id", "chat_id", "id", "parent_id", "parent_message",
        "background_tasks", "tool_servers", "features", "variables", "model_item"
    };

    /**
     * Removes known UI-orchestration fields in-place from the given ObjectNode.
     * Safe to call on any request — fields that are absent are silently skipped.
     */
    public static void stripOrchestrationFields(ObjectNode on) {
        for (String field : OPENWEBUI_FIELDS) {
            on.remove(field);
        }
    }
}
