package cn.lineai.ai.protocol;

import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;

public final class OpenAiCompatibleCapabilities {
    private OpenAiCompatibleCapabilities() {
    }

    public static boolean supportsNativeTools(ModelConfig config) {
        return config != null && !isNvidiaCompatibleGateway(config);
    }

    public static boolean supportsReasoningRequestParameters(ModelConfig config) {
        return config != null && !isNvidiaCompatibleGateway(config);
    }

    public static boolean isNvidiaCompatibleGateway(ModelConfig config) {
        if (config == null) {
            return false;
        }
        String base = lower(config.getBaseUrl());
        String provider = lower(config.getProviderLabel());
        return base.contains("integrate.api.nvidia.com")
                || base.contains("api.nvidia.com")
                || provider.contains("nvidia");
    }

    /**
     * 判断模型是否只接受 {@code temperature: 1}（OpenAI o1/o3/o4、GPT-5、Kimi-K3 及 Console Go 网关托管的推理模型）。
     * 这类模型对 temperature 有严格限制，必须显式发送 1，否则上游返回 400。
     */
    public static boolean requiresTemperatureOne(ModelConfig config) {
        if (config == null) {
            return false;
        }
        String base = lower(config.getBaseUrl());
        String provider = lower(config.getProviderLabel());
        String model = lower(ModelContextParser.apiModelId(config));
        return isConsoleGoGateway(base, provider)
                || isReasoningFamily(model);
    }

    private static boolean isConsoleGoGateway(String base, String provider) {
        return base.contains("console.go")
                || base.contains("console-go")
                || base.contains("consolego")
                || base.contains("opencode.ai")
                || provider.contains("console go")
                || provider.contains("console-go")
                || provider.contains("opencode");
    }

    private static boolean isReasoningFamily(String modelId) {
        return modelId.startsWith("o1")
                || modelId.startsWith("o3")
                || modelId.startsWith("o4")
                || modelId.startsWith("gpt-5")
                || modelId.startsWith("kimi-k3")
                || modelId.startsWith("kimi_k3");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }
}
