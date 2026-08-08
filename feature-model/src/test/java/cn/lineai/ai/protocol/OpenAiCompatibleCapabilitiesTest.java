package cn.lineai.ai.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import org.junit.Test;

public final class OpenAiCompatibleCapabilitiesTest {
    @Test
    public void nvidiaGatewayDisablesNativeToolsAndReasoningParameters() {
        ModelConfig config = ModelConfig.builder(
                "nvidia",
                "NVIDIA DeepSeek",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "NVIDIA",
                "https://integrate.api.nvidia.com/v1",
                "sk-test",
                "deepseek-ai/deepseek-v4-pro").build();

        assertFalse(OpenAiCompatibleCapabilities.supportsNativeTools(config));
        assertFalse(OpenAiCompatibleCapabilities.supportsReasoningRequestParameters(config));
    }

    @Test
    public void regularOpenAiCompatibleProviderKeepsNativeToolsAndReasoningParameters() {
        ModelConfig config = ModelConfig.builder(
                "qwen",
                "Qwen",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "Qwen",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "sk-test",
                "qwen/qwen3-coder").build();

        assertTrue(OpenAiCompatibleCapabilities.supportsNativeTools(config));
        assertTrue(OpenAiCompatibleCapabilities.supportsReasoningRequestParameters(config));
    }

