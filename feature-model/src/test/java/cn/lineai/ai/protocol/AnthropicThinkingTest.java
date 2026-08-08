package cn.lineai.ai.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import org.json.JSONObject;
import org.junit.Test;

public final class AnthropicThinkingTest {
    private static ModelConfig claude(String modelId) {
        return ModelConfig.builder(
                "m", "Claude", ModelProtocolType.ANTHROPIC_MESSAGES, "Anthropic",
                "https://api.anthropic.com", "sk-test", modelId).build();
    }

    @Test
    public void claude46UsesAdaptiveThinking() throws Exception {
        // Claude 4.6+ 推荐 adaptive thinking,不发 budget_tokens,发 output_config.effort
        ModelConfig config = claude("claude-sonnet-4-6");
        JSONObject body = new AnthropicMessagesProtocol().requestBodyForTest(
                config, new ModelRequestOptions(AiBehaviorSettings.REASONING_HIGH, false));

        assertEquals("adaptive", body.getJSONObject("thinking").getString("type"));
        assertFalse(body.getJSONObject("thinking").has("budget_tokens"));
        assertEquals("high", body.getJSONObject("output_config").getString("effort"));
    }

    @Test
    public void claude47UsesAdaptiveThinking() throws Exception {
        // Claude 4.7+ 拒绝 thinking.type=enabled,必须用 adaptive
        ModelConfig config = claude("claude-opus-4-7");
        JSONObject body = new AnthropicMessagesProtocol().requestBodyForTest(
                config, new ModelRequestOptions(AiBehaviorSettings.REASONING_HIGH, false));

        assertEquals("adaptive", body.getJSONObject("thinking").getString("type"));
        assertFalse(body.getJSONObject("thinking").has("budget_tokens"));
    }

    @Test
    public void claude45UsesEnabledWithBudget() throws Exception {
        // Claude 4.5 及以下仅支持 extended thinking(enabled + budget_tokens)
        ModelConfig config = claude("claude-sonnet-4-5");
        JSONObject body = new AnthropicMessagesProtocol().requestBodyForTest(
                config, new ModelRequestOptions(AiBehaviorSettings.REASONING_HIGH, false));

        assertEquals("enabled", body.getJSONObject("thinking").getString("type"));
        assertTrue(body.getJSONObject("thinking").has("budget_tokens"));
        assertFalse(body.has("output_config"));
    }

    @Test
    public void adaptiveMaxDowngradesToHighForNonOpus46() throws Exception {
        // max 仅 Opus 4.6 支持,Sonnet 4.6 传 max 会报错,降级到 high
        ModelConfig config = claude("claude-sonnet-4-6");
        JSONObject body = new AnthropicMessagesProtocol().requestBodyForTest(
                config, new ModelRequestOptions(AiBehaviorSettings.REASONING_MAX, false));

        assertEquals("high", body.getJSONObject("output_config").getString("effort"));
    }

    @Test
    public void adaptiveMaxKeepsForOpus46() throws Exception {
        // Opus 4.6 支持 max,原样保留
        ModelConfig config = claude("claude-opus-4-6");
        JSONObject body = new AnthropicMessagesProtocol().requestBodyForTest(
                config, new ModelRequestOptions(AiBehaviorSettings.REASONING_MAX, false));

        assertEquals("max", body.getJSONObject("output_config").getString("effort"));
    }

    @Test
    public void omitsThinkingWhenDisabled() throws Exception {
        ModelConfig config = claude("claude-sonnet-4-6");
        JSONObject body = new AnthropicMessagesProtocol().requestBodyForTest(
                config, new ModelRequestOptions(AiBehaviorSettings.REASONING_OFF, false));

        assertFalse(body.has("thinking"));
        assertFalse(body.has("output_config"));
    }
}
