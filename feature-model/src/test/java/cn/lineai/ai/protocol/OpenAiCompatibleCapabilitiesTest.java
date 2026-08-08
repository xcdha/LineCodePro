package cn.lineai.ai.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import org.junit.Test;

public final class OpenAiCompatibleCapabilitiesTest {
    @Test
    public void nvidiaGatewayDisablesNativeToolsAndReasoningParameters() {
        ModelConfig config = ModelConfig.builder(
                "nvidia",
                "NVIDIA DeepSeek",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "NVIDIA",
                "https://integrate.api.nvidia.com/v1",
                "sk-test",
                "deepseek-ai/deepseek-v4-pro").build();

        assertFalse(OpenAiCompatibleCapabilities.supportsNativeTools(config));
        assertFalse(OpenAiCompatibleCapabilities.supportsReasoningRequestParameters(config));
    }

    @Test
    public void regularOpenAiCompatibleProviderKeepsNativeToolsAndReasoningParameters() {
        ModelConfig config = ModelConfig.builder(
                "qwen",
                "Qwen",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "Qwen",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "sk-test",
                "qwen/qwen3-coder").build();

        assertTrue(OpenAiCompatibleCapabilities.supportsNativeTools(config));
        assertTrue(OpenAiCompatibleCapabilities.supportsReasoningRequestParameters(config));
    }

