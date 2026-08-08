package cn.lineai.ai.protocol.reasoning;

import cn.lineai.ai.protocol.ReasoningRequestContext;
import cn.lineai.ai.protocol.ReasoningRequestStrategy;
import cn.lineai.model.AiBehaviorSettings;
import org.json.JSONObject;

public final class MoonshotReasoningStrategy implements ReasoningRequestStrategy {
    @Override
    public boolean matches(String baseUrl, String modelId) {
        return baseUrl.contains("moonshot") || baseUrl.contains("kimi") || modelId.contains("kimi")
                || baseUrl.contains("bigmodel") || baseUrl.contains("zhipu") || modelId.contains("glm")
                || baseUrl.contains("mimo") || baseUrl.contains("xiaomi") || modelId.contains("mimo");
    }

    @Override
    public void apply(JSONObject body, ReasoningRequestContext context) throws Exception {
        String base = context.getBaseUrl();
        String model = context.getModelId();
        // kimi-k3 不支持 thinking 字段(传了报错),改用顶层 reasoning_effort 配置推理强度;
        // 且 reasoning_effort 仅接受 low/high/max,medium/auto 必须归一化到 max(k3 默认值),否则请求被拒。
        // 来源:platform.kimi.com/docs/api/models-overview、lobehub PR #17272
        if (isKimiK3(model)) {
            if (context.isEnabled()) {
                body.put("reasoning_effort", kimiK3Effort(context.getEffort()));
            }
            // k3 始终思考、Preserved Thinking 始终开启,无需也不应发 thinking 字段
            return;
        }

        JSONObject thinking = new JSONObject().put("type", context.isEnabled() ? "enabled" : "disabled");
        if (context.isPreserveReasoning() && isMoonshotKimi(base, model)) {
            thinking.put("keep", "all");
        }
        body.put("thinking", thinking);
        if (context.isPreserveReasoning() && isGlm(base, model)) {
            body.put("clear_thinking", false);
        }
    }

    private static boolean isKimiK3(String model) {
        return model != null && model.contains("kimi-k3");
    }

    private static boolean isMoonshotKimi(String base, String model) {
        return base.contains("moonshot") || base.contains("kimi") || model.contains("kimi");
    }

    private static boolean isGlm(String base, String model) {
        return base.contains("bigmodel") || base.contains("zhipu") || model.contains("glm");
    }

    /**
     * kimi-k3 的 reasoning_effort 仅接受 low/high/max;medium(含 auto 归一化结果)映射到 max(k3 默认值)。
     * low/high/max 原样保留。来源:kimi 官方文档 + lobehub #17272(medium 会被 k3 拒绝)。
     */
    private static String kimiK3Effort(String effort) {
        if (AiBehaviorSettings.REASONING_LOW.equals(effort)
                || AiBehaviorSettings.REASONING_HIGH.equals(effort)
                || AiBehaviorSettings.REASONING_MAX.equals(effort)) {
            return effort;
        }
        return AiBehaviorSettings.REASONING_MAX;
    }
}
