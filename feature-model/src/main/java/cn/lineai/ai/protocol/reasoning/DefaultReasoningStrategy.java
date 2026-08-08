package cn.lineai.ai.protocol.reasoning;

import cn.lineai.ai.protocol.OpenAiCompatibleCapabilities;
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
            // 项目档位 max 映射到 OpenAI 的 xhigh(最高强度),仅 gpt-5.2 系列/5.1-codex-max 支持;
            // 不支持 xhigh 的模型(o系列/gpt-5初代/pro/codex/mini/nano)降级到 high 保证全模型兼容。
            // 来源:OpenAI 官方兼容性矩阵 community.openai.com/t/1371738
            if (AiBehaviorSettings.REASONING_MAX.equals(effort)) {
                effort = OpenAiCompatibleCapabilities.supportsXhigh(context.getModelId())
                        ? "xhigh"
                        : AiBehaviorSettings.REASONING_HIGH;
            }
            body.put("reasoning_effort", effort);
        }
    }
}
