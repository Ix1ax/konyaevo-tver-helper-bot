package dev.ix1ax.main.bot;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.service.ScheduleService;

/**
 * Main Telegram bot class — thin router.
 * Delegates callback handling to {@link CallbackRouter} and message sending to {@link MessageSender}.
 */
@Component
public class KonyaevoBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(KonyaevoBot.class);

    @Value("${bot.username}")
    private String botUsername;

    private final ScheduleService scheduleService;
    private final CallbackRouter callbackRouter;
    private final MessageSender messageSender;

    public KonyaevoBot(@Value("${bot.token}") String botToken,
                       @Value("${bot.base-url:https://api.telegram.org/bot}") String baseUrl,
                       ScheduleService scheduleService,
                       CallbackRouter callbackRouter,
                       MessageSender messageSender) {
        super(createBotOptions(baseUrl), botToken);
        this.scheduleService = scheduleService;
        this.callbackRouter = callbackRouter;
        this.messageSender = messageSender;
    }

    private static DefaultBotOptions createBotOptions(String baseUrl) {
        DefaultBotOptions options = new DefaultBotOptions();
        if (baseUrl != null && !baseUrl.isBlank()) {
            String trimmed = baseUrl.trim();
            if (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            if (!trimmed.endsWith("/bot")) {
                trimmed = trimmed + "/bot";
            }
            options.setBaseUrl(trimmed);
            log.info("[BOT CONFIG] Using Telegram base URL: {}", trimmed);
        }
        return options;
    }

    @PostConstruct
    public void init() {
        messageSender.init(this);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMyChatMember()) {
                handleMyChatMember(update);
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update);
            } else if (update.hasCallbackQuery()) {
                handleCallback(update);
            }
        } catch (Exception e) {
            if (TelegramErrorClassifier.isUserBlockedError(e)) {
                log.warn("[BOT BLOCKED] Update ignored because bot is blocked by user");
            } else if (TelegramErrorClassifier.isNetworkError(e)) {
                log.warn("[NETWORK ERROR] Telegram servers unreachable: {}", e.getMessage());
            } else {
                log.error("Unhandled error processing Telegram update", e);
            }
        }
    }

    // ===== Update handlers =====

    private void handleMyChatMember(Update update) {
        var myChatMember = update.getMyChatMember();
        long chatId = myChatMember.getChat().getId();
        String userTag = myChatMember.getFrom() != null && myChatMember.getFrom().getUserName() != null
                ? "@" + myChatMember.getFrom().getUserName()
                : "chatId:" + chatId;
        String newStatus = myChatMember.getNewChatMember().getStatus();

        if ("kicked".equalsIgnoreCase(newStatus)) {
            log.warn("[BOT BLOCKED] User {} blocked the bot. Resetting session.", userTag);
            scheduleService.resetUser(chatId);
        } else if ("member".equalsIgnoreCase(newStatus)) {
            log.info("[BOT UNBLOCKED] User {} unblocked the bot.", userTag);
        }
    }

    private void handleTextMessage(Update update) {
        String text = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        String sender = update.getMessage().getFrom() != null
                ? (update.getMessage().getFrom().getUserName() != null
                    ? "@" + update.getMessage().getFrom().getUserName()
                    : update.getMessage().getFrom().getFirstName())
                : "id:" + chatId;

        log.info("[USER MESSAGE] ChatId: {} ({}) sent text: '{}'", chatId, sender, text);

        if (text != null && !text.isBlank()) {
            sendMainMenu(chatId);
        }
    }

    private void handleCallback(Update update) {
        var callback = update.getCallbackQuery();

        callbackRouter.route(callback);

        // Answer callback to remove loading indicator
        try {
            execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callback.getId())
                    .build());
        } catch (Exception e) {
            if (!TelegramErrorClassifier.isUserBlockedError(e)
                    && !TelegramErrorClassifier.isNetworkError(e)) {
                log.debug("Failed to answer callback: {}", e.getMessage());
            }
        }
    }

    // ===== Auto-login: send initial message based on saved user settings =====

    private void sendMainMenu(long chatId) {
        UserSettings user = scheduleService.getOrCreateUser(chatId);

        // Auto-login: if user already has a saved group or teacher, go directly to their dashboard
        if ("student".equals(user.getRole()) && user.getGroupName() != null && !user.getGroupName().isBlank()) {
            messageSender.sendNewMessage(chatId,
                    callbackRouter.getStudentActionText(user.getGroupName()),
                    KeyboardFactory.buildStudentActionsKeyboard(user.getGroupName()),
                    "student dashboard (" + user.getGroupName() + ")");
            return;
        }
        if ("teacher".equals(user.getRole()) && user.getTeacherName() != null && !user.getTeacherName().isBlank()) {
            messageSender.sendNewMessage(chatId,
                    callbackRouter.getTeacherActionText(user.getTeacherName()),
                    KeyboardFactory.buildTeacherActionsKeyboard(user.getTeacherName()),
                    "teacher dashboard (" + user.getTeacherName() + ")");
            return;
        }

        messageSender.sendNewMessage(chatId,
                callbackRouter.getMainMenuText(),
                KeyboardFactory.buildRoleKeyboard(),
                "main menu");
    }
}
