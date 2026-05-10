package com.promptslinger.burp;

import java.util.regex.Pattern;

/**
 * Scans response text for credential-shaped patterns — AWS keys, API tokens,
 * private keys, connection strings, etc. Auto-flags history entries as FINDING.
 */
public class CredentialScanner {

    private record PatternDef(String label, Pattern pattern) {}

    private static final PatternDef[] PATTERNS = {
        // AWS access key
        new PatternDef("AWS Access Key",
            Pattern.compile("AKIA[A-Z0-9]{16}")),

        // AWS secret key (40-char base64-ish string following "secret" keyword)
        new PatternDef("AWS Secret Key",
            Pattern.compile("(?i)(secret.{0,20}key|aws.secret)[^\\n]{0,30}[A-Za-z0-9/+=]{40}")),

        // Generic API key / token assignment
        new PatternDef("API Key / Token",
            Pattern.compile("(?i)(api[_\\-]?key|access[_\\-]?token|auth[_\\-]?token|bearer)[\"'\\s:=]+[A-Za-z0-9_\\-]{20,}")),

        // Private key PEM header
        new PatternDef("Private Key",
            Pattern.compile("-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----")),

        // Database connection strings
        new PatternDef("DB Connection String",
            Pattern.compile("(?i)(mongodb|postgresql|postgres|mysql|redis|mssql)://[^\\s\"']{8,}")),

        // Generic password field in JSON / text
        new PatternDef("Password Field",
            Pattern.compile("(?i)\"password\"\\s*:\\s*\"[^\"]{6,}\"")),

        // Slack / GitHub / generic tokens
        new PatternDef("Token",
            Pattern.compile("(?i)(xox[baprs]-[A-Za-z0-9-]{10,}|ghp_[A-Za-z0-9]{36}|ghs_[A-Za-z0-9]{36})")),

        // Honeypot marker — explicitly planted fake credentials
        new PatternDef("Honeypot Credential",
            Pattern.compile("(?i)honeypot")),
    };

    /**
     * Returns a short description of the first credential pattern matched,
     * or null if nothing suspicious is found.
     */
    public static String scan(String responseText) {
        if (responseText == null || responseText.isBlank()) return null;
        for (PatternDef pd : PATTERNS) {
            if (pd.pattern().matcher(responseText).find()) {
                return pd.label();
            }
        }
        return null;
    }
}
