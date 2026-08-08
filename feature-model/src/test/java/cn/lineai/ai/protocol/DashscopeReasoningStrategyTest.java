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

/**
 * 验证 Dashscope (Qwen) 推理策略:
 * - Qwen3.8-Max 及以上用 reasoning_effort(xhigh/medium/low),不发 thinking_budget
 * - Qwen3.7 及更早 / 非 max 变体用 enable_thinking + thinking_budget
 */
public final class DashscopeReasoningStrategyTest {

    private static ModelConfig qwenModel(String modelId) {
        return ModelConfig.builder("qwen", "Qwen", ModelProtocolType.OPENAI_COMPATIBLE, "Qwen",
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "sk-test", modelId).build();
    }

    private static JSONObject bodyFor(ModelConfig config, String effort) throws Exception {
        return new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                config, new ModelRequestOptions(effort, false));
    }

    // ========== Qwen3.8-Max:reasoning_effort 映射 ==========

    @Test
    public void qwen38MaxLowMapsToLow() throws Exception {
        JSONObject body = bodyFor(qwenModel("qwen3.8-max"), AiBehaviorSettings.REASONING_LOW);
        assertEquals("low", body.getString("reasoning_effort"));
        assertTrue(body.has("enable_thinking"));
        // Qwen3.8-Max 不发 thinking_budget(reasoning_effort 已涵盖思考强度控制)
        assertFalse(body.has("thinking_budget"));
    }

    @Test
    public void qwen38MaxMediumMapsToMedium() throws Exception {
        JSONObject body = bodyFor(qwenModel("qwen3.8-max"), AiBehaviorSettings.REASONING_MEDIUM);
        assertEquals("medium", body.getString("reasoning_effort"));
        assertFalse(body.has("thinking_budget"));
    }

    @Test
    public void qwen38MaxAutoMapsToMedium() throws Exception {
        // auto 归一化为 medium
        JSONObject body = bodyFor(qwenModel("qwen3.8-max"), AiBehaviorSettings.REASONING_AUTO);
        assertEquals("medium", body.getString("reasoning_effort"));
        assertFalse(body.has("thinking_budget"));
    }

    @Test
    public void qwen38MaxHighMapsToXhigh() throws Exception {
        // Qwen3.8-Max 不支持 high,映射为 xhigh(最高档)
        JSONObject body = bodyFor(qwenModel("qwen3.8-max"), AiBehaviorSettings.REASONING_HIGH);
        assertEquals("xhigh", body.getString("reasoning_effort"));
        assertFalse(body.has("thinking_budget"));
    }

    @Test
    public void qwen38MaxMaxMapsToXhigh() throws Exception {
        // Qwen3.8-Max 最高档为 xhigh,max 映射为 xhigh
        JSONObject body = bodyFor(qwenModel("qwen3.8-max"), AiBehaviorSettings.REASONING_MAX);
        assertEquals("xhigh", body.getString("reasoning_effort"));
        assertFalse(body.has("thinking_budget"));
    }

    @Test
    public void qwen38MaxOffDisablesThinking() throws Exception {
        // off:不发 reasoning_effort,enable_thinking=false
        JSONObject body = bodyFor(qwenModel("qwen3.8-max"), AiBehaviorSettings.REASONING_OFF);
        assertFalse(body.has("reasoning_effort"));
        assertFalse(body.getBoolean("enable_thinking"));
        assertFalse(body.has("thinking_budget"));
    }

    @Test
    public void qwen38MaxAlternateIdsAlsoMatch() throws Exception {
        // qwen3.8-max-preview / qwen3.9-max 等后续版本同样走 reasoning_effort 路径
        JSONObject body = bodyFor(qwenModel("qwen3.8-max-preview"), AiBehaviorSettings.REASONING_HIGH);
        assertEquals("xhigh", body.getString("reasoning_effort"));
        JSONObject body39 = bodyFor(qwenModel("qwen3.9-max"), AiBehaviorSettings.REASONING_LOW);
        assertEquals("low", body39.getString("reasoning_effort"));
    }

    // ========== Qwen3.7 及更早 / 非 max 变体:thinking_budget 路径 ==========

    @Test
    public void qwen37MaxUsesThinkingBudgetNotReasoningEffort() throws Exception {
        // Qwen3.7-Max 不支持 reasoning_effort,走 enable_thinking + thinking_budget
        JSONObject body = bodyFor(qwenModel("qwen3.7-max"), AiBehaviorSettings.REASONING_HIGH);
        assertFalse(body.has("reasoning_effort"));
        assertTrue(body.has("enable_thinking"));
        assertTrue(body.has("thinking_budget"));
    }

    @Test
    public void qwen3CoderUsesThinkingBudgetNotReasoningEffort() throws Exception {
        // qwen3-coder(非 max 变体)走 thinking_budget 路径
        JSONObject body = bodyFor(qwenModel("qwen3-coder"), AiBehaviorSettings.REASONING_MEDIUM);
        assertFalse(body.has("reasoning_effort"));
        assertTrue(body.has("enable_thinking"));
        assertTrue(body.has("thinking_budget"));
    }

    @Test
    public void qwen38CoderUsesThinkingBudgetNotReasoningEffort() throws Exception {
        // qwen3.8-coder(非 max 变体)仍走 thinking_budget 路径(reasoning_effort 仅 max 系列支持)
        JSONObject body = bodyFor(qwenModel("qwen3.8-coder"), AiBehaviorSettings.REASONING_HIGH);
        assertFalse(body.has("reasoning_effort"));
        assertTrue(body.has("thinking_budget"));
    }
}
