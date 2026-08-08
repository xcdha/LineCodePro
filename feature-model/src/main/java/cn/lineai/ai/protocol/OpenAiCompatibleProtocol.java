package cn.lineai.ai.protocol;
import cn.lineai.model.tool.ToolCall;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.ai.stream.ThinkTagParser;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.ai.protocol.reasoning.DashscopeReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.DeepseekReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.DefaultReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.MinimaxReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.MoonshotReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.ReasoningDeltaExtractor;
import cn.lineai.tool.ToolInfo;
import cn.lineai.util.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OpenAiCompatibleProtocol extends AbstractHttpModelProtocol {

    private final OpenAiMessageSerializer messageSerializer = new OpenAiMessageSerializer();
    private final ReasoningStrategyRegistry reasoningStrategyRegistry = createDefaultRegistry();
    private final ReasoningDeltaExtractor reasoningDeltaExtractor = new ReasoningDeltaExtractor();

    private static ReasoningStrategyRegistry createDefaultRegistry() {
        ReasoningStrategyRegistry registry = new ReasoningStrategyRegistry();
        registry.register(new DashscopeReasoningStrategy());
        registry.register(new MinimaxReasoningStrategy());
        registry.register(new DeepseekReasoningStrategy());
        registry.register(new MoonshotReasoningStrategy());
        registry.register(new DefaultReasoningStrategy());
        return registry;
    }

    @Override
    public boolean supportsNativeTools(ModelConfig model) {
        return OpenAiCompatibleCapabilities.supportsNativeTools(model);
    }

    @Override
    public boolean supportsDedicatedCompression() {
        return true;
    }

    @Override
    public boolean supportsImageGeneration() {
        return true;
    }

    @Override
    public ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException {
        // complete 路径不发 reasoning_effort/thinking 参数,温度按模型默认思考模式取值:
        // gpt-5.2/5.1 默认 reasoning_effort=none(非思考)→ 允许用户温度;
        // kimi-k2.5/k2.6 不发 thinking 默认思考模式 → 温度取思考模式值(1.0);
        // gpt-5初代/o系列 始终推理 → 温度固定 1.0。
        String modelId = ModelContextParser.apiModelId(config);
        boolean reasoningEnabled = OpenAiCompatibleCapabilities.defaultReasoningEnabledWhenOmitted(modelId);
        Double forcedTemperature = null;
        ModelCompletionException lastError = null;
        // 最多 2 次尝试:原始 → 温度修复(complete 路径不发 reasoning_effort,无 effort 降级)
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return completeOnce(config, messages, forcedTemperature, reasoningEnabled);
            } catch (ModelCompletionException e) {
                lastError = e;
                String msg = e.getMessage();
                // 温度错误:解析硬性温度,按本次思考模式写缓存,重试时用 forcedTemperature
                Double hard = parseHardTemperature(msg);
                if (hard != null && config != null && config.getTemperature() != hard) {
                    HardTemperatureCache.put(modelId, reasoningEnabled, hard);
                    forcedTemperature = hard;
                    continue;
                }
                break;
            }
        }
        throw lastError;
    }

    private ModelCompletionResponse completeOnce(
            ModelConfig config,
            List<ModelMessage> messages,
            Double forcedTemperature,
            boolean reasoningEnabled
    ) throws ModelCompletionException {
        String raw = "";
        try {
            JSONObject body = new JSONObject();
            body.put("model", ModelContextParser.apiModelId(config));
            body.put("messages", messageSerializer.messagesJson(messages));
            applyTemperature(body, config, forcedTemperature, reasoningEnabled);

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getApiKey());
            raw = postJson(endpoint(config.getBaseUrl(), "/chat/completions"), body, headers);
            JSONObject response = new JSONObject(raw);
            String text = response
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content");
            JSONObject usage = response.optJSONObject("usage");
            int inputTokens = usage == null ? 0 : usage.optInt("prompt_tokens", 0);
            int outputTokens = usage == null ? 0 : usage.optInt("completion_tokens", 0);
            return new ModelCompletionResponse(text, "", java.util.Collections.emptyList(), inputTokens, outputTokens);
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            logParseError("parse_openai_complete", raw, e);
            throw new ModelCompletionException("OpenAI compatible protocol parse failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ModelCompletionResponse stream(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelStreamCallback callback,
            ModelCancellationToken cancellationToken,
            ModelRequestOptions options
    ) throws ModelCompletionException {
        // 思考模式由 AI 行为设置的 reasoningEffort 决定:kimi-k2.5/k2.6 等模型温度随模式切换
        ModelRequestOptions requestOptions = options == null ? ModelRequestOptions.defaults() : options;
        boolean userReasoningEnabled = AiBehaviorSettings.isReasoningEnabled(requestOptions.getReasoningEffort());
        Double forcedTemperature = null;
        ModelCompletionException lastError = null;
        String modelId = ModelContextParser.apiModelId(config);
        // 最多 6 次尝试:温度修复(1) + effort 全档降级(max/high/medium/low 共 4) + 禁用后重试(1)。
        // effort 降级只影响 reasoning_effort 参数,不影响 thinking 字段:kimi-k2.5/k2.6 通过
        // thinking.type=enabled/disabled 控制思考开关,与 reasoning_effort 独立。故 effort 全部
        // 被拒不发 reasoning_effort 时,模型仍发 thinking.type=enabled(思考模式),温度必须按思考模式取。
        for (int attempt = 0; attempt < 6; attempt++) {
            // 每次尝试前算出本次实际发送的 effort(resolveEffort 会沿降级链降级)。
            // 温度的思考模式跟随用户设置(userReasoningEnabled),不跟随 effort 降级:
            // effort 降级到 null 仅表示不发 reasoning_effort,但 thinking 字段仍由用户设置决定,
            // 因此温度的 reasoningEnabled 始终等于 userReasoningEnabled,与 applyReasoningRequest 一致。
            String currentEffort = userReasoningEnabled
                    ? ReasoningEffortCache.resolveEffort(modelId, AiBehaviorSettings.concreteReasoningEffort(requestOptions.getReasoningEffort()))
                    : null;
            boolean effectiveReasoningEnabled = userReasoningEnabled;
            try {
                return streamOnce(config, messages, callback, cancellationToken, options, forcedTemperature, effectiveReasoningEnabled);
            } catch (ModelCompletionException e) {
                lastError = e;
                String msg = e.getMessage();
                // 温度错误:解析硬性温度,按本次实际思考模式写缓存,重试时用 forcedTemperature
                Double hard = parseHardTemperature(msg);
                if (hard != null && config != null && config.getTemperature() != hard) {
                    HardTemperatureCache.put(modelId, effectiveReasoningEnabled, hard);
                    forcedTemperature = hard;
                    continue;
                }
                // effort 错误:记录当前 effort 被拒,下次 resolveEffort 自动降级到下一档
                if (currentEffort != null && isReasoningEffortError(msg)) {
                    ReasoningEffortCache.markRejected(modelId, currentEffort);
                    continue;
                }
                break;
            }
        }
        throw lastError;
    }

    private ModelCompletionResponse streamOnce(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelStreamCallback callback,
            ModelCancellationToken cancellationToken,
            ModelRequestOptions options,
            Double forcedTemperature,
            boolean reasoningEnabled
    ) throws ModelCompletionException {
        try {
            ModelRequestOptions requestOptions = options == null ? ModelRequestOptions.defaults() : options;
            JSONObject body = new JSONObject();
            body.put("model", ModelContextParser.apiModelId(config));
            body.put("messages", messageSerializer.messagesJson(messages, requestOptions.isPreserveReasoning()));
            applyTemperature(body, config, forcedTemperature, reasoningEnabled);
            body.put("stream", true);
            if (!requestOptions.getTools().isEmpty()) {
                body.put("tools", ToolInfo.toJsonArray(requestOptions.getTools()));
                body.put("tool_choice", "auto");
            }
            applyReasoningRequest(config, body, requestOptions);

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getApiKey());

            StringBuilder text = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            ThinkTagParser thinkTagParser = new ThinkTagParser();
            HashMap<Integer, ToolCallBuilder> toolCallBuilders = new HashMap<>();
            final int[] usageInputTokens = new int[1];
            final int[] usageOutputTokens = new int[1];

            postJsonSse(endpoint(config.getBaseUrl(), "/chat/completions"), body, headers, cancellationToken, (eventType, data) -> {
                if ("[DONE]".equals(data.trim())) {
                    return;
                }
                JSONObject event = new JSONObject(data);
                if (event.has("error")) {
                    throw new ModelCompletionException("OpenAI stream error: " + describeError(event.opt("error")));
                }
                // 部分兼容端点会在最后一个 chunk（含空 choices）携带 usage，先于 choices 处理。
                JSONObject usage = event.optJSONObject("usage");
                if (usage != null) {
                    usageInputTokens[0] = Math.max(usageInputTokens[0], usage.optInt("prompt_tokens", 0));
                    usageOutputTokens[0] = Math.max(usageOutputTokens[0], usage.optInt("completion_tokens", 0));
                }
                JSONArray choices = event.optJSONArray("choices");
                if (choices == null || choices.length() == 0) {
                    return;
                }
                JSONObject choice = choices.optJSONObject(0);
                if (choice == null) {
                    return;
                }
                if ("content_filter".equals(choice.optString("finish_reason"))) {
                    throw new ModelCompletionException("OpenAI stream error: output blocked by content safety policy");
                }
                JSONObject delta = choice.optJSONObject("delta");
                if (delta == null) {
                    return;
                }

                JSONArray toolCalls = delta.optJSONArray("tool_calls");
                if (toolCalls != null) {
                    appendToolCallDeltas(toolCallBuilders, toolCalls);
                }

                String reasoningDelta = reasoningDeltaExtractor.extract(delta);
                if (reasoningDelta.length() > 0) {
                    reasoning.append(reasoningDelta);
                    if (callback != null) {
                        callback.onReasoningDelta(reasoningDelta);
                    }
                }

                if (delta.has("content") && !delta.isNull("content")) {
                    ThinkTagParser.Result parsed = thinkTagParser.append(delta.optString("content"));
                    appendParsedDelta(text, reasoning, parsed, callback);
                }
            });

            appendParsedDelta(text, reasoning, thinkTagParser.flush(), callback);
            return new ModelCompletionResponse(
                    text.toString(),
                    reasoning.toString(),
                    buildToolCalls(toolCallBuilders),
                    usageInputTokens[0],
                    usageOutputTokens[0]
            );
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("OpenAI compatible protocol stream parse failed: " + e.getMessage(), e);
        }
    }

    private void appendToolCallDeltas(Map<Integer, ToolCallBuilder> builders, JSONArray toolCalls) {
        for (int i = 0; i < toolCalls.length(); i++) {
            JSONObject item = toolCalls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            int index = item.optInt("index", i);
            ToolCallBuilder builder = builders.get(index);
            if (builder == null) {
                builder = new ToolCallBuilder();
                builders.put(index, builder);
            }
            if (item.has("id") && !item.isNull("id")) {
                String id = item.optString("id");
                if (id.length() > 0) {
                    builder.id = id;
                }
            }
            JSONObject function = item.optJSONObject("function");
            if (function == null) {
                continue;
            }
            if (function.has("name") && !function.isNull("name")) {
                String name = function.optString("name");
                if (name.length() > 0) {
                    builder.name = name;
                }
            }
            if (function.has("arguments") && !function.isNull("arguments")) {
                builder.arguments.append(function.optString("arguments"));
            }
        }
    }

    private List<ToolCall> buildToolCalls(Map<Integer, ToolCallBuilder> builders) {
        ArrayList<Integer> indexes = new ArrayList<>(builders.keySet());
        indexes.sort(Integer::compareTo);
        ArrayList<ToolCall> calls = new ArrayList<>();
        for (Integer index : indexes) {
            ToolCallBuilder builder = builders.get(index);
            if (builder == null || !builder.hasName()) {
                continue;
            }
            calls.add(builder.build(index));
        }
        return calls;
    }

    private static final class ToolCallBuilder extends AbstractToolCallBuilder {
    }

    private void appendParsedDelta(
            StringBuilder text,
            StringBuilder reasoning,
            ThinkTagParser.Result parsed,
            ModelStreamCallback callback
    ) {
        if (parsed.getThinking().length() > 0) {
            reasoning.append(parsed.getThinking());
            if (callback != null) {
                callback.onReasoningDelta(parsed.getThinking());
            }
        }
        if (parsed.getText().length() > 0) {
            text.append(parsed.getText());
            if (callback != null) {
                callback.onTextDelta(parsed.getText());
            }
        }
    }

    /**
     * 写入 temperature 字段。{@code forcedTemperature} 非 null 时优先使用（错误驱动的硬性温度重试），
     * 否则走 {@link #resolveTemperature(ModelConfig, boolean)} 的优先级决策。
     * <p>{@code reasoningEnabled} 表示本次请求是否启用思考模式：部分模型（如 kimi-k2.5/k2.6）
     * 在思考与非思考模式下温度硬性要求不同，必须按实际模式取值，否则上游报错。
     * <p>返回 {@link OpenAiCompatibleCapabilities#TEMPERATURE_MUST_OMIT} 时省略 temperature 字段
     * (search 变体等必须省略温度的模型)。
     */
    private static void applyTemperature(JSONObject body, ModelConfig config, Double forcedTemperature, boolean reasoningEnabled) {
        Double resolved = forcedTemperature != null ? forcedTemperature : resolveTemperature(config, reasoningEnabled);
        // TEMPERATURE_MUST_OMIT 哨兵值:search 变体等必须省略 temperature,不写字段
        if (resolved == null || resolved.equals(OpenAiCompatibleCapabilities.TEMPERATURE_MUST_OMIT)) {
            return;
        }
        try {
            body.put("temperature", resolved);
        } catch (org.json.JSONException ignored) {
        }
    }

    /**
     * 从上游错误消息中解析硬性温度要求，例如：
     * {@code invalid temperature: only 1 is allowed for this model} → 1.0。
     * 解析失败返回 {@code null}。
     */
    static Double parseHardTemperature(String message) {
        if (message == null) {
            return null;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (!lower.contains("temperature") || !lower.contains("is allowed")) {
            return null;
        }
        Matcher matcher = HARD_TEMPERATURE_PATTERN.matcher(lower);
        if (matcher.find()) {
            try {
                double value = Double.parseDouble(matcher.group(1));
                return ModelConfig.normalizeTemperature(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static final Pattern HARD_TEMPERATURE_PATTERN =
            Pattern.compile("only\\s+([0-9]+(?:\\.[0-9]+)?)\\s+is\\s+allowed");

    /**
     * 判断上游错误是否与 reasoning_effort/thinking 参数相关(无效值、不支持参数等)。
     * 覆盖常见上游错误格式:OpenAI "invalid reasoning_effort"、通用 "unsupported parameter"、
     * "unrecognized parameter"、"unknown parameter"、"thinking is not supported" 等。
     * 拓宽匹配:HTTP 错误体里含 thinking/reasoning_effort 字样即视为参数错误,触发自动降级,
     * 避免不匹配时直接 break 进入应用级 3 次重试(用户看到的"正在第 2/3 次重试,错误:HTTP")。
     */
    private static boolean isReasoningEffortError(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        // reasoning_effort 相关错误
        if (lower.contains("reasoning_effort") || lower.contains("reasoning effort")) {
            return lower.contains("invalid") || lower.contains("unsupported")
                    || lower.contains("unrecognized") || lower.contains("unknown")
                    || lower.contains("not supported") || lower.contains("not a valid")
                    || lower.contains("must be one of") || lower.contains("allowed values")
                    || lower.contains("not allowed") || lower.contains("bad request");
        }
        // thinking 字段相关错误:网关返回 "thinking" + 任意错误指示词
        if (lower.contains("thinking")) {
            return lower.contains("unsupported") || lower.contains("unrecognized")
                    || lower.contains("unknown parameter") || lower.contains("not supported")
                    || lower.contains("not allowed") || lower.contains("invalid")
                    || lower.contains("bad request") || lower.contains("not a valid");
        }
        return false;
    }

    /**
     * 返回应当发送的 temperature 值;返回 {@code null} 或 {@link OpenAiCompatibleCapabilities#TEMPERATURE_MUST_OMIT}
     * 表示不发送该字段,让上游使用模型默认值(或必须省略)。
     * <p>优先级:硬性温度模型(缓存/内置表命中)> 用户自定义温度 > 不传字段。
     * <p>硬性温度模型(kimi-k3/k2.5/k2.6、o-series、gpt-5 等)只接受固定温度值,传其他值上游必报错。
     * 故缓存/内置表命中时直接覆盖用户填的温度,避免每次请求都试错一次:
     * 先查进程内缓存(已遇过 "only X is allowed" 的模型直接复用),再查内置表(已知硬性模型首次即零试错)。
     * <p>search 变体(gpt-5-search-api、gpt-4o-search-preview 等)必须省略 temperature 字段,
     * 内置表返回 {@link OpenAiCompatibleCapabilities#TEMPERATURE_MUST_OMIT} 哨兵值,applyTemperature 据此省略字段。
     * <p>非硬性温度模型(绝大多数)走用户自定义温度,用户填 0.2/0.7/1.0 等任意值完全生效;
     * 用户未设置则不传字段,交由上游用模型默认值 —— 若上游报硬性温度错误(此前未知的硬性模型),
     * complete/stream 会自动重试并写入缓存,后续请求零试错。
     * <p>{@code reasoningEnabled} 区分思考/非思考模式:kimi-k2.5/k2.6 在两种模式下温度硬性要求不同
     * (思考 1.0 / 非思考 0.6),缓存按模式分别记录,避免互相覆盖。
     */
    private static Double resolveTemperature(ModelConfig config, boolean reasoningEnabled) {
        if (config == null) {
            return null;
        }
        String modelId = ModelContextParser.apiModelId(config);
        // 硬性温度模型优先:缓存/内置表命中时直接覆盖用户填的温度,零试错。
        // 这些模型只接受固定温度,用户填其他值必然失败,覆盖是正确行为。
        Double cached = HardTemperatureCache.get(modelId, reasoningEnabled);
        if (cached != null) {
            return cached;
        }
        Double known = OpenAiCompatibleCapabilities.knownHardTemperature(modelId, reasoningEnabled);
        if (known != null) {
            // 内置表可能返回 TEMPERATURE_MUST_OMIT(search 变体必须省略温度)
            return known;
        }
        // 非硬性温度模型:用户填的值完全生效
        if (config.getTemperature() != ModelConfig.TEMPERATURE_UNSET) {
            return config.getTemperature();
        }
        return null;
    }

    private void applyReasoningRequest(ModelConfig config, JSONObject body, ModelRequestOptions options) throws Exception {
        if (!OpenAiCompatibleCapabilities.supportsReasoningRequestParameters(config)) {
            return;
        }
        String base = config.getBaseUrl().toLowerCase(java.util.Locale.ROOT);
        String model = ModelContextParser.apiModelId(config).toLowerCase(java.util.Locale.ROOT);
        String effort = options.getReasoningEffort();
        boolean enabled = AiBehaviorSettings.isReasoningEnabled(effort);
        String concrete = AiBehaviorSettings.concreteReasoningEffort(effort);
        // 运行时自适应:从用户选的档位开始,沿降级链(max→high→medium→low)找第一个未被拒的档位。
        // 用户调整优先:用户选的档位未被拒就直接用,保证 AI 行为设置调整生效;
        // 已被拒则降到下一档;全部被拒返回 null,不发 reasoning_effort 参数。
        if (enabled) {
            concrete = ReasoningEffortCache.resolveEffort(model, concrete);
            if (concrete == null) {
                // 模型所有档位都被拒,完全不支持 reasoning_effort,不发参数
                return;
            }
        }
        ReasoningRequestContext context = new ReasoningRequestContext(
                enabled, concrete, options.isPreserveReasoning(), base, model, thinkingBudget(concrete));
        ReasoningRequestStrategy strategy = reasoningStrategyRegistry.find(base, model);
        if (strategy != null) {
            strategy.apply(body, context);
        }
    }

    JSONObject reasoningRequestBodyForTest(ModelConfig config, ModelRequestOptions options) throws Exception {
        JSONObject body = new JSONObject();
        applyReasoningRequest(config, body, options == null ? ModelRequestOptions.defaults() : options);
        return body;
    }

    /**
     * 测试专用：返回经过 {@link #applyTemperature(JSONObject, ModelConfig, Double, boolean)} 处理后的请求体，
     * 用于验证 temperature 字段的优先级与是否被写入。默认按思考模式解析温度。
     */
    JSONObject temperatureBodyForTest(ModelConfig config) throws Exception {
        return temperatureBodyForTest(config, true);
    }

    /**
     * 测试专用：按指定思考模式返回温度处理后的请求体,用于验证思考/非思考模式下的温度取值。
     */
    JSONObject temperatureBodyForTest(ModelConfig config, boolean reasoningEnabled) throws Exception {
        JSONObject body = new JSONObject();
        applyTemperature(body, config, null, reasoningEnabled);
        return body;
    }

    /**
     * 把 SSE 错误字段可读地转为文本。{@code error} 可能是字符串或 JSON 对象：
     * 对象时直接 {@code JSONObject.toString()} 会把中文转义成 {@code \\uXXXX}，
     * 因此优先读取 {@code message}/{@code type} 字段，最后统一做一次 Unicode 转义解码。
     */
    private static String describeError(Object error) {
        if (error == null) {
            return "";
        }
        if (error instanceof JSONObject) {
            JSONObject obj = (JSONObject) error;
            String message = obj.optString("message");
            if (message != null && message.length() > 0) {
                return StringUtils.decodeUnicodeEscapes(message);
            }
            String type = obj.optString("type");
            if (type != null && type.length() > 0) {
                return StringUtils.decodeUnicodeEscapes(type);
            }
            return StringUtils.decodeUnicodeEscapes(obj.optString("code"));
        }
        return StringUtils.decodeUnicodeEscapes(error.toString());
    }

    /**
     * 进程内硬性温度缓存:从上游 "only X is allowed" 错误中学到的模型硬性温度要求。
     * <p>同一模型同一思考模式第二次起直接命中缓存,零试错。进程级生命周期(App 运行期间有效,重启后重建)。
     * 重启后由内置表({@link OpenAiCompatibleCapabilities#knownHardTemperature})兜底常用模型,
     * 未知模型则会再试错一次后重新进缓存 —— 每模型每模式每进程最多付一次试错成本。
     * <p>缓存 key 按 "模型 + 思考模式" 区分:kimi-k2.5/k2.6 思考模式固定 1.0、非思考模式固定 0.6,
     * 若不区分模式会互相覆盖导致后续请求传错值。
     * <p>TTL 24 小时:过期后视为未缓存,重新探测。模型提供商可能更新 API 支持新的温度值,
     * TTL 避免陈旧缓存永久锁死。内置表模型不受 TTL 影响(每次重新查表)。
     */
    static final class HardTemperatureCache {
        private static final long TTL_MS = java.util.concurrent.TimeUnit.HOURS.toMillis(24);

        private static final class CachedTemperature {
            final double temperature;
            final long timestamp;
            CachedTemperature(double temperature, long timestamp) {
                this.temperature = temperature;
                this.timestamp = timestamp;
            }
        }

        private static final java.util.concurrent.ConcurrentMap<String, CachedTemperature> CACHE =
                new java.util.concurrent.ConcurrentHashMap<>();

        static Double get(String modelId, boolean reasoningEnabled) {
            if (modelId == null || modelId.isEmpty()) {
                return null;
            }
            CachedTemperature c = CACHE.get(cacheKey(modelId, reasoningEnabled));
            if (c == null) {
                return null;
            }
            // TTL 过期:移除并返回 null,触发重新探测(模型可能已更新支持新温度值)
            if (System.currentTimeMillis() - c.timestamp > TTL_MS) {
                CACHE.remove(cacheKey(modelId, reasoningEnabled), c);
                return null;
            }
            return c.temperature;
        }

        static void put(String modelId, boolean reasoningEnabled, double temperature) {
            if (modelId == null || modelId.isEmpty()) {
                return;
            }
            CACHE.put(cacheKey(modelId, reasoningEnabled),
                    new CachedTemperature(temperature, System.currentTimeMillis()));
        }

        private static String cacheKey(String modelId, boolean reasoningEnabled) {
            return modelId.toLowerCase(Locale.ROOT) + ":" + (reasoningEnabled ? "think" : "nothink");
        }

        static void clearForTest() {
            CACHE.clear();
        }
    }

    /**
     * 运行时推理强度自适应缓存:对未在内置表/strategy 中的未知模型,从上游错误中学习其对
     * {@code reasoning_effort} 各档位的支持情况。
     * <p>记录"已知被拒绝的 effort 集合",支持六档逐档降级:
     * {@code max → high → medium → low → 禁用}。每次请求从用户选的档位开始,
     * 若该档位在拒绝集合中则降到下一档,直到找到模型支持的档位或全部被拒(禁用参数)。
     * <p>用户调整优先:用户选某档时,先用该档(若未被拒),被拒再降级——不预先降级,
     * 保证用户在 AI 行为设置里的调整能生效。缓存命中后后续请求零试错。
     * <p>TTL 24 小时:过期后视为未拒绝,允许重新探测。模型提供商可能更新支持新的 effort 档位,
     * TTL 避免陈旧拒绝记录永久锁死。
     */
    static final class ReasoningEffortCache {
        private static final long TTL_MS = java.util.concurrent.TimeUnit.HOURS.toMillis(24);

        /** effort 降级链:从高到低。max→high→medium→low,low 再被拒则禁用参数 */
        private static final String[] DOWNGRADE_CHAIN = {
                AiBehaviorSettings.REASONING_MAX,
                AiBehaviorSettings.REASONING_HIGH,
                AiBehaviorSettings.REASONING_MEDIUM,
                AiBehaviorSettings.REASONING_LOW
        };

        /** 拒绝记录:value 为拒绝时间戳,用于 TTL 过期判断 */
        private static final java.util.concurrent.ConcurrentMap<String, Long> REJECTED =
                new java.util.concurrent.ConcurrentHashMap<>();

        private static String rejectionKey(String modelId, String effort) {
            return modelId.toLowerCase(Locale.ROOT) + ":" + effort;
        }

        /**
         * 记录某 effort 档位被上游拒绝。
         */
        static void markRejected(String modelId, String effort) {
            if (modelId == null || modelId.isEmpty() || effort == null) {
                return;
            }
            REJECTED.put(rejectionKey(modelId, effort), System.currentTimeMillis());
        }

        /**
         * 该 effort 档位是否已被拒绝(且未过 TTL)。
         */
        static boolean isRejected(String modelId, String effort) {
            if (modelId == null || modelId.isEmpty() || effort == null) {
                return false;
            }
            Long ts = REJECTED.get(rejectionKey(modelId, effort));
            if (ts == null) {
                return false;
            }
            // TTL 过期:移除并返回 false,允许重新探测(模型可能已更新支持该档位)
            if (System.currentTimeMillis() - ts > TTL_MS) {
                REJECTED.remove(rejectionKey(modelId, effort), ts);
                return false;
            }
            return true;
        }

        /**
         * 所有已知档位是否都被拒绝(模型完全不支持 reasoning_effort)。
         */
        static boolean isFullyDisabled(String modelId) {
            if (modelId == null || modelId.isEmpty()) {
                return false;
            }
            for (String e : DOWNGRADE_CHAIN) {
                if (!isRejected(modelId, e)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * 从用户选的 effort 开始,沿降级链找到第一个未被拒绝的档位。
         * @return 可用的 effort;若全部被拒返回 null(应禁用 reasoning_effort 参数)
         */
        static String resolveEffort(String modelId, String requestedEffort) {
            if (modelId == null || modelId.isEmpty() || requestedEffort == null) {
                return requestedEffort;
            }
            // 用户选的档位未被拒,直接用(用户调整优先)
            if (!isRejected(modelId, requestedEffort)) {
                return requestedEffort;
            }
            // 用户选的档位被拒,沿降级链找下一个可用档位
            int start = indexOfEffort(requestedEffort);
            if (start >= 0) {
                for (int i = start + 1; i < DOWNGRADE_CHAIN.length; i++) {
                    if (!isRejected(modelId, DOWNGRADE_CHAIN[i])) {
                        return DOWNGRADE_CHAIN[i];
                    }
                }
            }
            return null;
        }

        private static int indexOfEffort(String effort) {
            for (int i = 0; i < DOWNGRADE_CHAIN.length; i++) {
                if (DOWNGRADE_CHAIN[i].equals(effort)) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * 清除指定模型的缓存,允许重新探测。用户切换模型或显式想重新探测时调用。
         */
        static void reset(String modelId) {
            if (modelId == null || modelId.isEmpty()) {
                return;
            }
            String prefix = modelId.toLowerCase(Locale.ROOT) + ":";
            REJECTED.keySet().removeIf(k -> k.startsWith(prefix));
        }

        static void clearForTest() {
            REJECTED.clear();
        }
    }
}
