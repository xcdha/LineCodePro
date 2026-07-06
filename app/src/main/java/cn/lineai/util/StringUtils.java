package cn.lineai.util;

public final class StringUtils {

    private StringUtils() {
    }

    /**
     * Checks if all characters in {@code text} are either a-z, A-Z, 0-9,
     * or one of the characters in {@code allowedSpecialChars}.
     */
    public static boolean isAllowedIdentifier(String text, String allowedSpecialChars) {
        if (text == null || text.length() == 0) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!isAllowedChar(text.charAt(i), allowedSpecialChars)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a single character is either a-z, A-Z, 0-9,
     * or one of the characters in {@code allowedSpecialChars}.
     */
    public static boolean isAllowedChar(char ch, String allowedSpecialChars) {
        if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) {
            return true;
        }
        if (allowedSpecialChars != null) {
            for (int i = 0; i < allowedSpecialChars.length(); i++) {
                if (ch == allowedSpecialChars.charAt(i)) {
                    return true;
                }
            }
        }
        return false;
    }
}
