package com.promptslinger.burp;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class DecoderUtil {

    public static String rot13(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if      (c >= 'a' && c <= 'z') sb.append((char) ('a' + (c - 'a' + 13) % 26));
            else if (c >= 'A' && c <= 'Z') sb.append((char) ('A' + (c - 'A' + 13) % 26));
            else sb.append(c);
        }
        return sb.toString();
    }

    public static String reverse(String text) {
        return new StringBuilder(text).reverse().toString();
    }

    public static String fromBase64(String text) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(text.trim().replaceAll("\\s", ""));
        return new String(decoded, StandardCharsets.UTF_8);
    }

    public static String fromHex(String text) throws Exception {
        String clean = text.trim().replace(" ", "").replace("\n", "").replace("\r", "");
        if (clean.length() % 2 != 0) throw new IllegalArgumentException("Odd hex length");
        byte[] bytes = new byte[clean.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** "h e l l o   w o r l d" â†’ "hello world" (double-space = word boundary) */
    public static String fromSpell(String text) {
        String[] words = text.split("  ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i].replace(" ", ""));
        }
        return sb.toString();
    }
}
