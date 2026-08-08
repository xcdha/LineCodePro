package cn.lineai.ai.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import org.json.JSONObject;
import org.junit.Test;

/**
 * 验证未知模型的 reasoning_effort 运行时自适应:三级降级缓存 + 错误驱动重试。
 */
public final class ReasoningEffortAutoAdaptTest {

    private static ModelConfig unknownModel() {
        return ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                "https://api.unknown.ai/v1", "k", "future-unknown-model").build();
    }

    @Test
    public void cacheStartsEmpty() {
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        assertNull(OpenAiCompatibleProtocol.ReasoningEffortCache.get("future-unknown-model"));
        assertFalse(OpenAiCompatibleProtocol.ReasoningEffortCache.isDisabled("future-unknown-model"));
        assertFalse(OpenAiCompatibleProtocol.ReasoningEffortCache.isHighOnly("future-unknown-model"));
    }

    @Test
    public void upgradeRestrictionFromNormalToHighOnly() {
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        try {
            // 第一次升级:正常→HIGH_ONLY
            boolean upgraded = OpenAiCompatibleProtocol.ReasoningEffortCache.upgradeRestriction("future-model-a");
            assertTrue(upgraded);
            assertTrue(OpenAiCompatibleProtocol.ReasoningEffortCache.isHighOnly("future-model-a"));
            assertFalse(OpenAiCompatibleProtocol.ReasoningEffortCache.isDisabled("future-model-a"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        }
    }

    @Test
    public void upgradeRestrictionFromHighOnlyToDisabled() {
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.upgradeRestriction("future-model-b");
            // 第二次升级:HIGH_ONLY→DISABLED
            boolean upgraded = OpenAiCompatibleProtocol.ReasoningEffortCache.upgradeRestriction("future-model-b");
            assertTrue(upgraded);
            assertTrue(OpenAiCompatibleProtocol.ReasoningEffortCache.isDisabled("future-model-b"));
            assertFalse(OpenAiCompatibleProtocol.ReasoningEffortCache.isHighOnly("future-model-b"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        }
    }

    @Test
    public void upgradeRestrictionReturnsFalseAtDisabled() {
        // 已到 DISABLED,无法再降级
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.upgradeRestriction("future-model-c");
            OpenAiCompatibleProtocol.ReasoningEffortCache.upgradeRestriction("future-model-c");
            boolean upgraded = OpenAiCompatibleProtocol.ReasoningEffortCache.upgradeRestriction("future-model-c");
            assertFalse(upgraded);
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        }
    }

    @Test
    public void highOnlyCacheDowngradesMaxToHigh() throws Exception {
        // 缓存标记 HIGH_ONLY 的未知模型,applyReasoningRequest 把 effort 降级到 high
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.upgradeRestriction("future-unknown-model");
            ModelConfig config = unknownModel();
            JSONObject body = new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                    config, new cn.lineai.ai.ModelRequestOptions(AiBehaviorSettings.REASONING_MAX, false));

            assertEquals("high", body.getString("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @Test
    public void disabledCacheOmitsReasoningEffort() throws Exception {
        // 缓存标记 DISABLED 的未知模型,applyReasoningRequest 完全不发 reasoning_effort
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.upgradeRestriction("future-unknown-model");
            OpenAiCompatibleProtocol.ReasoningEffortCache.upgradeRestriction("future-unknown-model");
            ModelConfig config = unknownModel();
            JSONObject body = new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                    config, new cn.lineai.ai.ModelRequestOptions(AiBehaviorSettings.REASONING_HIGH, false));

            assertFalse(body.has("reasoning_effort"));
            assertFalse(body.has("thinking"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @Test
    public void unknownModelSendsReasoningEffortWhenNoCache() throws Exception {
        // 无缓存的未知模型,正常发送 reasoning_effort(DefaultReasoningStrategy 已把 max→high 保守降级)
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            ModelConfig config = unknownModel();
            JSONObject body = new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                    config, new cn.lineai.ai.ModelRequestOptions(AiBehaviorSettings.REASONING_HIGH, false));

            assertEquals("high", body.getString("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }
}
