package cn.lineai.ai.protocol;

import static org.junit.Assert.assertFalse;
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
    public void consoleGoReasoningModelsRequireTemperatureOne() {
        ModelConfig consoleGo = ModelConfig.builder(
                "console-go",
                "Console Go",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "Console Go",
                "https://console.go/api/v1",
                "sk-test",
                "o1").build();
        assertTrue(OpenAiCompatibleCapabilities.requiresTemperatureOne(consoleGo));

        ModelConfig openAiReasoning = ModelConfig.builder(
                "o3",
                "OpenAI o3",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "OpenAI",
                "https://api.openai.com/v1",
                "sk-test",
                "o3-mini").build();
        assertTrue(OpenAiCompatibleCapabilities.requiresTemperatureOne(openAiReasoning));

        ModelConfig gpt5 = ModelConfig.builder(
                "gpt-5",
                "OpenAI GPT-5",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "OpenAI",
                "https://api.openai.com/v1",
                "sk-test",
                "gpt-5").build();
        assertTrue(OpenAiCompatibleCapabilities.requiresTemperatureOne(gpt5));

        ModelConfig regular = ModelConfig.builder(
                "qwen",
                "Qwen",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "Qwen",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "sk-test",
                "qwen/qwen3-coder").build();
        assertFalse(OpenAiCompatibleCapabilities.requiresTemperatureOne(regular));
    }
}
