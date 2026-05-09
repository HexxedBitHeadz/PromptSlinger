package com.promptslinger.burp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Extracts the plain assistant reply text from various LLM response formats.
 *
 * Priority order:
 *   1. Top-level "response"          — Ollama /api/generate
 *   2. Top-level "content" / "text"  — generic
 *   3. choices[0].message.content    — OpenAI non-streaming
 *   4. message.content               — Ollama /api/chat non-streaming
 *   5. Last "assistant" entry in any "messages" array found recursively
 *      (covers Open WebUI, MegaCorpAI, and similar chat-history formats)
 *   6. null — caller falls back to pretty-printed JSON
 */
public class ResponseExtractor {

    public static String extract(JsonNode root) {
        if (root == null) return null;

        // 1. top-level "response"
        if (root.has("response") && root.get("response").isTextual())
            return root.get("response").asText();

        // 2. top-level "content" / "text"
        if (root.has("content") && root.get("content").isTextual())
            return root.get("content").asText();
        if (root.has("text") && root.get("text").isTextual())
            return root.get("text").asText();

        // 3. OpenAI choices[0].message.content
        if (root.has("choices")) {
            JsonNode choices = root.get("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode msg = choices.get(0).path("message");
                if (msg.has("content") && !msg.get("content").isNull())
                    return msg.get("content").asText();
            }
        }

        // 4. Ollama /api/chat non-streaming: message.content
        if (root.has("message")) {
            JsonNode msg = root.get("message");
            if (msg.has("content") && msg.get("content").isTextual())
                return msg.get("content").asText();
        }

        // 5. Recursive search for a "messages" array containing role:assistant entries
        String fromMessages = findLastAssistantContent(root);
        if (fromMessages != null) return fromMessages;

        return null;
    }

    /**
     * Walks the entire JSON tree looking for any array named "messages".
     * Returns the "content" of the last entry whose "role" is "assistant".
     */
    private static String findLastAssistantContent(JsonNode node) {
        if (node == null || node.isValueNode()) return null;

        if (node.isObject()) {
            if (node.has("messages")) {
                JsonNode msgs = node.get("messages");
                String found = extractLastAssistant(msgs);
                if (found != null) return found;
            }
            // Recurse into all object fields
            for (JsonNode child : node) {
                String found = findLastAssistantContent(child);
                if (found != null) return found;
            }
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findLastAssistantContent(child);
                if (found != null) return found;
            }
        }

        return null;
    }

    /**
     * Given a messages node (array or object map), returns the content of
     * the last entry with role == "assistant".
     */
    private static String extractLastAssistant(JsonNode msgs) {
        if (msgs == null) return null;

        String last = null;

        if (msgs.isArray()) {
            for (JsonNode m : (ArrayNode) msgs) {
                if (isAssistant(m)) last = contentOf(m);
            }
        } else if (msgs.isObject()) {
            // Object keyed by UUID (Open WebUI format)
            for (JsonNode m : msgs) {
                if (isAssistant(m)) last = contentOf(m);
            }
        }

        return last;
    }

    private static boolean isAssistant(JsonNode m) {
        return m.has("role") && "assistant".equalsIgnoreCase(m.get("role").asText());
    }

    private static String contentOf(JsonNode m) {
        return (m.has("content") && m.get("content").isTextual())
                ? m.get("content").asText() : null;
    }
}
