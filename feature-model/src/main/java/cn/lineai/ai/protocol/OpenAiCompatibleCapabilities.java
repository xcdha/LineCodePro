package cn.lineai.ai.protocol;

import cn.lineai.model.ModelConfig;

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
     * 内置硬性温度表:返回模型对 {@code temperature} 参数的硬性要求值(只接受该值,传其他值上游会报错)。
     * 返回 {@code null} 表示该模型无已知硬性要求,温度交由运行时自动重试 + 缓存发现,或使用上游默认值。
     * 返回 {@link #TEMPERATURE_MUST_OMIT} 表示该模型必须省略 temperature 字段(传任何值都报错)。
     * <p>{@code reasoningEnabled} 表示本次请求是否启用思考模式:部分模型(如 kimi-k2.5/k2.6)
     * 在思考与非思考模式下温度硬性要求不同,必须按实际模式取值,否则上游报错。
     * <p>数据来源:各提供商官方 API 文档(截至 2026-08)。
     */
    public static Double knownHardTemperature(String modelId, boolean reasoningEnabled) {
        if (modelId == null || modelId.isEmpty()) {
            return null;
        }
        String m = modelId.toLowerCase(java.util.Locale.ROOT);

        // Moonshot / Kimi 推理模型
        // 来源:platform.kimi.com/docs/api/models-overview
        // kimi-k3:始终推理,temperature 固定 1.0
        if (m.startsWith("kimi-k3")) return 1.0;
        // kimi-k2.7-code(含 highspeed 变体):思考始终开启,temperature 固定 1.0
        if (m.startsWith("kimi-k2.7-code")) return 1.0;
        // kimi-k2.6 / kimi-k2.5:思考模式固定 1.0,非思考模式固定 0.6,传其他值报错
        if (m.startsWith("kimi-k2.6")) return reasoningEnabled ? 1.0 : 0.6;
        if (m.startsWith("kimi-k2.5")) return reasoningEnabled ? 1.0 : 0.6;

        // OpenAI o 系列与 gpt-5 系列推理模型:只接受 temperature=1
        // 来源:platform.openai.com、Azure OpenAI reasoning 文档、社区兼容性矩阵
        if (isOpenAiReasoningModel(m)) return 1.0;

        // OpenAI search 变体:必须省略 temperature 字段(传任何值都报错)
        // 来源:platform.openai.com docs、社区兼容性矩阵
        // 例:gpt-5-search-api、gpt-4o-search-preview、gpt-4o-mini-search-preview
        if (isSearchVariant(m)) return TEMPERATURE_MUST_OMIT;

        return null;
    }

    /**
     * 哨兵值:表示模型必须省略 temperature 字段(不接受任何 temperature 值)。
     * 与 {@code null}(无已知硬性要求)区分,避免 search 变体被当作普通模型传温度。
     */
    public static final Double TEMPERATURE_MUST_OMIT = Double.NEGATIVE_INFINITY;

    /**
     * OpenAI 推理模型判定:o1/o3/o4 系列与 gpt-5 系列均只接受 temperature=1。
     * 排除 {@code *-chat-latest} 变体(非推理,支持灵活温度,如 gpt-5-chat-latest、gpt-5.2-chat-latest)
     * 与 search 变体(必须省略 temperature,如 gpt-5-search-api)。
     */
    private static boolean isOpenAiReasoningModel(String m) {
        if (m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4")) {
            return true;
        }
        if (m.startsWith("gpt-5") && !m.contains("-chat-latest") && !m.contains("search")) {
            return true;
        }
        return false;
    }

    /**
     * search 变体判定:必须省略 temperature 字段。
     * 覆盖 OpenAI search-preview / search-api 系列与 gpt-4o search 变体。
     * 来源:platform.openai.com docs、社区兼容性矩阵
     */
    private static boolean isSearchVariant(String m) {
        return m.contains("search");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }
}
