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
    public void knownHardTemperatureForGpt52Gpt51DiffersByThinkingMode() {
        // gpt-5.2/5.1 支持 reasoning_effort=none(关闭思考),关闭时允许 temperature(top_p);
        // 思考模式(effort != none)下只接受 temperature=1。
        // 来源:community.openai.com/t/1371738 脚注¹
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2-codex", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2-codex", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1", false));
    }

    @Test
    public void knownHardTemperatureReturnsNullForFlexibleModels() {
        // 支持灵活温度的普通模型、非推理 chat-latest 变体均不在硬性表
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-4o", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-chat-latest", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2-chat-latest", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("deepseek-v4-flash", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("glm-4.6", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("qwen3-coder", true));
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
    public void supportsNoneEffortForGpt52Gpt51Only() {
        // gpt-5.2/5.1 系列支持 reasoning_effort=none(关闭思考)
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.2"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.2-codex"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.1"));
        // chat-latest 变体不支持(只支持 medium)
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.2-chat-latest"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.1-chat-latest"));
        // gpt-5 初代/pro/codex/mini/nano 不支持 none
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5-pro"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5-codex"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("o3"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort(null));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort(""));
    }

    // ========== supportsXhigh ==========

    @Test
    public void supportsXhighForGpt52AndCodexMaxOnly() {
        // gpt-5.2 系列与 gpt-5.1-codex-max 支持 xhigh(最高强度)
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.2"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.2-codex"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.2-pro"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.1-codex-max"));
        // 其他模型不支持 xhigh,应降级到 high
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5-pro"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.1"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.1-codex"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("o3"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.2-chat-latest"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh(null));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh(""));
    }

    // ========== defaultReasoningEnabledWhenOmitted ==========

    @Test
    public void defaultReasoningEnabledWhenOmittedForGpt52Gpt51IsFalse() {
        // gpt-5.2/5.1 不发 reasoning_effort 时默认 none(非思考)
        assertFalse(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5.2"));
        assertFalse(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5.2-codex"));
        assertFalse(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5.1"));
    }

    @Test
    public void defaultReasoningEnabledWhenOmittedForOtherModelsIsTrue() {
        // gpt-5 初代/o系列/kimi/未知模型 不发参数时默认思考模式
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5-pro"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("o3"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("kimi-k2.6"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("future-model"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted(null));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted(""));
    }
}