    @Test
    public void knownHardTemperatureReturnsOneForKimiAlwaysThinkingModels() {
        // kimi-k3 / kimi-k2.7-code 始终思考,temperature 固定 1.0,与 reasoningEnabled 无关
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k3", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k3", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.7-code", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.7-code", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.7-code-highspeed", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.7-code-highspeed", false));
    }

    @Test
    public void knownHardTemperatureDiffersByThinkingModeForKimiK2x() {
        // kimi-k2.6 / kimi-k2.5:思考模式固定 1.0,非思考模式固定 0.6
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.6", true));
        assertEquals(Double.valueOf(0.6), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.6", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.5", true));
        assertEquals(Double.valueOf(0.6), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k2.5", false));
    }

    @Test
    public void knownHardTemperatureForOpenAiOSeriesFixedOneAndGpt5NonReasoningMustOmit() {
        // o 系列:官方唯一接受 temperature=1(与思考模式无关)
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o1", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o1-mini", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o3", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o3-mini", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o4-mini", true));

        // gpt-5 初代/mini/nano/pro/codex:不支持 none,永远不接受 temperature → 必须省略
        // 来源:community.openai.com/t/1371738 兼容性矩阵、braintrust KB
        Double omit = OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT;
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5", true));
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5", false));
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-mini", false));
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-nano", true));
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-pro", true));
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-codex", true));
        // gpt-5.1-codex-max / gpt-5.2-pro:不支持 none,非思考模式也必须省略
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1-codex-max", false));
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2-pro", false));
    }

    @Test
    public void knownHardTemperatureForGpt5xMatrixFromOpenAI() {
        // GPT-5.x 精确矩阵(来源:community.openai.com/t/1371738 兼容性矩阵、braintrust KB):
        //   思考模式(effort != none):全系列不接受 temperature → MUST_OMIT
        //   非思考模式(effort=none):gpt-5.x(x>=1) 接受用户温度 → null
        Double omit = OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT;
        // gpt-5.1 家族(支持 none)
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1", false));
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1-codex", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1-codex", false));
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1-codex-mini", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.1-codex-mini", false));
        // gpt-5.2 家族(支持 none)
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2", false));
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2-codex", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2-codex", false));
        // gpt-5.5 / 5.6(支持 none)
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.5", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.5", false));
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.6", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.6", false));
        assertEquals(omit, OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.6-sol", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.6-sol", false));
    }

    @Test
    public void knownHardTemperatureReturnsMustOmitForClaude5FamilyAndOpus47() {
        // Claude 5 家族 + Opus 4.7/4.8:temperature/top_p/top_k 已移除,传非默认值返回 400。
        // 来源:platform.claude.com whats-new-sonnet-5、migration-guide
        // 覆盖:claude-sonnet-5、claude-opus-5、claude-haiku-5、claude-fable-5、claude-mythos-5
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-sonnet-5", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-sonnet-5", false));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-opus-5", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-haiku-5", false));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-fable-5", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-mythos-5", true));
        // Opus 4.7 / 4.8 同样移除了采样参数
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-opus-4-7", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-opus-4-8", false));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-opus-4.7", true));
    }

    @Test
    public void knownHardTemperatureReturnsNullForFlexibleModels() {
        // 支持灵活温度的普通模型、非推理 chat-latest 变体、DeepSeek V4 Flash、GLM-5.2、Qwen3.8-max 均不在硬性表
        // DeepSeek V4 Flash:thinking 模式下 temperature 无效但不报错(非硬性要求)
        // GLM-5.2:temperature 0-1 默认 1.0(非硬性要求)
        // Qwen3.8-max:思考模式默认 0.6,<0.6 自动夹紧不报错(非硬性要求)
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-4o", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-chat-latest", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5.2-chat-latest", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("deepseek-v4-flash", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("deepseek-v4-pro", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("glm-4.6", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("glm-5.2", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("glm-5.3", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("qwen3.8-max", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("qwen3-coder", true));
        // Claude 3.x 及 Opus 4.6 仍接受 temperature(0.0-1.0)
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("claude-3-7-sonnet", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("claude-opus-4-6", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature(null, true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("", false));
    }

    @Test
    public void knownHardTemperatureReturnsMustOmitForSearchVariants() {
        // search 变体必须省略 temperature 字段(传任何值都报错),返回 TEMPERATURE_MUST_OMIT 哨兵值
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-search-api", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("gpt-4o-search-preview", false));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("gpt-4o-mini-search-preview", true));
    }

    // ========== supportsNoneEffort ==========

    @Test
    public void supportsNoneEffortForGpt5xSeries() {
        // gpt-5.x(x>=1) 系列支持 reasoning_effort=none(关闭思考)
        // 覆盖:gpt-5.1 / 5.2 / 5.5 / 5.6
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.1"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.2"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.2-codex"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.6"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.6-sol"));
        // chat-latest 变体不支持(只支持 medium)
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.2-chat-latest"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.6-chat-latest"));
        // gpt-5 初代/pro/codex/mini/nano 不支持 none
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5-pro"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5-codex"));
        // gpt-5.1-codex-max / gpt-5.2-pro 虽小版本>=1,但官方矩阵标注不支持 none
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.1-codex-max"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-5.2-pro"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("o3"));
        // Claude 5 不支持 none(用 thinking.type=disabled 关闭思考,而非 reasoning_effort=none)
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("claude-sonnet-5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort(null));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort(""));
    }

    // ========== supportsXhigh ==========

    @Test
    public void supportsXhighForGpt5xAndClaude5() {
        // gpt-5.x(x>=2) 系列、gpt-5.1-codex-max、Claude 5 家族 + Opus 4.5+ 支持 xhigh(高强度思考)
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.2"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.2-codex"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.2-pro"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.1-codex-max"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.6"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-sonnet-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-opus-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-fable-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-mythos-5"));
        // Claude Opus 4.5+ 支持 xhigh(4.5 effort to xhigh)
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-opus-4-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-opus-4-6"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-opus-4-8"));
        // 其他模型不支持 xhigh,应降级到 high
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5-pro"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.1"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.1-codex"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("o3"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("claude-opus-4-1"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("gpt-5.2-chat-latest"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh(null));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh(""));
    }

    // ========== supportsMax ==========

    @Test
    public void supportsMaxForGpt56AndClaude5AndOpus46() {
        // gpt-5.6+、Claude 5 家族、Claude Opus 4.6+ 支持 max(最高强度思考)
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("gpt-5.6"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("gpt-5.6-sol"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("gpt-5.6-terra"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-sonnet-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-opus-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-fable-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-mythos-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-opus-4-6"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-opus-4-7"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("claude-opus-4-8"));
        // gpt-5.5/5.2/5.1-codex-max 仅支持到 xhigh,不支持 max
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("gpt-5.5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("gpt-5.2"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("gpt-5.1-codex-max"));
        // Claude Opus 4.5 仅支持到 xhigh,不支持 max
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("claude-opus-4-5"));
        // 其他模型不支持 max
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("gpt-5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("gpt-5-pro"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("o3"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("claude-opus-4-1"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("gpt-5.6-chat-latest"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax(null));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax(""));
    }

    // ========== defaultReasoningEnabledWhenOmitted ==========

    @Test
    public void defaultReasoningEnabledWhenOmittedForGpt51Gpt52IsFalse() {
        // gpt-5.1/5.2 不发 reasoning_effort 时默认 none(非思考)
        assertFalse(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5.1"));
        assertFalse(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5.2"));
        assertFalse(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5.2-codex"));
    }

    @Test
    public void defaultReasoningEnabledWhenOmittedForGpt55Gpt56AndOthersIsTrue() {
        // gpt-5.5/5.6 默认 medium(思考);gpt-5 初代/o系列/kimi/Claude 5/未知模型 不发参数时默认思考模式
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5.5"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5.6"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("gpt-5-pro"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("o3"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("kimi-k2.6"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("claude-sonnet-5"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("future-model"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted(null));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted(""));
    }

    // ========== 第三方网关 provider 前缀剥离 ==========

    @Test
    public void stripProviderPrefixHandlesGatewayNaming() {
        // 第三方网关(OpenRouter / SiliconFlow / 聚合代理)常用 "provider/model-id" 命名
        assertEquals("gpt-5.5", OpenAiCompatibleCapabilities.stripProviderPrefix("openai/gpt-5.5"));
        assertEquals("gpt-5.5", OpenAiCompatibleCapabilities.stripProviderPrefix("OpenAI/GPT-5.5"));
        assertEquals("kimi-k3", OpenAiCompatibleCapabilities.stripProviderPrefix("moonshot/kimi-k3"));
        assertEquals("claude-sonnet-5", OpenAiCompatibleCapabilities.stripProviderPrefix("anthropic/claude-sonnet-5"));
        assertEquals("deepseek-v4-flash", OpenAiCompatibleCapabilities.stripProviderPrefix("deepseek-ai/deepseek-v4-flash"));
        assertEquals("glm-5.2", OpenAiCompatibleCapabilities.stripProviderPrefix("zhipuai/glm-5.2"));
        assertEquals("qwen3.8-max", OpenAiCompatibleCapabilities.stripProviderPrefix("alibaba/qwen3.8-max"));
        // 无前缀原样返回(仅小写)
        assertEquals("gpt-5.5", OpenAiCompatibleCapabilities.stripProviderPrefix("gpt-5.5"));
        // 多段前缀只剥离最后一段:/openai/azure/gpt-5.6 → gpt-5.6
        assertEquals("gpt-5.6", OpenAiCompatibleCapabilities.stripProviderPrefix("openai/azure/gpt-5.6"));
        // 尾部斜杠不剥离(避免返回空)
        assertEquals("openai/", OpenAiCompatibleCapabilities.stripProviderPrefix("openai/"));
        assertEquals("", OpenAiCompatibleCapabilities.stripProviderPrefix(""));
    }

    @Test
    public void knownHardTemperatureMatchesAfterStrippingProviderPrefix() {
        // 带前缀的模型名也能命中内置温度表(核心:避免第三方网关下温度丢失)
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("openai/gpt-5.5", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("openai/gpt-5.6", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("openai/gpt-5.5", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("moonshot/kimi-k3", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("moonshot/kimi-k3", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("moonshot/kimi-k2.6", true));
        assertEquals(Double.valueOf(0.6), OpenAiCompatibleCapabilities.knownHardTemperature("moonshot/kimi-k2.6", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("openai/o3", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("openai/o3-mini", false));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("anthropic/claude-sonnet-5", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("anthropic/claude-opus-5", false));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("anthropic/claude-opus-4-7", true));
        // 灵活温度模型带前缀仍返回 null
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("deepseek-ai/deepseek-v4-flash", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("zhipuai/glm-5.2", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("zhipuai/glm-5.3", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("alibaba/qwen3.8-max", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("openai/gpt-4o", false));
        // search 变体带前缀仍 MUST_OMIT
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("openai/gpt-5-search-api", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("openai/gpt-4o-search-preview", false));
    }

    @Test
    public void effortCapabilitiesMatchAfterStrippingProviderPrefix() {
        // 带前缀的模型名也能命中 effort 档位判定
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("openai/gpt-5.1"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("openai/gpt-5.6"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("openai/gpt-5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("openai/gpt-5-pro"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("openai/gpt-5.2"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("openai/gpt-5.5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("anthropic/claude-sonnet-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("anthropic/claude-opus-4-5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsXhigh("openai/gpt-5.1"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("openai/gpt-5.6"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("anthropic/claude-sonnet-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("anthropic/claude-opus-4-6"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("openai/gpt-5.5"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("anthropic/claude-opus-4-5"));
        // defaultReasoningEnabledWhenOmitted 带前缀
        assertFalse(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("openai/gpt-5.1"));
        assertFalse(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("openai/gpt-5.2"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("openai/gpt-5.5"));
        assertTrue(OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted("openai/gpt-5.6"));
    }

    // ========== search / o 系列防误判 ==========

    @Test
    public void searchVariantTightMatchAvoidsFalsePositives() {
        // 已知 search 变体仍 MUST_OMIT
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("gpt-5-search-api", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("gpt-4o-search-preview", false));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("gpt-4o-mini-search-preview", true));
        // research / perplexity-search / 普通模型不应被误判为 search 变体
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("research-1.0", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("perplexity-search", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("sonar-research", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-4o", false));
    }

    @Test
    public void openAiOSeriesTightMatchAvoidsFalsePositives() {
        // o1 / o3 / o4 及变体仍固定 1.0
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o1", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o1-mini", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o1-preview", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o3", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o3-mini", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("o4-mini", false));
        // o1bak / qwen-o1 / o123 / o11 不应被误判为 o 系列
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("o1bak", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("qwen-o1", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("o123", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("o11", false));
    }

    // ========== 第三方网关改名(任意位置任意字符干扰) ==========

    @Test
    public void kimiToleratesArbitraryCharInterference() {
        // 干扰字符(x)数量/位置不固定:前缀/中缀/后缀/多段叠加,品牌词 kimi 与版本号 k3 之间任意字符
        // kimi-k3 始终推理,温度固定 1.0
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("abc-kimi-xx-k3", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-def-k3-v2", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("vendor-kimi-xyz-k3-pro", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("xkimixk3", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("akimi-bk3c", false));
        // provider 前缀 + 中缀干扰叠加
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("moonshot/abc-kimi-xx-k3", true));
        // kimi-k2.6 / k2.5 中缀干扰仍按思考/非思考区分温度
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("abc-kimi-xx-k2.6", true));
        assertEquals(Double.valueOf(0.6), OpenAiCompatibleCapabilities.knownHardTemperature("abc-kimi-xx-k2.6", false));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-def-k2.5-v2", true));
        assertEquals(Double.valueOf(0.6), OpenAiCompatibleCapabilities.knownHardTemperature("kimi-def-k2.5-v2", false));
        // kimi-k2.7 中缀干扰仍固定 1.0
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("abc-kimi-xx-k2.7-code", true));
        // k30 不应被误判为 k3(版本号边界保护)
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("kimi-k30", true));
    }

    @Test
    public void gpt5ToleratesArbitraryCharInterference() {
        // gpt-5.x 中缀干扰:abc-gpt-xx-5.5 / gpt-def-5.6-v2 等
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("abc-gpt-xx-5.5", true));
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("gpt-def-5.6-v2", true));
        // 非思考模式 gpt-5.x(x>=1) 允许用户温度
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("abc-gpt-xx-5.5", false));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-def-5.6-v2", false));
        // effort 档位判定也容忍中缀干扰
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("abc-gpt-xx-5.1"));
        assertTrue(OpenAiCompatibleCapabilities.supportsNoneEffort("abc-gpt-xx-5.6"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("abc-gpt-xx-5.5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("abc-gpt-xx-5.6"));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("abc-gpt-xx-5.5"));
        // provider 前缀 + 中缀叠加
        assertEquals(Double.valueOf(1.0), OpenAiCompatibleCapabilities.knownHardTemperature("openai/abc-gpt-xx-5.6", true));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("openai/abc-gpt-xx-5.6"));
        // gpt-4o 不应被误判为 gpt-5
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("gpt-4o", true));
        assertFalse(OpenAiCompatibleCapabilities.supportsNoneEffort("gpt-4o"));
    }

    @Test
    public void claude5ToleratesArbitraryCharInterference() {
        // Claude 5 中缀干扰仍 MUST_OMIT
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("abc-claude-xx-sonnet-5", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-def-opus-5-v2", false));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("anthropic/abc-claude-xx-sonnet-5", true));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("abc-claude-xx-sonnet-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("abc-claude-xx-sonnet-5"));
        // Opus 4.7/4.8 中缀干扰仍 MUST_OMIT
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("abc-claude-xx-opus-4-7", true));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("claude-def-opus-4-8-v2", false));
    }

    @Test
    public void claudeOpus45NotMisjudgedAsClaude5() {
        // 误判防护:claude-opus-4-5(Opus 4.5)不应被当作 Claude 5 家族
        // Opus 4.5 温度灵活(null),支持 xhigh 但不支持 max
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("claude-opus-4-5", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("claude-opus-4-5", false));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("claude-opus-4-5"));
        assertTrue(OpenAiCompatibleCapabilities.supportsXhigh("claude-opus-4-5"));
        // 中缀干扰下仍正确区分 opus-4-5 与 opus-5
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("abc-claude-xx-opus-4-5", true));
        assertFalse(OpenAiCompatibleCapabilities.supportsMax("abc-claude-xx-opus-4-5"));
        assertEquals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT,
                OpenAiCompatibleCapabilities.knownHardTemperature("abc-claude-xx-opus-5", true));
        assertTrue(OpenAiCompatibleCapabilities.supportsMax("abc-claude-xx-opus-5"));
    }

    @Test
    public void glmAndQwenTolerateArbitraryCharInterference() {
        // GLM-5.2/5.3 中缀干扰仍灵活温度(null)
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("abc-glm-xx-5.2", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("glm-def-5.3-v2", false));
        // Qwen3.8-max 中缀干扰仍灵活温度(null)
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("abc-qwen-xx-3.8-max", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("qwen-def-3.8-max-v2", false));
        // provider 前缀 + 中缀叠加
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("zhipuai/abc-glm-xx-5.2", true));
        assertNull(OpenAiCompatibleCapabilities.knownHardTemperature("alibaba/abc-qwen-xx-3.8-max", true));
    }
}
