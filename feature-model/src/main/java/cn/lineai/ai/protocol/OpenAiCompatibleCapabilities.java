package cn.lineai.ai.protocol;

import cn.lineai.model.ModelConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpenAiCompatibleCapabilities {
    private OpenAiCompatibleCapabilities() {
    }

    // ========== 预编译正则:容忍第三方网关在品牌词与版本号之间插入任意字符 ==========
    // 设计:品牌词(kimi/gpt/claude/glm/qwen)作锚点(contains 前提),再用惰性 .*? 提取版本号,
    // 容忍前缀/中缀/后缀干扰字符(含字母数字),覆盖 abc-kimi-xx-k3 / kimi-def-k2.6-v2 等改名。
    // 误判防护:版本号后用边界 (?=\D|$) 避免 k30 被当作 k3;opus 后取第一个数字区分 4.x 与 5。
    private static final Pattern KIMI_VERSION =
            Pattern.compile("kimi.*?k(\\d+)(?:[.\\-]?(\\d+))?", Pattern.DOTALL);
    private static final Pattern GPT5_MINOR =
            Pattern.compile("gpt.*?5\\.(\\d+)", Pattern.DOTALL);
    private static final Pattern CLAUDE_VARIANT_MAJOR =
            Pattern.compile("claude.*?(sonnet|opus|haiku|fable|mythos).*?(\\d)", Pattern.DOTALL);
    private static final Pattern CLAUDE_OPUS4_MINOR =
            Pattern.compile("claude.*?opus.*?4\\D*(\\d+)", Pattern.DOTALL);
    private static final Pattern GLM_VERSION =
            Pattern.compile("glm\\D*(\\d+)(?:[.\\-](\\d+))?", Pattern.DOTALL);
    private static final Pattern QWEN_VERSION =
            Pattern.compile("qwen\\D*(\\d+)(?:[.\\-](\\d+))?", Pattern.DOTALL);

    public static boolean supportsNativeTools(ModelConfig config) {
        return config != null && !isNvidiaCompatibleGateway(config);
    }

    public static boolean supportsReasoningRequestParameters(ModelConfig config) {
        return config != null && !isNvidiaCompatibleGateway(config);
    }

    public static boolean isNvidiaCompatibleGateway(ModelConfig config) {
        if (config == null) {
            return false;
        }
        String base = lower(config.getBaseUrl());
        String provider = lower(config.getProviderLabel());
        return base.contains("integrate.api.nvidia.com")
                || base.contains("api.nvidia.com")
                || provider.contains("nvidia");
    }

    /**
     * 内置硬性温度表:返回模型对 {@code temperature} 参数的硬性要求值(只接受该值,传其他值上游会报错)。
     * 返回 {@code null} 表示该模型无已知硬性要求,温度交由运行时自动重试 + 缓存发现,或使用上游默认值。
     * 返回 {@link #TEMPERATURE_MUST_OMIT} 表示该模型必须省略 temperature 字段(传任何值都报错)。
     * <p>{@code reasoningEnabled} 表示本次请求是否启用思考模式:部分模型(如 kimi-k2.5/k2.6)
     * 在思考与非思考模式下温度硬性要求不同,必须按实际模式取值,否则上游报错。
     * <p>gpt-5.x(x>=1) 支持 {@code reasoning_effort=none}(关闭思考),关闭时允许 temperature/top_p;
     * 思考模式(effort=low/medium/high/xhigh/max)下则只接受 temperature=1,与非思考模式不同。
     * <p>数据来源:各提供商官方 API 文档(截至 2026-08):
     * <ul>
     *   <li>platform.kimi.com / platform.claude.com / developers.openai.com</li>
     *   <li>docs.bigmodel.cn / api-docs.deepseek.com / platform.qianwenai.com</li>
     * </ul>
     */
    public static Double knownHardTemperature(String modelId, boolean reasoningEnabled) {
        if (modelId == null || modelId.isEmpty()) {
            return null;
        }
        String m = stripProviderPrefix(modelId);

        // Moonshot / Kimi 推理模型
        // 来源:platform.kimi.com/docs/api/models-overview
        // 正则容忍第三方网关在品牌词与版本号之间插入任意字符(含字母数字):
        //   abc-kimi-xx-k3 / kimi-def-k2.6-v2 / xkimik3 等改名均能命中。
        // kimi-k3:始终推理,temperature 固定 1.0
        if (kimiMajorVersion(m) == 3) return 1.0;
        // kimi-k2.7-code(含 highspeed 变体):思考始终开启,temperature 固定 1.0
        if (kimiMajorVersion(m) == 2 && kimiMinorVersion(m) == 7) return 1.0;
        // kimi-k2.6 / kimi-k2.5:思考模式固定 1.0,非思考模式固定 0.6,传其他值报错
        if (kimiMajorVersion(m) == 2 && kimiMinorVersion(m) == 6) return reasoningEnabled ? 1.0 : 0.6;
        if (kimiMajorVersion(m) == 2 && kimiMinorVersion(m) == 5) return reasoningEnabled ? 1.0 : 0.6;

        // OpenAI o 系列:始终推理,temperature 固定 1
        // 收紧匹配:o1 / o3 / o4 后必须跟边界(-/preview/mini 等),避免 o1bak / qwen-o1 误判。
        // 来源:platform.openai.com、Azure OpenAI reasoning 文档
        if (isOpenAiOSeries(m)) {
            return 1.0;
        }

        // gpt-5 系列(排除 chat-latest 非推理变体与 search 变体)
        // temperature 精确矩阵(来源:community.openai.com 兼容性矩阵、braintrust KB、MS agent-framework):
        //   思考模式(effort != none):GPT-5 全系列都不接受 temperature,传任何值官方端点返回 400 → 必须省略。
        //   非思考模式(effort=none):仅 gpt-5.x(x>=1) 接受 temperature/top_p(用户温度生效);
        //     gpt-5 初代/mini/nano/pro/codex-max 不支持 none,永远不接受 temperature → 必须省略。
        // o 系列例外:官方唯一接受 temperature=1(见下方 isOpenAiOSeries 分支,不进此分支)。
        if (isGpt5Series(m)) {
            if (reasoningEnabled) {
                return TEMPERATURE_MUST_OMIT;
            }
            // 非思考模式:仅支持 none 的 gpt-5.x(x>=1) 接受用户温度
            return supportsNoneEffort(m) ? null : TEMPERATURE_MUST_OMIT;
        }

        // Claude 5 家族 + Opus 4.7/4.8:temperature/top_p/top_k 已移除,传非默认值返回 400。
        // 覆盖:claude-sonnet-5、claude-opus-5、claude-haiku-5、claude-fable-5、claude-mythos-5,
        // 以及 claude-opus-4-7 / claude-opus-4-8(同样移除了采样参数)。
        // 来源:platform.claude.com docs/about-claude/models/whats-new-sonnet-5、migration-guide
        if (isClaudeTemperatureRemoved(m)) return TEMPERATURE_MUST_OMIT;

        // OpenAI search 变体:必须省略 temperature 字段(传任何值都报错)
        // 来源:platform.openai.com docs、社区兼容性矩阵
        // 例:gpt-5-search-api、gpt-4o-search-preview、gpt-4o-mini-search-preview
        if (isSearchVariant(m)) return TEMPERATURE_MUST_OMIT;

        // DeepSeek V4 Flash/Pro:thinking 模式下 temperature 无效但不报错(非硬性要求);
        // GLM-5.2+:temperature 0-1 默认 1.0(非硬性要求);Qwen3.8-max:思考模式默认 0.6,<0.6 自动夹紧不报错。
        // 以上模型均返回 null,用户温度直接生效(无效则被上游忽略/夹紧)。
        return null;
    }

    /**
     * 哨兵值:表示模型必须省略 temperature 字段(不接受任何 temperature 值)。
     * 与 {@code null}(无已知硬性要求)区分,避免 search 变体 / Claude 5 被当作普通模型传温度。
     */
    public static final Double TEMPERATURE_MUST_OMIT = Double.NEGATIVE_INFINITY;

    /**
     * 判断模型是否支持 {@code reasoning_effort=none}(关闭思考)。
     * gpt-5.x(x>=1) 系列默认支持 none,允许 temperature/top_p;
     * gpt-5 初代/pro/codex/mini/nano 与 o 系列不支持 none,始终推理。
     * 覆盖:gpt-5.1 / 5.2 / 5.3 / 5.4 / 5.5 / 5.6 及后续小版本(排除 chat-latest)。
     * 来源:developers.openai.com gpt-5.5/gpt-5.6 文档、community.openai.com 兼容性矩阵
     */
    public static boolean supportsNoneEffort(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return false;
        }
        String m = stripProviderPrefix(modelId);
        if (m.contains("-chat-latest")) return false;
        // gpt-5.x(x>=1)支持 none;gpt-5 初代(无小版本)/gpt-5-pro/mini/codex-max 不支持。
        // 例外:gpt-5.1-codex-max、gpt-5.2-pro 虽小版本>=1,但官方矩阵标注不支持 none。
        // 来源:community.openai.com/t/1371738 兼容性矩阵
        if (m.contains("codex-max") || m.contains("-pro")) return false;
        return gpt5MinorVersion(m) >= 1;
    }

    /**
     * 判断模型是否支持 {@code reasoning_effort=xhigh}(高强度思考)。
     * gpt-5.x(x>=2) 系列、gpt-5.1-codex-max、Claude 5 家族支持 xhigh;其他模型不支持,应降级到 high。
     * 覆盖:gpt-5.2 / 5.3 / 5.4 / 5.5 / 5.6、gpt-5.1-codex-max、claude-sonnet-5/opus-5/fable-5/mythos-5。
     * 来源:community.openai.com/t/1371738 兼容性矩阵、platform.claude.com sonnet-5 migration
     */
    public static boolean supportsXhigh(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return false;
        }
        String m = stripProviderPrefix(modelId);
        if (m.contains("-chat-latest")) return false;
        // gpt-5.x(x>=2)支持 xhigh
        if (gpt5MinorVersion(m) >= 2) return true;
        // gpt-5.1-codex-max 支持 xhigh
        if (m.contains("gpt-5.1") && m.contains("codex-max")) return true;
        // Claude 5 家族 + Opus 4.5+ 支持 xhigh(low/medium/high/xhigh/max 全档)
        // 来源:datallmlab 采样参数支持表(Opus 4.5 effort to xhigh,4.6+ to max)
        if (isClaude5FamilyOrLater(m) || claudeOpus4Minor(m) >= 5) return true;
        return false;
    }

    /**
     * 判断模型是否支持 {@code reasoning_effort=max}(最高强度思考)。
     * gpt-5.6+、Claude 5 家族支持 max;gpt-5.5/5.2/5.1-codex-max 仅支持到 xhigh(选 max 时映射为 xhigh)。
     * 其他模型不支持 max,应降级到 xhigh(若支持)或 high。
     * 来源:developers.openai.com gpt-5.6 model guidance、platform.claude.com sonnet-5 migration
     */
    public static boolean supportsMax(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return false;
        }
        String m = stripProviderPrefix(modelId);
        if (m.contains("-chat-latest")) return false;
        // gpt-5.6+ 支持 max
        if (gpt5MinorVersion(m) >= 6) return true;
        // Claude 5 家族 + Opus 4.6+ 支持 max
        // 来源:datallmlab 采样参数支持表(Opus 4.6/4.7/4.8 + Claude 5 family effort to max)
        if (isClaude5FamilyOrLater(m) || claudeOpus4Minor(m) >= 6) return true;
        return false;
    }

    /**
     * 不发 reasoning_effort/thinking 参数时,模型默认是否处于思考模式。
     * <p>complete 路径不发思考参数,温度需按模型默认模式取值:
     * <ul>
     *   <li>gpt-5.1/5.2:默认 reasoning_effort=none(非思考),允许用户温度</li>
     *   <li>gpt-5.5/5.6:默认 medium(思考),温度固定 1</li>
     *   <li>gpt-5 初代/pro/codex/mini/nano:默认 always(思考),温度固定 1</li>
     *   <li>kimi-k2.5/k2.6:不发 thinking 时默认思考模式,温度取思考模式值</li>
     *   <li>Claude 5:adaptive thinking 默认开启(但温度已移除,走 TEMPERATURE_MUST_OMIT)</li>
     *   <li>o 系列:始终思考,温度固定 1</li>
     *   <li>未知模型:保守取 true(与历史行为一致)</li>
     * </ul>
     * 来源:developers.openai.com、platform.openai.com、platform.kimi.com、platform.claude.com
     */
    public static boolean defaultReasoningEnabledWhenOmitted(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return true;
        }
        String m = stripProviderPrefix(modelId);
        int minor = gpt5MinorVersion(m);
        // gpt-5.1/5.2 不发 reasoning_effort 时默认 none(非思考)
        if (minor == 1 || minor == 2) return false;
        // gpt-5.5/5.6 及其他模型不发参数时默认思考模式(medium/always/adaptive)
        return true;
    }

    /**
     * search 变体判定:必须省略 temperature 字段。
     * 收紧匹配:仅命中已知 OpenAI search 变体,避免 research / perplexity-search 等误判。
     * 已知:gpt-5-search-api、gpt-4o-search-preview、gpt-4o-mini-search-preview
     * (均含 {@code -search-} 中缀或 {@code search-preview}/{@code search-api} 子串)。
     * 不命中 {@code perplexity-search}、{@code research-1.0} 等非 OpenAI search 模型。
     * 来源:platform.openai.com docs、社区兼容性矩阵
     */
    private static boolean isSearchVariant(String m) {
        return m.contains("-search-")
                || m.contains("search-preview")
                || m.contains("search-api");
    }

    /**
     * gpt-5 系列判定:gpt 后跟主版本 5(5 后非数字,避免 gpt-50 误判),容忍中间任意字符。
     * 排除 chat-latest 非推理变体与 search 变体。覆盖初代(gpt-5/mini/pro)与小版本(gpt-5.x)。
     */
    private static boolean isGpt5Series(String m) {
        if (!m.contains("gpt") || m.contains("-chat-latest") || isSearchVariant(m)) {
            return false;
        }
        // gpt 后(惰性)跟 5,5 后非数字或结尾(避免 gpt-50);容忍 gpt 与 5 之间任意字符
        return Pattern.compile("gpt.*?5(?=\\D|$)", Pattern.DOTALL).matcher(m).find();
    }

    /**
     * 解析 gpt-5 系列的小版本号。
     * @return "gpt-5.X" 中的 X(>=0);gpt-5 初代(gpt-5 / gpt-5-mini / gpt-5-pro 等无小版本)返回 -1;非 gpt-5 返回 -1。
     * 正则容忍第三方在 gpt 与 5.X 之间插入任意字符(含字母数字),覆盖 xgpt-xx-5.6 / gpt-abc-5.5-pro 等。
     */
    private static int gpt5MinorVersion(String m) {
        Matcher mat = GPT5_MINOR.matcher(m);
        if (!mat.find()) return -1;
        try {
            return Integer.parseInt(mat.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Claude 5 家族及更新版本判定(Fable/Mythos/Sonnet-5/Opus-5/Haiku-5)。
     * 这些模型移除了采样参数,且支持 effort 全档(含 xhigh/max)。
     * 正则容忍第三方在 claude 与变体/版本之间插入任意字符,覆盖 xclaude-xx-sonnet-5 等。
     * 误判防护:取变体(sonnet/opus/haiku)后第一个数字作主版本,==5 才算 Claude 5;
     * 故 claude-opus-4-5(opus 4.5)不会被误判为 Claude 5 家族。
     * Fable/Mythos 无版本号,直接命中即 Claude 5 家族。
     * 来源:platform.claude.com docs/about-claude/models/whats-new-sonnet-5
     */
    private static boolean isClaude5FamilyOrLater(String m) {
        if (!m.contains("claude")) return false;
        // Fable/Mythos 即 Claude 5 家族(无版本号)
        Matcher fm = Pattern.compile("claude.*?(fable|mythos)", Pattern.DOTALL).matcher(m);
        if (fm.find()) return true;
        // sonnet/opus/haiku 后第一个数字 == 5 才是 Claude 5 家族
        Matcher mat = CLAUDE_VARIANT_MAJOR.matcher(m);
        if (mat.find()) {
            try {
                return Integer.parseInt(mat.group(2)) >= 5;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        // 裸 claude-5 别名(无变体词)
        if (Pattern.compile("claude\\D*5(?=\\D|$)", Pattern.DOTALL).matcher(m).find()) return true;
        return false;
    }

    /**
     * 解析 Claude Opus 4.x 的小版本号。
     * @return "claude-opus-4-X" 中的 X(如 claude-opus-4-5 → 5,claude-opus-4-8-20260101 → 8);
     *         非 opus-4 或无小版本返回 -1(claude-opus-5 走 isClaude5FamilyOrLater)。
     * 正则容忍第三方在 claude/opus/4/X 之间插入任意非数字字符,覆盖 xclaude-xx-opus-xx-4-7 等。
     */
    private static int claudeOpus4Minor(String m) {
        Matcher mat = CLAUDE_OPUS4_MINOR.matcher(m);
        if (!mat.find()) return -1;
        try {
            return Integer.parseInt(mat.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Claude 移除 temperature 的模型判定:Claude 5 家族 + Opus 4.7/4.8。
     * Opus 4.7 起移除采样参数,Sonnet 5 / Fable / Mythos / Opus 5 同样移除。
     * 正则容忍第三方改名,opus-4 后小版本 >=7 即移除温度。
     * 来源:platform.claude.com migration-guide、datallmlab 采样参数支持表
     */
    private static boolean isClaudeTemperatureRemoved(String m) {
        if (!m.contains("claude")) return false;
        if (isClaude5FamilyOrLater(m)) return true;
        // Claude Opus 4.7 / 4.8 同样移除了 temperature/top_p/top_k
        int minor = claudeOpus4Minor(m);
        return minor == 7 || minor == 8;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 规范化模型 ID:转小写并剥离 provider 前缀。
     * <p>第三方网关(OpenRouter / SiliconFlow / 聚合代理)常用 {@code "provider/model-id"} 命名,
     * 例 {@code openai/gpt-5.5}、{@code moonshot/kimi-k3}、{@code deepseek-ai/deepseek-v4-flash}、
     * {@code anthropic/claude-sonnet-5}、{@code zhipuai/glm-5.2}、{@code alibaba/qwen3.8-max}。
     * 剥离前缀后才能命中内置温度表与 effort 档位判定。
     * <p>仅剥离最后一个 {@code /} 之前的部分,保留模型名本身的所有字符(含别名/简写/版本后缀)。
     */
    public static String stripProviderPrefix(String modelId) {
        String m = modelId.toLowerCase(java.util.Locale.ROOT).trim();
        int slash = m.lastIndexOf('/');
        if (slash >= 0 && slash < m.length() - 1) {
            m = m.substring(slash + 1);
        }
        return m;
    }

    /**
     * Kimi 系列主版本号:k 后第一个数字(k3→3,k2.6→2)。
     * 正则 {@code kimi.*?k(\d+)} 容忍品牌词与版本号之间任意字符(含字母数字)。
     * 未命中返回 -1。
     */
    public static int kimiMajorVersion(String m) {
        Matcher mat = KIMI_VERSION.matcher(m);
        if (!mat.find()) return -1;
        try {
            return Integer.parseInt(mat.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Kimi 系列次版本号:k 后主版本.次版本 中的次版本(k2.6→6,k3→-1 无次版本)。
     * 未命中或无次版本返回 -1。
     */
    public static int kimiMinorVersion(String m) {
        Matcher mat = KIMI_VERSION.matcher(m);
        if (!mat.find()) return -1;
        String minor = mat.group(2);
        if (minor == null) return -1;
        try {
            return Integer.parseInt(minor);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * GLM 系列版本号:glm 后主.次(glm-5.2→[5,2])。
     * 正则容忍品牌词与版本号之间任意非数字字符,覆盖 xglm-xx-5.2 等。
     * 未命中返回 [-1,-1]。
     */
    public static int[] glmVersion(String m) {
        Matcher mat = GLM_VERSION.matcher(m);
        if (!mat.find()) return new int[]{-1, -1};
        try {
            int major = Integer.parseInt(mat.group(1));
            int minor = mat.group(2) != null ? Integer.parseInt(mat.group(2)) : 0;
            return new int[]{major, minor};
        } catch (NumberFormatException e) {
            return new int[]{-1, -1};
        }
    }

    /**
     * Qwen 系列版本号:qwen 后主.次(qwen3.8-max→[3,8])。
     * 正则容忍品牌词与版本号之间任意非数字字符,覆盖 xqwen-xx-3.8 等。
     * 未命中返回 [-1,-1]。
     */
    public static int[] qwenVersion(String m) {
        Matcher mat = QWEN_VERSION.matcher(m);
        if (!mat.find()) return new int[]{-1, -1};
        try {
            int major = Integer.parseInt(mat.group(1));
            int minor = mat.group(2) != null ? Integer.parseInt(mat.group(2)) : 0;
            return new int[]{major, minor};
        } catch (NumberFormatException e) {
            return new int[]{-1, -1};
        }
    }

    /**
     * OpenAI o 系列判定:o1 / o3 / o4 及其变体始终推理,temperature 固定 1。
     * 收紧匹配:base 名(o1/o3/o4)或 base-xxx 形式,避免 o1bak / qwen-o1 / o123 误判。
     */
    private static boolean isOpenAiOSeries(String m) {
        if (m.equals("o1") || m.equals("o3") || m.equals("o4")) {
            return true;
        }
        if (m.startsWith("o1-") || m.startsWith("o3-") || m.startsWith("o4-")) {
            return true;
        }
        return false;
    }
}
