package dev.ix1ax.main.bot;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.apache.http.client.config.RequestConfig;
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
    private final dev.ix1ax.main.service.AdminService adminService;

    public KonyaevoBot(@Value("${bot.token}") String botToken,
                       @Value("${bot.base-url:https://api.telegram.org/bot}") String baseUrl,
                       @Value("${bot.max-threads:16}") int maxThreads,
                       @Value("${bot.proxy.type:NO_PROXY}") String proxyType,
                       @Value("${bot.proxy.host:127.0.0.1}") String proxyHost,
                       @Value("${bot.proxy.port:10808}") int proxyPort,
                       ScheduleService scheduleService,
                       CallbackRouter callbackRouter,
                       MessageSender messageSender,
                       dev.ix1ax.main.service.AdminService adminService) {
        super(createBotOptions(baseUrl, maxThreads, proxyType, proxyHost, proxyPort), botToken);
        this.scheduleService = scheduleService;
        this.callbackRouter = callbackRouter;
        this.messageSender = messageSender;
        this.adminService = adminService;

        // AbsSender has captured the fast senderConfig (5s socket timeout) from createBotOptions().
        // Now set pollingConfig (25s socket timeout) for DefaultBotSession long polling (15s getUpdates):
        RequestConfig pollingConfig = RequestConfig.custom()
                .setConnectTimeout(6000)
                .setSocketTimeout(25000)
                .setConnectionRequestTimeout(6000)
                .build();
        getOptions().setRequestConfig(pollingConfig);
    }

    private static DefaultBotOptions createBotOptions(String baseUrl, int maxThreads,
                                                     String proxyType, String proxyHost, int proxyPort) {
        DefaultBotOptions options = new DefaultBotOptions();
        options.setMaxThreads(Math.max(4, maxThreads));
        // Long polling timeout: 15 seconds (Telegram returns clean HTTP 200 [] on idle)
        options.setGetUpdatesTimeout(15);

        // Fast timeout for message sending / editing: 5s socket timeout
        // Normal Telegram API responses take ~100ms. If it hangs, fail fast in 5s instead of 60s.
        RequestConfig senderConfig = RequestConfig.custom()
                .setConnectTimeout(4000)
                .setSocketTimeout(5000)
                .setConnectionRequestTimeout(4000)
                .build();
        options.setRequestConfig(senderConfig);

        // Proxy configuration (e.g. SOCKS5 via local Xray / Happ daemon)
        if (proxyType != null && !proxyType.equalsIgnoreCase("NO_PROXY") && !proxyType.isBlank()) {
            if ("SOCKS5".equalsIgnoreCase(proxyType.trim())) {
                options.setProxyType(DefaultBotOptions.ProxyType.SOCKS5);
            } else if ("HTTP".equalsIgnoreCase(proxyType.trim())) {
                options.setProxyType(DefaultBotOptions.ProxyType.HTTP);
            } else if ("SOCKS4".equalsIgnoreCase(proxyType.trim())) {
                options.setProxyType(DefaultBotOptions.ProxyType.SOCKS4);
            }
            options.setProxyHost(proxyHost != null ? proxyHost.trim() : "127.0.0.1");
            options.setProxyPort(proxyPort > 0 ? proxyPort : 10808);
            log.info("[BOT CONFIG] Using {} proxy: {}:{}", options.getProxyType(), options.getProxyHost(), options.getProxyPort());
        }

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
        log.info("[BOT CONFIG] Telegram executor thread pool size: {}", options.getMaxThreads());
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

        if (text == null || text.isBlank()) {
            return;
        }

        String trimmed = text.trim();

        // Admin commands
        if (adminService.isAdmin(chatId)) {
            if (trimmed.equals("/admin")) {
                callbackRouter.sendAdminMenu(chatId);
                return;
            }
            if (trimmed.equals("/stats")) {
                callbackRouter.sendAdminStats(chatId);
                return;
            }
            if (trimmed.equals("/refresh")) {
                callbackRouter.sendAdminRefresh(chatId);
                return;
            }
            if (trimmed.equals("/broadcast")) {
                String help = "📢 <b>Рассылка сообщений</b>\n\n" +
                        "Используйте команду:\n" +
                        "<code>/broadcast [текст сообщения]</code>\n\n" +
                        "<i>Поддерживается HTML-разметка Telegram. Перед отправкой бот покажет предпросмотр сообщения и запросит подтверждение.</i>";
                messageSender.sendDirectMessage(chatId, help, KeyboardFactory.buildAdminBackKeyboard());
                return;
            }
            if (trimmed.startsWith("/broadcast ")) {
                String broadcastContent = trimmed.substring("/broadcast ".length()).trim();
                handleBroadcastDraft(chatId, broadcastContent);
                return;
            }
        }

        // Check if user is entering a custom notification time (e.g. "07:30", "17:53")
        if (trimmed.matches("^\\d{1,2}:\\d{2}$")) {
            if (callbackRouter.handleCustomTimeInput(chatId, trimmed)) {
                return;
            }
        }

        sendMainMenu(chatId);
    }

    private void handleBroadcastDraft(long chatId, String broadcastContent) {
        if (broadcastContent.isBlank()) {
            messageSender.sendDirectMessage(chatId,
                    "⚠️ <i>Текст рассылки не может быть пустым.</i>",
                    KeyboardFactory.buildAdminBackKeyboard());
            return;
        }

        var draft = adminService.createDraft(chatId, broadcastContent);
        long totalUsers = adminService.getTotalRecipients();

        String preview = "📢 <b>Предпросмотр рассылки</b>\n\n" +
                "──────────────────\n" +
                broadcastContent + "\n" +
                "──────────────────\n\n" +
                "👥 Получателей в базе: <b>" + totalUsers + "</b>\n\n" +
                "<i>Подтвердите отправку сообщения всем пользователям бота:</i>";

        messageSender.sendDirectMessage(chatId, preview,
                KeyboardFactory.buildBroadcastConfirmKeyboard(draft.id()));
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
