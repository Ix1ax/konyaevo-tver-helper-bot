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
    private record Screen(String token, java.util.List<String> pages, InlineKeyboardMarkup keyboard, long created) {}
    private final java.util.Map<Long, Screen> screens = new java.util.concurrent.ConcurrentHashMap<>();

    public boolean showPage(long chatId, int messageId, String callback) {
        Screen screen = screens.get(chatId);
        String[] parts = callback.split(":");
        if (screen == null || parts.length != 3 || !screen.token().equals(parts[1]) ||
                System.currentTimeMillis() - screen.created() > 3600000) return false;
        try {
            int page = Integer.parseInt(parts[2]);
            if (page < 0 || page >= screen.pages().size()) return false;
            editSingle(chatId, messageId, screen.pages().get(page), pageKeyboard(screen, page));
            return true;
        } catch (NumberFormatException ignored) { return false; }
    }

    private InlineKeyboardMarkup pageKeyboard(Screen screen, int page) {
        var rows = new java.util.ArrayList<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>>();
        var nav = new java.util.ArrayList<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>();
        if (page > 0) nav.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder().text("‹").callbackData("page:" + screen.token() + ":" + (page - 1)).build());
        nav.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder().text((page + 1) + " / " + screen.pages().size()).callbackData("page:" + screen.token() + ":" + page).build());
        if (page + 1 < screen.pages().size()) nav.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder().text("›").callbackData("page:" + screen.token() + ":" + (page + 1)).build());
        rows.add(nav);
        if (screen.keyboard() != null) rows.addAll(screen.keyboard().getKeyboard());
        return new InlineKeyboardMarkup(rows);
    }

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
        var pages = dev.ix1ax.main.util.TelegramText.pages(text);
        Screen screen = pages.size() > 1 ? new Screen(java.util.UUID.randomUUID().toString().substring(0, 8), pages, keyboard, System.currentTimeMillis()) : null;
        msg.setText(pages.get(0));
        msg.setParseMode("HTML");
        msg.setReplyMarkup(screen == null ? keyboard : pageKeyboard(screen, 0));

        try {
            Message sent = (Message) bot.execute(msg);
            UserSettings user = scheduleService.getOrCreateUser(chatId);
            Integer oldId = user.getMessageId();
            user.setMessageId(sent.getMessageId());
            if (screen == null) screens.remove(chatId); else screens.put(chatId, screen);
            if (oldId != null && !oldId.equals(sent.getMessageId())) {
                try {
                    bot.execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup.builder()
                            .chatId(Long.toString(chatId)).messageId(oldId)
                            .replyMarkup(new InlineKeyboardMarkup(java.util.List.of())).build());
                } catch (Exception ignored) { /* Старое сообщение могло быть удалено пользователем. */ }
            }
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
        var pages = dev.ix1ax.main.util.TelegramText.pages(text);
        screens.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().created() > 3600000);
        if (pages.size() > 1) {
            Screen screen = new Screen(java.util.UUID.randomUUID().toString().substring(0, 8), pages, keyboard, System.currentTimeMillis());
            screens.put(chatId, screen);
            editSingle(chatId, messageId, pages.get(0), pageKeyboard(screen, 0));
        } else {
            screens.remove(chatId);
            editSingle(chatId, messageId, text, keyboard);
        }
    }

    private void editSingle(long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId(messageId);
        edit.setText(text);
        edit.setParseMode("HTML");
        edit.setReplyMarkup(keyboard);

        try {
            bot.execute(edit);
            log.debug("[SCREEN UPDATE] Edited msgId: {} for chatId: {}", messageId, chatId);
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
        var pages = dev.ix1ax.main.util.TelegramText.pages(text);
        for (int i = 0; i < pages.size(); i++) {
            var result = sendDirectPage(chatId, pages.get(i), i == pages.size() - 1 ? keyboard : null);
            if (result != DirectSendResult.SUCCESS) return result;
        }
        return DirectSendResult.SUCCESS;
    }

    private DirectSendResult sendDirectPage(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(text);
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

            if (TelegramErrorClassifier.isNetworkError(e)) return DirectSendResult.ERROR;
            if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("parse entities")) return DirectSendResult.ERROR;
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

    public DirectSendResult sendDirectMedia(long chatId, BroadcastMedia media) {
        try {
            var file = new org.telegram.telegrambots.meta.api.objects.InputFile(media.fileId());
            String chat = Long.toString(chatId);
            switch (media.type()) {
                case PHOTO -> bot.execute(org.telegram.telegrambots.meta.api.methods.send.SendPhoto.builder()
                        .chatId(chat).photo(file).caption(media.caption()).captionEntities(media.entities()).build());
                case VIDEO -> bot.execute(org.telegram.telegrambots.meta.api.methods.send.SendVideo.builder()
                        .chatId(chat).video(file).caption(media.caption()).captionEntities(media.entities()).supportsStreaming(true).build());
                case ANIMATION -> bot.execute(org.telegram.telegrambots.meta.api.methods.send.SendAnimation.builder()
                        .chatId(chat).animation(file).caption(media.caption()).captionEntities(media.entities()).build());
                case DOCUMENT -> bot.execute(org.telegram.telegrambots.meta.api.methods.send.SendDocument.builder()
                        .chatId(chat).document(file).caption(media.caption()).captionEntities(media.entities()).build());
            }
            return DirectSendResult.SUCCESS;
        } catch (Exception e) {
            if (TelegramErrorClassifier.isUserBlockedError(e)) return DirectSendResult.BLOCKED;
            if (TelegramErrorClassifier.isRateLimitError(e)) return DirectSendResult.RATE_LIMIT;
            log.warn("Не удалось отправить медиа в чат {}: {}", chatId, e.getMessage());
            return DirectSendResult.ERROR;
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

}
