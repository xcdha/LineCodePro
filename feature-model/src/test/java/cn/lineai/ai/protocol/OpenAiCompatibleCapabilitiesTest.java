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
    public void knownHardTemperatureReturnsOneForKimiReasoningModels() {
        // Moonshot / Kimi 推理模型 temperature 固定 1.0
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k3"));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.7-code"));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.7-code-highspeed"));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.6"));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.5"));
    }

    @Test
    public void knownHardTemperatureReturnsOneForOpenAiReasoningModels() {
        // OpenAI o 系列与 gpt-5 系列推理模型只接受 temperature=1
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o1"));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o1-mini"));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o3"));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o3-mini"));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o4-mini"));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5"));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-mini"));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1-codex"));
    }

    @Test
    public void knownHardTemperatureReturnsNullForFlexibleOrSpecialModels() {
        // 支持灵活温度的普通模型、非推理 chat-latest 变体、必须省略 temperature 的 search 变体均不在硬性表
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-4o"));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-chat-latest"));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2-chat-latest"));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-search-api"));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("deepseek-v4-flash"));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("glm-4.6"));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("qwen3-coder"));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature(null));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature(""));
    }
}
