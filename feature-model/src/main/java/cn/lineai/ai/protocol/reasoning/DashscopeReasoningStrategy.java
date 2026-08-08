package cn.lineai.ai.protocol.reasoning;

import cn.lineai.ai.protocol.OpenAiCompatibleCapabilities;
import cn.lineai.ai.protocol.ReasoningRequestContext;
import cn.lineai.ai.protocol.ReasoningRequestStrategy;
import cn.lineai.model.AiBehaviorSettings;
import org.json.JSONObject;

public final class DashscopeReasoningStrategy implements ReasoningRequestStrategy {
    @Override
    public boolean matches(String baseUrl, String modelId) {
        return baseUrl.contains("dashscope") || baseUrl.contains("aliyuncs") || modelId.contains("qwen");
    }

    @Override
    public void apply(JSONObject body, ReasoningRequestContext context) throws Exception {
        String model = context.getModelId();
        // Qwen3.8-Max 及以上(OpenAI 兼容端点)用顶层 reasoning_effort 控制推理强度,
        // 仅接受 xhigh(默认)/medium/low;项目 high/max 映射为 xhigh(Qwen3.8 的最高档),
        // auto 已归一化为 medium。不再发 thinking_budget(reasoning_effort 已涵盖思考强度控制)。
        // 来源:platform.qianwenai.com docs/api-reference/chat/openai-chat、apidog Qwen 3.8 API 指南
        if (isQwen38Plus(model)) {
            body.put("enable_thinking", context.isEnabled());
            if (context.isEnabled()) {
                body.put("reasoning_effort", qwen38Effort(context.getEffort()));
            }
            if (context.isPreserveReasoning()) {
                body.put("preserve_thinking", true);
            }
            return;
        }

        // Qwen3.7 及更早 / Qwen3-Coder 等:用 enable_thinking + thinking_budget 控制思考
        body.put("enable_thinking", context.isEnabled());
        if (context.isEnabled()) {
            body.put("thinking_budget", context.getThinkingBudget());
        }
        if (context.isPreserveReasoning()) {
            body.put("preserve_thinking", true);
        }
    }

    /**
     * Qwen3.8-Max 及以上判定:主版本 > 3,或主版本 = 3 且次版本 >= 8。
     * 仅 Qwen3.8-Max 及以上支持 reasoning_effort 参数(xhigh/medium/low)。
     * 复用 OpenAiCompatibleCapabilities.qwenVersion 正则,容忍第三方在 qwen 与版本号之间插入任意字符;
     * 仅匹配 max 变体(推理模型),coder/instruct 等非 max 变体仍走 thinking_budget 路径。
     * 来源:platform.qianwenai.com(qwen3.8-max 支持 reasoning_effort,thinking_budget 适用 Qwen3.7/3.8)
     */
    private static boolean isQwen38Plus(String model) {
        if (model == null) {
            return false;
        }
        String m = OpenAiCompatibleCapabilities.stripProviderPrefix(model);
        // 必须含 qwen 和 max(仅 max 变体支持 reasoning_effort)
        if (!m.contains("qwen") || !m.contains("max")) {
            return false;
        }
        int[] v = OpenAiCompatibleCapabilities.qwenVersion(m);
        return v[0] > 3 || (v[0] == 3 && v[1] >= 8);
    }

    /**
     * Qwen3.8-Max 的 reasoning_effort 仅接受 xhigh/medium/low。
     * 项目 high/max 映射为 xhigh(Qwen3.8 最高档),low/medium 原样保留,
     * auto 已在 context 归一化为 medium。来源:platform.qianwenai.com reasoning_effort 参数表。
     */
    private static String qwen38Effort(String effort) {
        if (AiBehaviorSettings.REASONING_LOW.equals(effort)
                || AiBehaviorSettings.REASONING_MEDIUM.equals(effort)) {
            return effort;
        }
        // high / max / auto(已归一化为 medium,但保险起见)→ xhigh
        return "xhigh";
    }
}
