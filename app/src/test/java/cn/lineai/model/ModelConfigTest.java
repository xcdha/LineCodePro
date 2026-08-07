package cn.lineai.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class ModelConfigTest {
    @Test
    public void compressionModelConfigRoundTripsJson() throws Exception {
        ModelConfig model = new ModelConfig(
                "m1",
                "GPT",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "OpenAI",
                "https://api.openai.com/v1",
                "sk-test",
                "gpt-5",
                200,
                true,
                false,
                "gpt-5-mini",
                ModelConfig.CONTEXT_SIZE_UNSET
        );

        ModelConfig parsed = ModelConfig.fromJson(model.toJson());

        assertTrue(parsed.isCompressionModelEnabled());
        assertFalse(parsed.isCompressionModelAuto());
        assertEquals("gpt-5-mini", parsed.getCompressionModelId());
        assertEquals("gpt-5-mini", parsed.getEffectiveCompressionModelId());
    }

    @Test
    public void unsupportedProviderDisablesDedicatedCompression() throws Exception {
        ModelConfig model = new ModelConfig(
                "m1",
                "Claude",
                ModelProtocolType.ANTHROPIC_MESSAGES,
                "Anthropic",
                "https://api.anthropic.com",
                "sk-test",
                "claude-sonnet",
                200,
                true,
                false,
                "compact-model",
                ModelConfig.CONTEXT_SIZE_UNSET
        );

        assertFalse(model.isCompressionModelEnabled());

        JSONObject legacy = new JSONObject()
                .put("id", "m2")
                .put("name", "Codex")
                .put("protocolType", "codex")
                .put("providerLabel", "Codex")
                .put("baseUrl", "https://api.openai.com/v1")
                .put("apiKey", "sk-test")
                .put("modelId", "codex")
                .put("compression_model_enabled", true)
                .put("compression_model_auto", true);

        ModelConfig parsed = ModelConfig.fromJson(legacy);
        assertTrue(parsed.isCompressionModelEnabled());
        assertTrue(parsed.isCompressionModelAuto());
        assertEquals("codex", parsed.getEffectiveCompressionModelId());
    }

    @Test
    public void requiredTemperatureRoundTripsJson() throws Exception {
        ModelConfig model = new ModelConfig(
                "k3",
                "Kimi K3",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "OpenCode Go",
                "https://opencode.ai/zen/go/v1",
                "sk-test",
                "kimi-k3",
                ModelConfig.DEFAULT_TOOL_CALL_LIMIT,
                false,
                ModelConfig.DEFAULT_COMPRESSION_MODEL_AUTO,
                "",
                ModelConfig.CONTEXT_SIZE_UNSET,
                ModelConfig.TEMPERATURE_UNSET,
                1.0
        );

        ModelConfig parsed = ModelConfig.fromJson(model.toJson());

        assertEquals(ModelConfig.TEMPERATURE_UNSET, parsed.getTemperature(), 0.0);
        assertEquals(1.0, parsed.getRequiredTemperature(), 0.0);
        assertTrue(model.toJson().has("requiredTemperature"));
        assertFalse(model.toJson().has("temperature"));
    }

    @Test
    public void unsetRequiredTemperatureOmitsField() throws Exception {
        ModelConfig model = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                        "https://x", "k", "mdl")
                .build();

        assertFalse(model.toJson().has("requiredTemperature"));
        assertEquals(ModelConfig.REQUIRED_TEMPERATURE_UNSET, model.getRequiredTemperature(), 0.0);
    }
}
