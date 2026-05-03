package com.promptslinger.burp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Attempts to parse an SSE-formatted response body and assemble the streamed text.
     * Returns null if the body does not look like an SSE stream.
     *
     * Handles:
     *   OpenAI:    data: {"choices":[{"delta":{"content":"..."}}]}
     *   Anthropic: data: {"type":"content_block_delta","delta":{"text":"..."}}
     *   Generic:   data: {"content":"..."} / {"text":"..."} / {"response":"..."}
     */
    public static String assemble(String body) {
        if (body == null || !body.contains("data:")) return null;

        StringBuilder sb = new StringBuilder();
        int dataLines = 0;

        for (String line : body.split("\n", -1)) {
            String t = line.trim();
            if (!t.startsWith("data:")) continue;
            String data = t.substring(5).trim();
            if ("[DONE]".equals(data) || data.isEmpty()) continue;
            dataLines++;
            try {
                JsonNode node = MAPPER.readTree(data);
                String chunk = extractChunk(node);
                if (chunk != null) sb.append(chunk);
            } catch (Exception ignored) {
                sb.append(data);
            }
        }

        // Only treat as SSE if we found multiple data: lines
        if (dataLines < 2 || sb.length() == 0) return null;
        return sb.toString();
    }

    private static String extractChunk(JsonNode node) {
        // OpenAI streaming
        if (node.has("choices")) {
            JsonNode choices = node.get("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).path("delta");
                if (delta.has("content") && !delta.get("content").isNull())
                    return delta.get("content").asText();
            }
            return null;
        }
        // Anthropic streaming
        if (node.has("type")) {
            String type = node.get("type").asText("");
            if ("content_block_delta".equals(type)) {
                JsonNode delta = node.path("delta");
                if (delta.has("text"))    return delta.get("text").asText();
                if (delta.has("content")) return delta.get("content").asText();
            }
            return null;
        }
        // Generic field fallbacks
        if (node instanceof ObjectNode on) {
            if (on.has("content"))  return on.get("content").asText();
            if (on.has("text"))     return on.get("text").asText();
            if (on.has("response")) return on.get("response").asText();
        }
        return null;
    }
}
