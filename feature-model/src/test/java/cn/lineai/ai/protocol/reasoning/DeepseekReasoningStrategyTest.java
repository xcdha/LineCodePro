package cn.lineai.ai.protocol.reasoning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.ai.protocol.ReasoningRequestContext;
import cn.lineai.model.AiBehaviorSettings;
import org.json.JSONObject;
import org.junit.Test;

public final class DeepseekReasoningStrategyTest {
    private static final String DEEPSEEK_BASE = "https://api.deepseek.com";

    private static ReasoningRequestContext context(boolean enabled, String effort) {
        return new ReasoningRequestContext(enabled, effort, false, DEEPSEEK_BASE, "deepseek-v4-pro", 0);
    }

    @Test
    public void sendsThinkingEnabledAndReasoningEffortWhenEnabled() throws Exception {
        DeepseekReasoningStrategy strategy = new DeepseekReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(true, AiBehaviorSettings.REASONING_HIGH));

        assertEquals("enabled", body.getJSONObject("thinking").getString("type"));
        assertEquals("high", body.getString("reasoning_effort"));
    }

    @Test
    public void sendsThinkingDisabledAndOmitsEffortWhenDisabled() throws Exception {
        DeepseekReasoningStrategy strategy = new DeepseekReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(false, AiBehaviorSettings.REASONING_OFF));

        assertEquals("disabled", body.getJSONObject("thinking").getString("type"));
        assertFalse(body.has("reasoning_effort"));
    }

    @Test
    public void mapsLowAndMediumToHigh() throws Exception {
        // DeepSeek reasoning_effort 仅接受 high/max;low/medium 会被上游映射为 high,主动映射避免语义偏差
        DeepseekReasoningStrategy strategy = new DeepseekReasoningStrategy();

        JSONObject lowBody = new JSONObject();
        strategy.apply(lowBody, context(true, AiBehaviorSettings.REASONING_LOW));
        assertEquals("high", lowBody.getString("reasoning_effort"));

        JSONObject mediumBody = new JSONObject();
        strategy.apply(mediumBody, context(true, AiBehaviorSettings.REASONING_MEDIUM));
        assertEquals("high", mediumBody.getString("reasoning_effort"));
    }

    @Test
    public void keepsHighAndMax() throws Exception {
        DeepseekReasoningStrategy strategy = new DeepseekReasoningStrategy();

        JSONObject highBody = new JSONObject();
        strategy.apply(highBody, context(true, AiBehaviorSettings.REASONING_HIGH));
        assertEquals("high", highBody.getString("reasoning_effort"));

        JSONObject maxBody = new JSONObject();
        strategy.apply(maxBody, context(true, AiBehaviorSettings.REASONING_MAX));
        assertEquals("max", maxBody.getString("reasoning_effort"));
    }

    @Test
    public void mapsAutoConcreteMediumToHigh() throws Exception {
        // 真实流程:auto 经 concreteReasoningEffort 归一化为 medium 后传入 context,
        // strategy 再将 medium 映射到 high(DeepSeek 仅接受 high/max)。
        // 此处直接传 medium 模拟 context 契约。
        DeepseekReasoningStrategy strategy = new DeepseekReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(true, AiBehaviorSettings.REASONING_MEDIUM));

        assertTrue(body.has("reasoning_effort"));
        assertEquals("high", body.getString("reasoning_effort"));
    }
}
