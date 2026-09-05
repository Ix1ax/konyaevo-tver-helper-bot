package dev.ix1ax.main.bot;

import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

/**
 * Classifies Telegram API errors into categories for proper handling.
 */
public final class TelegramErrorClassifier {

    private TelegramErrorClassifier() {
    }

    /**
     * Check if the error indicates the bot was blocked by the user.
     */
    public static boolean isUserBlockedError(Exception e) {
        if (e instanceof TelegramApiRequestException reqEx) {
            if (reqEx.getErrorCode() == 403) return true;
        }
        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("bot was blocked by the user")
                || lower.contains("user is deactivated")
                || lower.contains("chat not found")
                || lower.contains("forbidden");
    }

    /**
     * Check if the error is a network/connectivity issue.
     */
    public static boolean isNetworkError(Exception e) {
        if (e instanceof TelegramApiRequestException reqEx) {
            int code = reqEx.getErrorCode();
            if (code == 502 || code == 504 || code == 500) return true;
        }
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof java.net.SocketTimeoutException
                    || cause instanceof java.net.ConnectException
                    || cause instanceof java.net.UnknownHostException
                    || cause instanceof java.net.NoRouteToHostException
                    || cause instanceof java.net.SocketException
                    || cause instanceof javax.net.ssl.SSLHandshakeException) {
                return true;
            }
            cause = cause.getCause();
        }
        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("timeout")
                || lower.contains("timed out")
                || lower.contains("connection refused")
                || lower.contains("failed to connect")
                || lower.contains("connection reset")
                || lower.contains("bad gateway")
                || lower.contains("gateway timeout")
                || lower.contains("service unavailable");
    }

    /**
     * Check if the error is a rate limiting (429) error.
     */
    public static boolean isRateLimitError(Exception e) {
        if (e instanceof TelegramApiRequestException reqEx) {
            if (reqEx.getErrorCode() == 429) return true;
        }
        String msg = e.getMessage();
        return msg != null && (msg.contains("429") || msg.toLowerCase().contains("too many requests"));
    }

    /**
     * Check if the error indicates the message content was not modified.
     */
    public static boolean isMessageNotModifiedError(Exception e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("message is not modified");
    }
}
