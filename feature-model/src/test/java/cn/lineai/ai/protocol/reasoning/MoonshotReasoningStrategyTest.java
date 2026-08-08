package cn.lineai.ai.protocol.reasoning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.ai.protocol.ReasoningRequestContext;
import org.json.JSONObject;
import org.junit.Test;

public final class MoonshotReasoningStrategyTest {
    private static final String MOONSHOT_BASE = "https://api.moonshot.ai/v1";

    private static ReasoningRequestContext context(boolean enabled, String effort, boolean preserve) {
        return new ReasoningRequestContext(enabled, effort, preserve, MOONSHOT_BASE, "kimi-k3", 0);
    }

    private static ReasoningRequestContext context(boolean enabled, String effort, boolean preserve, String model) {
        return new ReasoningRequestContext(enabled, effort, preserve, MOONSHOT_BASE, model, 0);
    }

    @Test
    public void kimiK3DoesNotSendThinkingField() throws Exception {
        // k3 不支持 thinking 字段,传了会被上游拒绝
        MoonshotReasoningStrategy strategy = new MoonshotReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(true, "max", true));

        assertFalse("kimi-k3 不应发送 thinking 字段", body.has("thinking"));
    }

    @Test
    public void kimiK3SendsReasoningEffortWhenEnabled() throws Exception {
        MoonshotReasoningStrategy strategy = new MoonshotReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(true, "max", true));

        assertEquals("max", body.getString("reasoning_effort"));
    }

    @Test
    public void kimiK3OmitsReasoningEffortWhenDisabled() throws Exception {
        // 用户关闭思考时 k3 不发 reasoning_effort(k3 本身无法关闭思考,但不发字段即用上游默认 max,避免传无效值)
        MoonshotReasoningStrategy strategy = new MoonshotReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(false, "off", false));

        assertFalse(body.has("reasoning_effort"));
        assertFalse(body.has("thinking"));
    }

    @Test
    public void kimiK3MapsUnsupportedMediumToMax() throws Exception {
        // k3 仅接受 low/high/max,medium(含 auto 归一化结果)映射到 max,避免请求被拒
        MoonshotReasoningStrategy strategy = new MoonshotReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(true, "medium", false));

        assertEquals("max", body.getString("reasoning_effort"));
    }

    @Test
    public void kimiK3KeepsLowAndHigh() throws Exception {
        MoonshotReasoningStrategy strategy = new MoonshotReasoningStrategy();
        JSONObject lowBody = new JSONObject();
        strategy.apply(lowBody, context(true, "low", false));
        assertEquals("low", lowBody.getString("reasoning_effort"));

        JSONObject highBody = new JSONObject();
        strategy.apply(highBody, context(true, "high", false));
        assertEquals("high", highBody.getString("reasoning_effort"));
    }

    @Test
    public void kimiK2xSendsThinkingInsteadOfReasoningEffort() throws Exception {
        // k2.6/k2.5 用 thinking 字段,不用 reasoning_effort
        MoonshotReasoningStrategy strategy = new MoonshotReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(true, "medium", false, "kimi-k2.6"));

        assertTrue(body.has("thinking"));
        assertFalse(body.has("reasoning_effort"));
        assertEquals("enabled", body.getJSONObject("thinking").getString("type"));
    }

    @Test
    public void kimiK2xThinkingKeepAllWhenPreserveReasoning() throws Exception {
        MoonshotReasoningStrategy strategy = new MoonshotReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(true, "medium", true, "kimi-k2.6"));

        assertEquals("all", body.getJSONObject("thinking").optString("keep"));
    }

    @Test
    public void glm52PlusSendsReasoningEffort() throws Exception {
        // GLM-5.2+ 支持 reasoning_effort 控制推理深度,启用思考时发送
        MoonshotReasoningStrategy strategy = new MoonshotReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(true, "high", false, "glm-5.2"));

        assertTrue(body.has("thinking"));
        assertTrue(body.has("reasoning_effort"));
        assertEquals("high", body.getString("reasoning_effort"));
    }

    @Test
    public void glm46DoesNotSendReasoningEffort() throws Exception {
        // GLM-4.x 仅支持 thinking 开关,不支持 reasoning_effort
        MoonshotReasoningStrategy strategy = new MoonshotReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(true, "high", false, "glm-4.6"));

        assertTrue(body.has("thinking"));
        assertFalse(body.has("reasoning_effort"));
    }

    @Test
    public void glm52PlusOmitsReasoningEffortWhenDisabled() throws Exception {
        MoonshotReasoningStrategy strategy = new MoonshotReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(false, "off", false, "glm-5.2"));

        assertFalse(body.has("reasoning_effort"));
    }

    @Test
    public void glm52PlusKeepsAllEffortValues() throws Exception {
        // GLM-5.2 接受 max/xhigh/high/medium/low/minimal/none,项目值 low/medium/high/max 均在范围内
        MoonshotReasoningStrategy strategy = new MoonshotReasoningStrategy();

        JSONObject lowBody = new JSONObject();
        strategy.apply(lowBody, context(true, "low", false, "glm-5.2"));
        assertEquals("low", lowBody.getString("reasoning_effort"));

        JSONObject maxBody = new JSONObject();
        strategy.apply(maxBody, context(true, "max", false, "glm-5.2"));
        assertEquals("max", maxBody.getString("reasoning_effort"));
    }
}
