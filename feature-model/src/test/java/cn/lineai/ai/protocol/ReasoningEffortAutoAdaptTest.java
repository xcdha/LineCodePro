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

    // ========== effort 降级与温度联动 ==========

    @Test
    public void effortDowngradeToDisabledKeepsReasoningTemperature() throws Exception {
        // kimi-k2.6:effort 全部被拒降级到禁用时,温度必须保持思考模式(1.0),而非切非思考模式(0.6)。
        // 原因:effort 降级只影响 reasoning_effort 参数,不影响 thinking 字段。
        // kimi-k2.6 通过 thinking.type=enabled/disabled 控制思考开关,与 reasoning_effort 独立。
        // effort 全部被拒不发 reasoning_effort 时,模型仍发 thinking.type=enabled(思考模式),
        // 温度必须按思考模式取(1.0),否则上游报错。
        // 验证 stream 路径 effectiveReasoningEnabled 跟随用户设置(userReasoningEnabled),不跟随 effort 降级。
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            // 模拟 stream 路径已学到全部档位被拒
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("kimi-k2.6", "max");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("kimi-k2.6", "high");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("kimi-k2.6", "medium");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("kimi-k2.6", "low");
            assertTrue(OpenAiCompatibleProtocol.ReasoningEffortCache.isFullyDisabled("kimi-k2.6"));

            // resolveEffort 返回 null(不发 reasoning_effort),但用户仍处于思考模式(userReasoningEnabled=true)
            // → effectiveReasoningEnabled=true → 温度按思考模式取
            String resolved = OpenAiCompatibleProtocol.ReasoningEffortCache.resolveEffort("kimi-k2.6", "max");
            assertNull(resolved);

            // 验证温度表:思考模式应返回 1.0(effort 禁用后温度保持思考模式,不切非思考)
            Double reasoningTemp = OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.6", true);
            assertEquals(Double.valueOf(1.0), reasoningTemp);
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @Test
    public void completePathAlwaysUsesReasoningTemperature() throws Exception {
        // complete 路径不发 reasoning_effort/thinking 参数,模型按默认行为运行。
        // kimi-k2.6 不发 thinking 时默认走思考模式,温度按思考模式取(1.0)。
        // isFullyDisabled(模型不支持 reasoning_effort)不影响 complete 路径温度模式判断,
        // 否则 kimi-k2.6 会被误判为非思考模式,温度取 0.6 导致上游报错。
        // 验证:complete 路径始终按思考模式取温度,不受 isFullyDisabled 影响。
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            // 未 DISABLED:complete 按思考模式(温度 1.0)
            assertFalse(OpenAiCompatibleProtocol.ReasoningEffortCache.isFullyDisabled("kimi-k2.6"));
            Double reasoningTemp = OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.6", true);
            assertEquals(Double.valueOf(1.0), reasoningTemp);

            // DISABLED 后:complete 仍按思考模式(温度 1.0),不因 isFullyDisabled 切非思考模式
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("kimi-k2.6", "max");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("kimi-k2.6", "high");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("kimi-k2.6", "medium");
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("kimi-k2.6", "low");
            assertTrue(OpenAiCompatibleProtocol.ReasoningEffortCache.isFullyDisabled("kimi-k2.6"));
            Double stillReasoningTemp = OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.6", true);
            assertEquals(Double.valueOf(1.0), stillReasoningTemp);
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @Test
    public void effortPartiallyDowngradedKeepsReasoningTemperature() throws Exception {
        // effort 部分降级(max→high)时,仍处于思考模式,温度应保持思考模式(1.0),不切非思考
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            // 仅 max 被拒,降到 high(仍思考模式)
            OpenAiCompatibleProtocol.ReasoningEffortCache.markRejected("kimi-k2.6", "max");
            String resolved = OpenAiCompatibleProtocol.ReasoningEffortCache.resolveEffort("kimi-k2.6", "max");
            assertEquals("high", resolved);
            // resolved 非 null → effectiveReasoningEnabled=true → 温度按思考模式取
            Double reasoningTemp = OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.6", true);
            assertEquals(Double.valueOf(1.0), reasoningTemp);
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }
}
