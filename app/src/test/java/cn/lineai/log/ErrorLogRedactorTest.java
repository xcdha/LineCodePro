package cn.lineai.log;

import org.junit.Assert;
import org.junit.Test;

public final class ErrorLogRedactorTest {
    @Test
    public void redactsSecretsAndLargeImagePayloads() {
        String raw = "Authorization: Bearer sk-test-secret\n"
                + "x-api-key: api-secret\n"
                + "{\"api_key\":\"json-secret\",\"b64_json\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}\n"
                + "data:image/png;base64,aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        String redacted = ErrorLogRedactor.redact(raw);

        Assert.assertFalse(redacted.contains("sk-test-secret"));
        Assert.assertFalse(redacted.contains("api-secret"));
        Assert.assertFalse(redacted.contains("json-secret"));
        Assert.assertFalse(redacted.contains("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        Assert.assertTrue(redacted.contains("[REDACTED]"));
        Assert.assertTrue(redacted.contains("[BASE64_REDACTED]"));
    }
}
