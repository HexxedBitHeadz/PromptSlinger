package com.promptslinger.burp;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
                String chunk = extractSseChunk(node);
                if (chunk != null) sb.append(chunk);
            } catch (Exception ignored) {
                sb.append(data);
            }
        }

        // Only treat as SSE if we found multiple data: lines
        if (dataLines < 2 || sb.length() == 0) return null;
        return sb.toString();
    }

    /**
     * Fallback for when Burp's sendRequest() throws a streaming exception.
     * Opens a direct HttpURLConnection to the target, reads the streaming response
     * line by line, and assembles the full text from SSE or NDJSON (Ollama) format.
     */
    public static String readStreaming(HttpRequest request, String targetUrl) {
        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(request.method());
            conn.setDoOutput(true);
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(120_000);

            for (HttpHeader h : request.headers()) {
                String name = h.name();
                if (name.equalsIgnoreCase("Host") ||
                    name.equalsIgnoreCase("Content-Length") ||
                    name.equalsIgnoreCase("Accept-Encoding") ||
                    name.equalsIgnoreCase("Connection")) continue;
                try { conn.setRequestProperty(name, h.value()); } catch (Exception ignored) {}
            }

            byte[] body = request.bodyToString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = conn.getOutputStream()) { os.write(body); }

            StringBuilder extracted = new StringBuilder();
            StringBuilder raw       = new StringBuilder();

            java.io.InputStream is;
            try {
                is = conn.getInputStream();
            } catch (Exception e) {
                is = conn.getErrorStream();
            }

            if (is != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isEmpty()) raw.append(line).append("\n");
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if ("[DONE]".equals(data)) continue;
                            try {
                                JsonNode node = MAPPER.readTree(data);
                                String chunk = extractSseChunk(node);
                                if (chunk != null) extracted.append(chunk);
                            } catch (Exception ignored) { extracted.append(data); }
                        } else {
                            // NDJSON (Ollama /api/chat and /api/generate)
                            try {
                                JsonNode node = MAPPER.readTree(line);
                                String chunk = extractNdjsonChunk(node);
                                if (chunk != null) extracted.append(chunk);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            // Return extracted text if we got it, otherwise the raw stream so the user can see it
            if (extracted.length() > 0) return extracted.toString();
            return raw.length() > 0 ? raw.toString() : null;
        } catch (Exception e) {
            return "Stream error: " + e.getMessage();
        }
    }

    private static String extractSseChunk(JsonNode node) {
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

    private static String extractNdjsonChunk(JsonNode node) {
        // Ollama /api/chat
        if (node.has("message")) {
            JsonNode msg = node.get("message");
            if (msg.has("content")) return msg.get("content").asText();
        }
        // Ollama /api/generate
        if (node.has("response")) return node.get("response").asText();
        // Generic fallbacks
        if (node.has("content")) return node.get("content").asText();
        if (node.has("text"))    return node.get("text").asText();
        return null;
    }
}
