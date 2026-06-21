package cn.lineai.log;

import java.util.regex.Pattern;

public final class ErrorLogRedactor {
    private static final Pattern AUTHORIZATION = Pattern.compile("(?i)(Authorization\\s*[:=]\\s*)(Bearer\\s+)?[^\\r\\n,}]+?");
    private static final Pattern API_KEY_HEADER = Pattern.compile("(?i)((x-api-key|api-key)\\s*[:=]\\s*)[^\\r\\n,}]+?");
    private static final Pattern JSON_SECRET = Pattern.compile("(?i)(\\\"(?:api[_-]?key|authorization|access[_-]?token|refresh[_-]?token|password|secret|private[_-]?key)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")");
    private static final Pattern LONG_BASE64 = Pattern.compile("(?i)(data:image/[^;]+;base64,)[A-Za-z0-9+/=]{80,}");
    private static final Pattern B64_JSON = Pattern.compile("(?i)(\\\"b64_json\\\"\\s*:\\s*\\\")[^\\\"]{80,}(\\\")");

    private ErrorLogRedactor() {
    }

    public static String redact(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        String redacted = AUTHORIZATION.matcher(value).replaceAll("$1$2[REDACTED]");
        redacted = API_KEY_HEADER.matcher(redacted).replaceAll("$1[REDACTED]");
        redacted = JSON_SECRET.matcher(redacted).replaceAll("$1[REDACTED]$2");
        redacted = LONG_BASE64.matcher(redacted).replaceAll("$1[BASE64_REDACTED]");
        redacted = B64_JSON.matcher(redacted).replaceAll("$1[BASE64_REDACTED]$2");
        return redacted;
    }
}
