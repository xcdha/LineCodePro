package cn.lineai.model;

import java.util.Locale;

/**
 * 解析用户在「上下文大小」输入框中输入的文本，支持 k / K / m / M 单位。
 *
 * <p>该类只负责把 UI 文本解析成整数 tokens，以及把整数 tokens 格式化为友好显示文本。
 * 它不关心 modelId 字段，也不取代 {@link ModelContextParser} 对旧 {@code {id}[{大小}]}
 * 后缀的兼容解析。
 */
public final class ContextSizeParser {
    /** 解析失败或空输入时的哨兵值，表示「未设置，使用默认」。 */
    public static final int UNSET = 0;

    private ContextSizeParser() {
    }

    /**
     * 把用户输入解析为 tokens 数。
     *
     * <p>支持的格式（大小写不敏感）：
     * <ul>
     *   <li>{@code "128000"} → 128000</li>
     *   <li>{@code "128k"} / {@code "128K"} → 128000</li>
     *   <li>{@code "1m"} / {@code "1M"} → 1000000</li>
     *   <li>{@code "1.5k"} → 1500</li>
     *   <li>{@code ""} / {@code "0"} → {@link #UNSET}</li>
     * </ul>
     * 解析失败返回 {@link #UNSET}。
     */
    public static int parse(String input) {
        if (input == null) {
            return UNSET;
        }
        String trimmed = input.trim();
        if (trimmed.length() == 0) {
            return UNSET;
        }
        String lower = trimmed.toLowerCase(Locale.US);
        try {
            double value;
            if (lower.endsWith("k")) {
                value = Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1000d;
            } else if (lower.endsWith("m")) {
                value = Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1000000d;
            } else {
                value = Double.parseDouble(lower);
            }
            if (value <= 0d) {
                return UNSET;
            }
            long rounded = Math.round(value);
            if (rounded <= 0L || rounded > Integer.MAX_VALUE) {
                return UNSET;
            }
            return (int) rounded;
        } catch (NumberFormatException e) {
            return UNSET;
        }
    }

    /**
     * 把 tokens 数格式化为友好显示文本。
     *
     * <p>例：128000 → "128K"，1000000 → "1M"，1500 → "1.5K"，0 → ""。
     */
    public static String format(int size) {
        if (size <= 0) {
            return "";
        }
        if (size >= 1000000 && size % 1000000 == 0) {
            return (size / 1000000) + "M";
        }
        if (size >= 1000000) {
            return formatDecimal(size / 1000000d) + "M";
        }
        if (size >= 1000 && size % 1000 == 0) {
            return (size / 1000) + "K";
        }
        if (size >= 1000) {
            return formatDecimal(size / 1000d) + "K";
        }
        return String.valueOf(size);
    }

    private static String formatDecimal(double value) {
        if (Math.abs(value - Math.round(value)) < 0.00001d) {
            return String.valueOf((long) Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }
}
