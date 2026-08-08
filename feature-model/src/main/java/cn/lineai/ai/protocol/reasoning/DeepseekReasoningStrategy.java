package cn.lineai.ai.protocol.reasoning;

import cn.lineai.ai.protocol.ReasoningRequestContext;
import cn.lineai.ai.protocol.ReasoningRequestStrategy;
import cn.lineai.model.AiBehaviorSettings;
import org.json.JSONObject;

public final class DeepseekReasoningStrategy implements ReasoningRequestStrategy {
    @Override
    public boolean matches(String baseUrl, String modelId) {
        return baseUrl.contains("deepseek") || modelId.contains("deepseek");
    }

    @Override
    public void apply(JSONObject body, ReasoningRequestContext context) throws Exception {
        body.put("thinking", new JSONObject().put("type", context.isEnabled() ? "enabled" : "disabled"));
        if (context.isEnabled()) {
            body.put("reasoning_effort", deepseekEffort(context.getEffort()));
        }
    }

    /**
     * DeepSeek 的 reasoning_effort 仅接受 high/max;low/medium(含 auto 归一化结果)会被上游自动映射为 high。
     * 主动映射避免语义偏差,high/max 原样保留。
     * 来源:api-docs.deepseek.com/guides/thinking_mode(low/medium→high,xhigh→max)
     */
    private static String deepseekEffort(String effort) {
        if (AiBehaviorSettings.REASONING_HIGH.equals(effort)
                || AiBehaviorSettings.REASONING_MAX.equals(effort)) {
            return effort;
        }
        return AiBehaviorSettings.REASONING_HIGH;
    }
}
