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
            // 项目档位 max 按模型能力逐级映射:
            //   1. 支持 max(gpt-5.6+ / Claude 5 家族)→ 发 max(原生最高强度)
            //   2. 支持 xhigh(gpt-5.2/5.3/5.4/5.5 / gpt-5.1-codex-max)→ 发 xhigh
            //   3. 其他模型(o系列/gpt-5初代/pro/codex/mini/nano/未知)→ 降级到 high 保证全模型兼容
            // 来源:developers.openai.com gpt-5.6 model guidance、community.openai.com/t/1371738 兼容性矩阵
            if (AiBehaviorSettings.REASONING_MAX.equals(effort)) {
                if (OpenAiCompatibleCapabilities.supportsMax(context.getModelId())) {
                    effort = AiBehaviorSettings.REASONING_MAX;
                } else if (OpenAiCompatibleCapabilities.supportsXhigh(context.getModelId())) {
                    effort = "xhigh";
                } else {
                    effort = AiBehaviorSettings.REASONING_HIGH;
                }
            }
            body.put("reasoning_effort", effort);
        }
    }
}
