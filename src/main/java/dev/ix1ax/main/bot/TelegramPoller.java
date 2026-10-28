package dev.ix1ax.main.bot;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.methods.updates.GetUpdates;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.util.List;

/**
 * Long polling в отдельном потоке после запуска приложения. API и Mini App работают сразу,
 * даже если Telegram пока недоступен ни одним маршрутом: опрос повторяется с паузой до 30 секунд.
 */
@Component
@ConditionalOnProperty(name = "bot.enabled", havingValue = "true", matchIfMissing = true)
public class TelegramPoller {
    private static final Logger log = LoggerFactory.getLogger(TelegramPoller.class);
    private final TelegramRoutes telegram;
    private final KonyaevoBot bot;
    private volatile boolean running;
    private Thread thread;
    private boolean connected;
    private int offset;

    public TelegramPoller(TelegramRoutes telegram, KonyaevoBot bot) {
        this.telegram = telegram;
        this.bot = bot;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        running = true;
        thread = new Thread(this::loop, "telegram-polling");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        log.info("[STARTUP] Connecting @{} to Telegram: {}", bot.getBotUsername(), telegram.describe());
        int failures = 0;
        while (running) {
            try {
                pollOnce();
                if (failures > 0) log.info("[TELEGRAM] Connection restored via {}", telegram.activeRoute());
                failures = 0;
            } catch (Exception e) {
                if (!running) break;
                failures++;
                long delay = backoff(failures);
                // Первая ошибка и затем каждая десятая, чтобы долгий обрыв не засыпал журнал.
                if (failures == 1 || failures % 10 == 0)
                    log.warn("[TELEGRAM] Telegram unreachable (attempt {}), retry in {} s: {}", failures, delay / 1000, TelegramRoutes.reason(e));
                try { Thread.sleep(delay); } catch (InterruptedException interrupted) { break; }
            }
        }
    }

    static long backoff(int failures) {
        return Math.min(30_000, 1000L << Math.min(failures, 5));
    }

    void pollOnce() throws TelegramApiException {
        if (!connected) {
            // Как раньше при registerBot: снимаем webhook, иначе getUpdates вернёт конфликт.
            telegram.poll(sender -> sender.execute(new DeleteWebhook()));
            connected = true;
            log.info("[STARTUP] Telegram bot is connected via {}. Polling loop is active.", telegram.activeRoute());
        }
        List<Update> updates = telegram.poll(sender -> sender.execute(GetUpdates.builder()
                .offset(offset).limit(100).timeout(TelegramRoutes.POLL_TIMEOUT).build()));
        for (Update update : updates) {
            offset = Math.max(offset, update.getUpdateId() + 1);
            bot.onUpdateReceived(update);
        }
    }
}