    @Test
    public void knownHardTemperatureReturnsOneForKimiAlwaysThinkingModels() {
        // kimi-k3 / kimi-k2.7-code 始终思考,temperature 固定 1.0,与 reasoningEnabled 无关
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k3", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k3", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.7-code", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.7-code", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.7-code-highspeed", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.7-code-highspeed", false));
    }

    @Test
    public void knownHardTemperatureDiffersByThinkingModeForKimiK2x() {
        // kimi-k2.6 / kimi-k2.5:思考模式固定 1.0,非思考模式固定 0.6
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.6", true));
        assertEquals(Double.valueOf(0.6), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.6", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.5", true));
        assertEquals(Double.valueOf(0.6), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.5", false));
    }

    @Test
    public void knownHardTemperatureReturnsOneForOpenAiAlwaysReasoningModels() {
        // o 系列与 gpt-5 初代/pro/codex/mini/nano 始终推理,temperature 固定 1,与思考模式无关
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o1", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o1-mini", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o3", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o3-mini", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o4-mini", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-mini", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-pro", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-codex", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1-codex", true));
    }

    @Test
    public void knownHardTemperatureForGpt5xDiffersByThinkingMode() {
        // gpt-5.x(x>=1) 支持 reasoning_effort=none(关闭思考),关闭时允许 temperature(top_p);
        // 思考模式(effort != none)下只接受 temperature=1。
        // 覆盖:gpt-5.1 / 5.2 / 5.5 / 5.6
        // 来源:developers.openai.com gpt-5.5/gpt-5.6 文档、community.openai.com/t/1371738
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2-codex", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2-codex", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.5", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.5", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.6", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.6", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.6-sol", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.6-sol", false));
    }

    @Test
    public void knownHardTemperatureReturnsMustOmitForClaude5FamilyAndOpus47() {
        // Claude 5 家族 + Opus 4.7/4.8:temperature/top_p/top_k 已移除,传非默认值返回 400。
        // 来源:platform.claude.com whats-new-sonnet-5、migration-guide
        // 覆盖:claude-sonnet-5、claude-opus-5、claude-haiku-5、claude-fable-5、claude-mythos-5
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-sonnet-5", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-sonnet-5", false));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-opus-5", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-haiku-5", false));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-fable-5", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-mythos-5", true));
        // Opus 4.7 / 4.8 同样移除了采样参数
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-opus-4-7", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-opus-4-8", false));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-opus-4.7", true));
    }

    @Test
    public void knownHardTemperatureReturnsNullForFlexibleModels() {
        // 支持灵活温度的普通模型、非推理 chat-latest 变体、DeepSeek V4 Flash、GLM-5.2、Qwen3.8-max 均不在硬性表
        // DeepSeek V4 Flash:thinking 模式下 temperature 无效但不报错(非硬性要求)
        // GLM-5.2:temperature 0-1 默认 1.0(非硬性要求)
        // Qwen3.8-max:思考模式默认 0.6,<0.6 自动夹紧不报错(非硬性要求)
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-4o", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-chat-latest", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2-chat-latest", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("deepseek-v4-flash", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("deepseek-v4-pro", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("glm-4.6", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("glm-5.2", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("glm-5.3", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("qwen3.8-max", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("qwen3-coder", true));
        // Claude 3.x 及 Opus 4.6 仍接受 temperature(0.0-1.0)
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("claude-3-7-sonnet", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("claude-opus-4-6", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature(null, true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("", false));
    }

    @Test
    public void knownHardTemperatureReturnsMustOmitForSearchVariants() {
        // search 变体必须省略 temperature 字段(传任何值都报错),返回 TEMPERATURE_MUST_OMIT 哨兵值
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-search-api", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("gpt-4o-search-preview", false));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("gpt-4o-mini-search-preview", true));
    }

    // ========== supportsNoneEffort ==========

    @Test
    public void supportsNoneEffortForGpt5xSeries() {
        // gpt-5.x(x>=1) 系列支持 reasoning_effort=none(关闭思考)
        // 覆盖:gpt-5.1 / 5.2 / 5.5 / 5.6
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.1"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.2"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.2-codex"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.6"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.6-sol"));
        // chat-latest 变体不支持(只支持 medium)
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.2-chat-latest"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.6-chat-latest"));
        // gpt-5 初代/pro/codex/mini/nano 不支持 none
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5-pro"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5-codex"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("o3"));
        // Claude 5 不支持 none(用 thinking.type=disabled 关闭思考,而非 reasoning_effort=none)
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("claude-sonnet-5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort(null));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort(""));
    }

    // ========== supportsXhigh ==========

    @Test
    public void supportsXhighForGpt5xAndClaude5() {
        // gpt-5.x(x>=2) 系列、gpt-5.1-codex-max、Claude 5 家族 + Opus 4.5+ 支持 xhigh(高强度思考)
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.2"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.2-codex"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.2-pro"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.1-codex-max"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.6"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-sonnet-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-opus-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-fable-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-mythos-5"));
        // Claude Opus 4.5+ 支持 xhigh(4.5 effort to xhigh)
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-opus-4-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-opus-4-6"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-opus-4-8"));
        // 其他模型不支持 xhigh,应降级到 high
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5-pro"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.1"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.1-codex"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("o3"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("claude-opus-4-1"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.2-chat-latest"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh(null));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh(""));
    }

    // ========== supportsMax ==========

    @Test
    public void supportsMaxForGpt56AndClaude5AndOpus46() {
        // gpt-5.6+、Claude 5 家族、Claude Opus 4.6+ 支持 max(最高强度思考)
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("gpt-5.6"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("gpt-5.6-sol"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("gpt-5.6-terra"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-sonnet-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-opus-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-fable-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-mythos-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-opus-4-6"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-opus-4-7"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-opus-4-8"));
        // gpt-5.5/5.2/5.1-codex-max 仅支持到 xhigh,不支持 max
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("gpt-5.5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("gpt-5.2"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("gpt-5.1-codex-max"));
        // Claude Opus 4.5 仅支持到 xhigh,不支持 max
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("claude-opus-4-5"));
        // 其他模型不支持 max
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("gpt-5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("gpt-5-pro"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("o3"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("claude-opus-4-1"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("gpt-5.6-chat-latest"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax(null));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax(""));
    }

    // ========== defaultReasoningEnabledWhenOmitted ==========

    @Test
    public void defaultReasoningEnabledWhenOmittedForGpt51Gpt52IsFalse() {
        // gpt-5.1/5.2 不发 reasoning_effort 时默认 none(非思考)
        assertFalse(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5.1"));
        assertFalse(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5.2"));
        assertFalse(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5.2-codex"));
    }

    @Test
    public void defaultReasoningEnabledWhenOmittedForGpt55Gpt56AndOthersIsTrue() {
        // gpt-5.5/5.6 默认 medium(思考);gpt-5 初代/o系列/kimi/Claude 5/未知模型 不发参数时默认思考模式
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5.5"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5.6"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5-pro"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("o3"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("kimi-k2.6"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("claude-sonnet-5"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("future-model"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted(null));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted(""));
    }
}
