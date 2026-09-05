package dev.ix1ax.main.util;

/**
 * Utility class for Telegram HTML message formatting.
 */
public final class HtmlUtils {

    private HtmlUtils() {
    }

    /**
     * Escape special HTML characters for safe use in Telegram HTML messages.
     */
    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
