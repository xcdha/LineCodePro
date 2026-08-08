package cn.lineai.ai.protocol.reasoning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.ai.protocol.ReasoningRequestContext;
import cn.lineai.model.AiBehaviorSettings;
import org.json.JSONObject;
import org.junit.Test;

public final class DefaultReasoningStrategyTest {
    private static ReasoningRequestContext context(boolean enabled, String effort) {
        return new ReasoningRequestContext(enabled, effort, false, "https://api.openai.com/v1", "o3", 0);
    }

    @Test
    public void sendsTopLevelReasoningEffortWhenEnabled() throws Exception {
        // Chat Completions API 用顶层 reasoning_effort,非嵌套 reasoning.effort(后者是 Responses API)
        DefaultReasoningStrategy strategy = new DefaultReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(true, AiBehaviorSettings.REASONING_MEDIUM));

        assertTrue(body.has("reasoning_effort"));
        assertFalse(body.has("reasoning"));
        assertEquals("medium", body.getString("reasoning_effort"));
    }

    @Test
    public void mapsMaxToHighNotXhigh() throws Exception {
        // o系列仅接受 low/medium/high;gpt-5 大部分也不支持 xhigh。
        // max 统一降级到 high,避免选 max 时 o系列/大部分 gpt-5 被拒。
        DefaultReasoningStrategy strategy = new DefaultReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(true, AiBehaviorSettings.REASONING_MAX));

        assertEquals("high", body.getString("reasoning_effort"));
    }

    @Test
    public void preservesLowAndMediumAndHigh() throws Exception {
        DefaultReasoningStrategy strategy = new DefaultReasoningStrategy();

        JSONObject lowBody = new JSONObject();
        strategy.apply(lowBody, context(true, AiBehaviorSettings.REASONING_LOW));
        assertEquals("low", lowBody.getString("reasoning_effort"));

        JSONObject mediumBody = new JSONObject();
        strategy.apply(mediumBody, context(true, AiBehaviorSettings.REASONING_MEDIUM));
        assertEquals("medium", mediumBody.getString("reasoning_effort"));

        JSONObject highBody = new JSONObject();
        strategy.apply(highBody, context(true, AiBehaviorSettings.REASONING_HIGH));
        assertEquals("high", highBody.getString("reasoning_effort"));
    }

    @Test
    public void normalizesAutoToMedium() throws Exception {
        // auto 经 concreteReasoningEffort 归一化为 medium
        DefaultReasoningStrategy strategy = new DefaultReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(true, AiBehaviorSettings.REASONING_AUTO));

        assertEquals("medium", body.getString("reasoning_effort"));
    }

    @Test
    public void omitsReasoningEffortWhenDisabled() throws Exception {
        DefaultReasoningStrategy strategy = new DefaultReasoningStrategy();
        JSONObject body = new JSONObject();
        strategy.apply(body, context(false, AiBehaviorSettings.REASONING_OFF));

        assertFalse(body.has("reasoning_effort"));
        assertFalse(body.has("reasoning"));
    }
}
