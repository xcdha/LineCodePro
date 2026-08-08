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
     * <p>gpt-5.2/5.1 支持 {@code reasoning_effort=none}(关闭思考),关闭时允许 temperature/top_p;
     * 思考模式(effort=low/medium/high/xhigh)下则只接受 temperature=1,与非思考模式不同。
     * <p>数据来源:各提供商官方 API 文档 + OpenAI 社区兼容性矩阵(截至 2026-08)。
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

        // OpenAI o 系列:始终推理,temperature 固定 1
        // 来源:platform.openai.com、Azure OpenAI reasoning 文档
        if (m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4")) {
            return 1.0;
        }

        // gpt-5 系列(排除 chat-latest 非推理变体与 search 变体)
        if (m.startsWith("gpt-5") && !m.contains("-chat-latest") && !m.contains("search")) {
            // gpt-5.2/5.1 支持 reasoning_effort=none(关闭思考),关闭时允许 temperature/top_p;
            // 思考模式(effort != none)下只接受 temperature=1。
            // 来源:community.openai.com/t/1371738 脚注¹
            if (supportsNoneEffort(m) && !reasoningEnabled) {
                return null;
            }
            // 其他 gpt-5 变体(gpt-5初代/pro/codex/mini/nano)始终推理,temperature 固定 1
            return 1.0;
        }

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
     * 判断模型是否支持 {@code reasoning_effort=none}(关闭思考)。
     * gpt-5.2/5.1 系列默认 reasoning_effort=none,允许 temperature/top_p;
     * 其他 gpt-5 变体(初代/pro/codex/mini/nano)与 o 系列不支持 none,始终推理。
     * 来源:developers.openai.com gpt-5.1 文档、community.openai.com 兼容性矩阵
     */
    public static boolean supportsNoneEffort(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return false;
        }
        String m = modelId.toLowerCase(java.util.Locale.ROOT);
        // gpt-5.2 系列(排除 chat-latest)支持 none(默认)
        if (m.startsWith("gpt-5.2") && !m.contains("-chat-latest")) return true;
        // gpt-5.1 系列(排除 chat-latest)支持 none(默认)
        if (m.startsWith("gpt-5.1") && !m.contains("-chat-latest")) return true;
        return false;
    }

    /**
     * 判断模型是否支持 {@code reasoning_effort=xhigh}(最高思考强度)。
     * gpt-5.2 系列、gpt-5.1-codex-max 支持 xhigh;其他模型不支持,应降级到 high。
     * 来源:community.openai.com/t/1371738 兼容性矩阵
     */
    public static boolean supportsXhigh(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return false;
        }
        String m = modelId.toLowerCase(java.util.Locale.ROOT);
        // chat-latest 变体只支持 medium,不支持 xhigh
        if (m.contains("-chat-latest")) return false;
        // gpt-5.2 系列支持 xhigh
        if (m.startsWith("gpt-5.2")) return true;
        // gpt-5.1-codex-max 支持 xhigh
        if (m.contains("gpt-5.1") && m.contains("codex-max")) return true;
        return false;
    }

    /**
     * 不发 reasoning_effort/thinking 参数时,模型默认是否处于思考模式。
     * <p>complete 路径不发思考参数,温度需按模型默认模式取值:
     * <ul>
     *   <li>gpt-5.2/5.1:默认 reasoning_effort=none(非思考),允许用户温度</li>
     *   <li>gpt-5 初代/pro/codex/mini/nano:默认 medium/always(思考),温度固定 1</li>
     *   <li>kimi-k2.5/k2.6:不发 thinking 时默认思考模式,温度取思考模式值</li>
     *   <li>o 系列:始终思考,温度固定 1</li>
     *   <li>未知模型:保守取 true(与历史行为一致)</li>
     * </ul>
     * 来源:developers.openai.com、platform.openai.com、platform.kimi.com
     */
    public static boolean defaultReasoningEnabledWhenOmitted(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return true;
        }
        String m = modelId.toLowerCase(java.util.Locale.ROOT);
        // gpt-5.2/5.1 不发 reasoning_effort 时默认 none(非思考)
        if (supportsNoneEffort(m)) return false;
        // 其他模型不发参数时默认思考模式
        return true;
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
