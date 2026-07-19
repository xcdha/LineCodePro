package cn.lineai.model;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModelContextParser {
    private static final int DEFAULT_CONTEXT_TOKENS = 250000;
    private static final Pattern CONTEXT_SUFFIX = Pattern.compile("\\[([0-9]+(?:\\.[0-9]+)?)([kKmM]?)\\]$");

    private ModelContextParser() {
    }

    public static ModelContextInfo parse(String modelId) {
        String trimmed = modelId == null ? "" : modelId.trim();
        Matcher matcher = CONTEXT_SUFFIX.matcher(trimmed);
        if (!matcher.find()) {
            return new ModelContextInfo(trimmed, DEFAULT_CONTEXT_TOKENS, formatContextSize(DEFAULT_CONTEXT_TOKENS));
        }

        double rawNumber;
        try {
            rawNumber = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException e) {
            rawNumber = DEFAULT_CONTEXT_TOKENS;
        }
        String unit = matcher.group(2) == null ? "" : matcher.group(2).toLowerCase(Locale.US);
        double multiplier = "m".equals(unit) ? 1000000d : "k".equals(unit) ? 1000d : 1d;
        int contextTokens = Math.max(1, (int) Math.round(rawNumber * multiplier));
        String apiModelId = trimmed.substring(0, matcher.start()).trim();
        if (apiModelId.length() == 0) {
            apiModelId = trimmed;
        }
        return new ModelContextInfo(apiModelId, contextTokens, formatContextSize(contextTokens));
    }

    /**
     * 优先使用 {@link ModelConfig#getContextSize()} 字段，向后兼容旧 {@code {id}[{大小}]} 后缀。
     *
     * <p>解析优先级：
     * <ol>
     *   <li>{@code model.getContextSize() > 0}：直接使用，apiModelId 就是 {@code model.getModelId()}；</li>
     *   <li>否则回退到 {@link #parse(String)} 解析 modelId 中的旧 {@code [大小]} 后缀。</li>
     * </ol>
     */
    public static ModelContextInfo parse(ModelConfig model) {
        if (model == null) {
            return parse("");
        }
        int explicit = model.getContextSize();
        if (explicit > 0) {
            return new ModelContextInfo(
                    model.getModelId(),
                    explicit,
                    formatContextSize(explicit)
            );
        }
        return parse(model.getModelId());
    }

    public static String apiModelId(String modelId) {
        return parse(modelId).getApiModelId();
    }

    /**
     * 与 {@link #parse(ModelConfig)} 对应：返回真正要发给上游 API 的 modelId 字符串。
     * 若 {@link ModelConfig#getContextSize()} 已设置，则不再剥离任何后缀，直接返回 modelId。
     */
    public static String apiModelId(ModelConfig model) {
        if (model == null) {
            return "";
        }
        if (model.getContextSize() > 0) {
            return model.getModelId();
        }
        return apiModelId(model.getModelId());
    }

    public static String formatContextSize(int tokens) {
        if (tokens >= 1000000) {
            double value = tokens / 1000000d;
            return formatNumber(value) + "m";
        }
        if (tokens >= 1000) {
            double value = tokens / 1000d;
            return formatNumber(value) + "k";
        }
        return String.valueOf(tokens);
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.round(value)) < 0.00001d) {
            return String.valueOf((long) Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }
}
