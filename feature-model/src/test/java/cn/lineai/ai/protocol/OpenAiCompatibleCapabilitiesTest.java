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
    public void knownHardTemperatureReturnsOneForOpenAiReasoningModels() {
        // OpenAI o 系列与 gpt-5 系列推理模型只接受 temperature=1,与思考模式无关
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o1", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o1-mini", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o3", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o3-mini", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o4-mini", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-mini", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1-codex", true));
    }

    @Test
    public void knownHardTemperatureReturnsNullForFlexibleOrSpecialModels() {
        // 支持灵活温度的普通模型、非推理 chat-latest 变体、必须省略 temperature 的 search 变体均不在硬性表
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-4o", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-chat-latest", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2-chat-latest", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-search-api", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("deepseek-v4-flash", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("glm-4.6", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("qwen3-coder", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature(null, true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("", false));
    }
}
