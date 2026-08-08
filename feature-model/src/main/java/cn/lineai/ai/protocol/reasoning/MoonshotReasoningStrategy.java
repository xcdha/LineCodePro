package cn.lineai.ai.protocol.reasoning;

import cn.lineai.ai.protocol.OpenAiCompatibleCapabilities;
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
        // GLM-5.2+ 额外支持 reasoning_effort 控制推理深度(接受 max/xhigh/high/medium/low/minimal/none);
        // GLM-4.x 仅支持 thinking 开关,不发 reasoning_effort。
        // 来源:docs.bigmodel.cn/cn/guide/start/concept-param(reasoning_effort 仅 GLM-5.2 及以上支持)
        if (context.isEnabled() && isGlm52Plus(model)) {
            body.put("reasoning_effort", glmEffort(context.getEffort()));
        }
    }

    private static boolean isKimiK3(String model) {
        // 正则容忍第三方改名(前缀/中缀/后缀任意字符),复用 OpenAiCompatibleCapabilities.kimiMajorVersion
        if (model == null) {
            return false;
        }
        String m = OpenAiCompatibleCapabilities.stripProviderPrefix(model);
        return OpenAiCompatibleCapabilities.kimiMajorVersion(m) == 3;
    }

    private static boolean isMoonshotKimi(String base, String model) {
        return base.contains("moonshot") || base.contains("kimi") || model.contains("kimi");
    }

    private static boolean isGlm(String base, String model) {
        return base.contains("bigmodel") || base.contains("zhipu") || model.contains("glm");
    }

    /**
     * GLM-5.2+ 判定:主版本 > 5,或主版本 = 5 且次版本 >= 2。
     * 仅 GLM-5.2 及以上支持 reasoning_effort 参数。
     * 复用 OpenAiCompatibleCapabilities.glmVersion 正则,容忍第三方在 glm 与版本号之间插入任意字符。
     */
    private static boolean isGlm52Plus(String model) {
        if (model == null) {
            return false;
        }
        String m = OpenAiCompatibleCapabilities.stripProviderPrefix(model);
        int[] v = OpenAiCompatibleCapabilities.glmVersion(m);
        return v[0] > 5 || (v[0] == 5 && v[1] >= 2);
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

    /**
     * GLM-5.2+ 的 reasoning_effort 接受 max/xhigh/high/medium/low/minimal/none。
     * 项目 effort 值(low/medium/high/max)均在 GLM 支持范围内,原样映射;
     * auto 已在 context 归一化为 medium。来源:docs.bigmodel.cn reasoning_effort 参数表。
     */
    private static String glmEffort(String effort) {
        return effort;
    }
}
