package dev.ix1ax.main.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.service.ScheduleService;

/**
 * Handles all Telegram message sending/editing with error handling and fallback logic.
 */
@Component
public class MessageSender {

    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);

    private final ScheduleService scheduleService;
    private AbsSender bot;

    public MessageSender(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /**
     * Must be called after bot construction to provide the sender reference.
     */
    public void init(AbsSender bot) {
        this.bot = bot;
    }

    /**
     * Send a new message and save the message ID for future editing.
     */
    public void sendNewMessage(long chatId, String text, InlineKeyboardMarkup keyboard, String logContext) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(text);
        msg.setParseMode("HTML");
        msg.setReplyMarkup(keyboard);

        try {
            Message sent = (Message) bot.execute(msg);
            UserSettings user = scheduleService.getOrCreateUser(chatId);
            user.setMessageId(sent.getMessageId());
            scheduleService.saveUser(user);
            log.info("[SENT SUCCESS] {} to chatId: {} (msgId: {})", logContext, chatId, sent.getMessageId());
        } catch (Exception e) {
            logSendError(e, logContext, chatId);
        }
    }

    /**
     * Edit an existing message. Falls back to sending a new message if editing fails.
     */
    public void editMessage(long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        // Telegram message limit is 4096 chars — truncate safely
        text = truncateIfNeeded(text);

        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId(messageId);
        edit.setText(text);
        edit.setParseMode("HTML");
        edit.setReplyMarkup(keyboard);

        try {
            bot.execute(edit);
            log.info("[SCREEN UPDATE] Edited msgId: {} for chatId: {}", messageId, chatId);
        } catch (Exception e) {
            if (TelegramErrorClassifier.isUserBlockedError(e)) {
                log.warn("[USER BLOCKED] ChatId {}: bot was blocked by user.", chatId);
                return;
            }
            if (TelegramErrorClassifier.isNetworkError(e)) {
                log.warn("[NETWORK ERROR] ChatId {}: Telegram unreachable ({}).", chatId, e.getMessage());
                return;
            }
            if (TelegramErrorClassifier.isRateLimitError(e)) {
                log.warn("[RATE LIMIT] ChatId {}: Telegram 429 ({}).", chatId, e.getMessage());
                return;
            }
            if (TelegramErrorClassifier.isMessageNotModifiedError(e)) {
                log.debug("[EDIT IGNORED] Content unchanged for chatId: {}", chatId);
                return;
            }

            log.warn("Edit failed for chatId {} ({}). Attempting fallback SendMessage...", chatId, e.getMessage());
            fallbackSendMessage(chatId, text, keyboard);
        }
    }

    public enum DirectSendResult {
        SUCCESS,
        BLOCKED,
        RATE_LIMIT,
        ERROR
    }

    /**
     * Send a standalone message (e.g. broadcast or admin report) without modifying user settings session.
     */
    public DirectSendResult sendDirectMessage(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(truncateIfNeeded(text));
        msg.setParseMode("HTML");
        if (keyboard != null) {
            msg.setReplyMarkup(keyboard);
        }

        try {
            bot.execute(msg);
            return DirectSendResult.SUCCESS;
        } catch (Exception e) {
            if (TelegramErrorClassifier.isUserBlockedError(e)) {
                return DirectSendResult.BLOCKED;
            }
            if (TelegramErrorClassifier.isRateLimitError(e)) {
                return DirectSendResult.RATE_LIMIT;
            }

            // Try plain text fallback if HTML tags were unclosed/malformed
            try {
                msg.setParseMode(null);
                msg.setText(text.replaceAll("<[^>]*>", ""));
                bot.execute(msg);
                return DirectSendResult.SUCCESS;
            } catch (Exception ex2) {
                if (TelegramErrorClassifier.isUserBlockedError(ex2)) {
                    return DirectSendResult.BLOCKED;
                }
                if (TelegramErrorClassifier.isRateLimitError(ex2)) {
                    return DirectSendResult.RATE_LIMIT;
                }
                log.warn("[DIRECT SEND ERROR] Failed for chatId {}: {}", chatId, ex2.getMessage());
                return DirectSendResult.ERROR;
            }
        }
    }

    // ===== Private helpers =====

    private void fallbackSendMessage(long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            SendMessage sendMsg = new SendMessage();
            sendMsg.setChatId(String.valueOf(chatId));
            sendMsg.setText(text);
            sendMsg.setParseMode("HTML");
            sendMsg.setReplyMarkup(keyboard);
            Message sent = (Message) bot.execute(sendMsg);

            UserSettings user = scheduleService.getOrCreateUser(chatId);
            user.setMessageId(sent.getMessageId());
            scheduleService.saveUser(user);
            log.info("[FALLBACK SUCCESS] Sent replacement message for chatId: {} (new msgId: {})", chatId, sent.getMessageId());
        } catch (Exception ex) {
            if (TelegramErrorClassifier.isUserBlockedError(ex)
                    || TelegramErrorClassifier.isNetworkError(ex)
                    || TelegramErrorClassifier.isRateLimitError(ex)) {
                log.warn("[FALLBACK FAILED] chatId {}: {}", chatId, ex.getMessage());
                return;
            }

            log.warn("HTML fallback failed for chatId {}. Retrying as plain text...", chatId);
            fallbackPlainText(chatId, text, keyboard);
        }
    }

    private void fallbackPlainText(long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            SendMessage plainMsg = new SendMessage();
            plainMsg.setChatId(String.valueOf(chatId));
            plainMsg.setText(text.replaceAll("<[^>]*>", ""));
            plainMsg.setReplyMarkup(keyboard);
            Message sent = (Message) bot.execute(plainMsg);

            UserSettings user = scheduleService.getOrCreateUser(chatId);
            user.setMessageId(sent.getMessageId());
            scheduleService.saveUser(user);
            log.info("[PLAIN TEXT SUCCESS] Sent plain text message {} for chatId: {}", sent.getMessageId(), chatId);
        } catch (Exception fatalEx) {
            if (TelegramErrorClassifier.isUserBlockedError(fatalEx)) {
                log.warn("[USER BLOCKED] Plain text fallback aborted for chatId {}", chatId);
            } else if (TelegramErrorClassifier.isNetworkError(fatalEx)) {
                log.warn("[NETWORK ERROR] Plain text fallback aborted for chatId {}", chatId);
            } else {
                log.error("Fatal error delivering message to chatId {}: {}", chatId, fatalEx.getMessage());
            }
        }
    }

    private void logSendError(Exception e, String logContext, long chatId) {
        if (TelegramErrorClassifier.isUserBlockedError(e)) {
            log.warn("[USER BLOCKED] Cannot send {} to chatId {}", logContext, chatId);
        } else if (TelegramErrorClassifier.isNetworkError(e)) {
            log.warn("[NETWORK ERROR] Cannot send {} to chatId {}: {}", logContext, chatId, e.getMessage());
        } else if (TelegramErrorClassifier.isRateLimitError(e)) {
            log.warn("[RATE LIMIT] Cannot send {} to chatId {}: {}", logContext, chatId, e.getMessage());
        } else {
            log.error("Failed to send {} to chatId {}: {}", logContext, chatId, e.getMessage());
        }
    }

    /**
     * Truncate text to fit Telegram's 4096-char limit, trying to break at paragraph boundaries.
     */
    private String truncateIfNeeded(String text) {
        if (text.length() <= 3900) return text;

        int cut = text.lastIndexOf("\n\n", 3900);
        if (cut > 2000) {
            return text.substring(0, cut) + "\n\n<i>...часть расписания сокращена</i>";
        } else {
            return text.substring(0, 3900) + "...";
        }
    }
}
