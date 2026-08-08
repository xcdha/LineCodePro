package cn.lineai.ai.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import org.json.JSONObject;
import org.junit.Test;

/**
 * 验证未知模型的 reasoning_effort 运行时自适应:六档逐档降级 + 用户调整优先。
 */
public final class ReasoningEffortAutoAdaptTest {

    private static ModelConfig unknownModel() {
        return ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                "https://api.unknown.ai/v1", "k", "future-unknown-model").build();
    }

    private static ModelConfig unknownModel(String modelId) {
        return ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                "https://api.unknown.ai/v1", "k", modelId).build();
    }

    private static JSONObject bodyFor(ModelConfig config, String effort) throws Exception {
        return new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                config, new ModelRequestOptions(effort, false));
    }

    @Test
    public void cacheStartsEmpty() {
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        assertFalse(OpenAiCompatibleProtocol.ReasoningEffortCache.isRejected("future-unknown-model", "max"));
        assertFalse(OpenAiCompatibleProtocol.ReasoningEffortCache.isRejected("future-unknown-model", "high"));
        assertFalse(OpenAiCompatibleProtocol.ReasoningEffortCache.isFullyDisabled("future-unknown-model"));
    }

    // ========== 用户调整优先 ==========

    @Test
    public void userEffortUsedWhenNotRejected() throws Exception {
        // 无缓存时,用户选任意档直接用(用户调整优先)
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            ModelConfig config = unknownModel();
            // 六档全覆盖(auto 归一化为 medium)
            assertEquals("max", bodyFor(config, AiBehaviorSettings.REASONING_MAX).getString("reasoning_effort"));
            assertEquals("high", bodyFor(config, AiBehaviorSettings.REASONING_HIGH).getString("reasoning_effort"));
            assertEquals("medium", bodyFor(config, AiBehaviorSettings.REASONING_MEDIUM).getString("reasoning_effort"));
            assertEquals("medium", bodyFor(config, AiBehaviorSettings.REASONING_AUTO).getString("reasoning_effort"));
            assertEquals("low", bodyFor(config, AiBehaviorSettings.REASONING_LOW).getString("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @Test
    public void userOffOmitsReasoningEffort() throws Exception {
        // 用户选 off,不发 reasoning_effort(思考关闭)
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            ModelConfig config = unknownModel();
            JSONObject body = bodyFor(config, AiBehaviorSettings.REASONING_OFF);
            assertFalse(body.has("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    // ========== 逐档降级 ==========

    @Test
    public void downgradeFromMaxToHigh() throws Exception {
        // max 被拒,降到 high
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "max");
            ModelConfig config = unknownModel();
            assertEquals("high", bodyFor(config, AiBehaviorSettings.REASONING_MAX).getString("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @Test
    public void downgradeFromMaxToMedium() throws Exception {
        // max+high 被拒,降到 medium
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "max");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "high");
            ModelConfig config = unknownModel();
            assertEquals("medium", bodyFor(config, AiBehaviorSettings.REASONING_MAX).getString("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @Test
    public void downgradeFromMaxToLow() throws Exception {
        // max+high+medium 被拒,降到 low
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "max");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "high");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "medium");
            ModelConfig config = unknownModel();
            assertEquals("low", bodyFor(config, AiBehaviorSettings.REASONING_MAX).getString("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @Test
    public void fullyDisabledOmitsReasoningEffort() throws Exception {
        // 所有档位都被拒,完全不发 reasoning_effort
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "max");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "high");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "medium");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "low");
            assertTrue(OpenAiCompatibleProtocol.ReasoningEffortCache.isFullyDisabled("future-unknown-model"));

            ModelConfig config = unknownModel();
            JSONObject body = bodyFor(config, AiBehaviorSettings.REASONING_MAX);
            assertFalse(body.has("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    // ========== 用户调整不被锁死 ==========

    @Test
    public void userSelectsHighNotAffectedByMaxRejection() throws Exception {
        // max 被拒,但用户切到 high(未被拒),直接用 high(不被 max 的拒绝影响)
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "max");
            ModelConfig config = unknownModel();
            assertEquals("high", bodyFor(config, AiBehaviorSettings.REASONING_HIGH).getString("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @Test
    public void userSelectsLowNotAffectedByHighRejection() throws Exception {
        // high 被拒,但用户切到 low(未被拒),直接用 low
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "high");
            ModelConfig config = unknownModel();
            assertEquals("low", bodyFor(config, AiBehaviorSettings.REASONING_LOW).getString("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @Test
    public void fullyDisabledModelUserSelectsLowStillOmits() throws Exception {
        // 全部被拒的模型,用户选 low 仍不发(模型确实不支持 reasoning_effort)
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "max");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "high");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "medium");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "low");
            ModelConfig config = unknownModel();
            JSONObject body = bodyFor(config, AiBehaviorSettings.REASONING_LOW);
            assertFalse(body.has("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @Test
    public void resetAllowsReprobe() throws Exception {
        // reset 后重新探测:之前被拒的档位可再次尝试(模型可能更新支持了)
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("future-unknown-model", "max");
            assertTrue(OpenAiCompatibleProtocol.ReasoningEffortCache.isRejected("future-unknown-model", "max"));

            OpenAiCompatibleProtocol.ReasoningEffortCache.reset("future-unknown-model");
            assertFalse(OpenAiCompatibleProtocol.ReasoningEffortCache.isRejected("future-unknown-model", "max"));

            ModelConfig config = unknownModel();
            assertEquals("max", bodyFor(config, AiBehaviorSettings.REASONING_MAX).getString("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    // ========== 缓存按模型隔离 ==========

    @Test
    public void cacheIsolatedPerModel() throws Exception {
        // A 模型 max 被拒不影响 B 模型
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("model-a", "max");
            ModelConfig configB = unknownModel("model-b");
            assertEquals("max", bodyFor(configB, AiBehaviorSettings.REASONING_MAX).getString("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    // ========== resolveEffort 边界 ==========

    @Test
    public void resolveEffortReturnsRequestedWhenNotRejected() {
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        try {
            assertEquals("max", OpenAiCompatibleProtocol.ReasoningEffortCache.resolveEffort("m", "max"));
            assertEquals("high", OpenAiCompatibleProtocol.ReasoningEffortCache.resolveEffort("m", "high"));
            assertEquals("medium", OpenAiCompatibleProtocol.ReasoningEffortCache.resolveEffort("m", "medium"));
            assertEquals("low", OpenAiCompatibleProtocol.ReasoningEffortCache.resolveEffort("m", "low"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        }
    }

    @Test
    public void resolveEffortReturnsNullWhenAllRejected() {
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        try {
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("m", "max");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("m", "high");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("m", "medium");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("m", "low");
            assertNull(OpenAiCompatibleProtocol.ReasoningEffortCache.resolveEffort("m", "max"));
            assertNull(OpenAiCompatibleProtocol.ReasoningEffortCache.resolveEffort("m", "low"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        }
    }
}
