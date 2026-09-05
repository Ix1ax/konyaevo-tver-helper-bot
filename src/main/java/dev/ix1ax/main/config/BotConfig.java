package dev.ix1ax.main.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import dev.ix1ax.main.bot.KonyaevoBot;

@Configuration
public class BotConfig {

    private static final Logger log = LoggerFactory.getLogger(BotConfig.class);

    @Bean
    public TelegramBotsApi telegramBotsApi(KonyaevoBot bot) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        try {
            log.info("[STARTUP] Registering Konyaevo Bot (@{}) with Telegram API servers...", bot.getBotUsername());
            api.registerBot(bot);
            log.info("[STARTUP] Telegram bot registered successfully! Polling loop is active and waiting for updates.");
        } catch (TelegramApiException e) {
            log.error("[STARTUP ERROR] Cannot connect to Telegram servers or register bot: {}. Check internet connectivity and BOT_TOKEN.", e.getMessage());
            throw e;
        }
        return api;
    }
}
