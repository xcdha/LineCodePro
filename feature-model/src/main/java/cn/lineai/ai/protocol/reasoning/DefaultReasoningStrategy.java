package cn.lineai.ai.protocol.reasoning;

import cn.lineai.ai.protocol.ReasoningRequestContext;
import cn.lineai.ai.protocol.ReasoningRequestStrategy;
import cn.lineai.model.AiBehaviorSettings;
import org.json.JSONObject;

public final class DefaultReasoningStrategy implements ReasoningRequestStrategy {
    @Override
    public boolean matches(String baseUrl, String modelId) {
        return true;
    }

    @Override
    public void apply(JSONObject body, ReasoningRequestContext context) throws Exception {
        if (context.isEnabled()) {
            String effort = AiBehaviorSettings.concreteReasoningEffort(context.getEffort());
            // OpenAI Chat Completions API 用顶层 reasoning_effort(非嵌套 reasoning.effort)。
            // o系列仅接受 low/medium/high;gpt-5 大部分接受 minimal/low/medium/high,
            // 仅 gpt-5.2+/5.1-codex-max 支持 xhigh。max 统一降级到 high 保证全模型兼容,
            // 避免选 max 时 o系列/大部分 gpt-5 因 xhigh 被拒。
            // 来源:OpenAI 官方兼容性矩阵 community.openai.com/t/1371738
            if (AiBehaviorSettings.REASONING_MAX.equals(effort)) {
                effort = AiBehaviorSettings.REASONING_HIGH;
            }
            body.put("reasoning_effort", effort);
        }
    }
}
