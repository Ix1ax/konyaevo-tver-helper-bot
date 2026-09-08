package dev.ix1ax.main.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import dev.ix1ax.main.bot.MessageSender;
import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.repository.UserSettingsRepository;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sends daily change notifications to users who have enabled them.
 * Runs every minute, checks Moscow time, and sends changes to matching users.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final UserSettingsRepository userSettingsRepo;
    private final ChangesParserService changesParser;
    private final MessageSender messageSender;

    public NotificationService(UserSettingsRepository userSettingsRepo,
                               ChangesParserService changesParser,
                               MessageSender messageSender) {
        this.userSettingsRepo = userSettingsRepo;
        this.changesParser = changesParser;
        this.messageSender = messageSender;
    }

    /**
     * Runs every minute. Checks if current Moscow time (HH:mm) matches
     * any user's notification time, and sends them their changes.
     */
    @Scheduled(cron = "0 * * * * *")
    public void processNotifications() {
        String currentTime = LocalTime.now(MOSCOW).format(TIME_FORMAT);
        processNotificationsForTime(currentTime);
    }

    public void processNotificationsForTime(String currentTime) {
        List<UserSettings> users = userSettingsRepo.findByNotifyEnabledTrueAndNotifyTime(currentTime);
        if (users.isEmpty()) {
            return;
        }

        log.info("[NOTIFY] Processing {} notification(s) for time {}", users.size(), currentTime);

        int sent = 0;
        int blocked = 0;
        int skipped = 0;

        for (UserSettings user : users) {
            Long chatId = user.getChatId();
            String text = buildNotificationText(user);

            if (text == null) {
                skipped++;
                continue;
            }

            MessageSender.DirectSendResult result = messageSender.sendDirectMessage(chatId, text, null);
            if (result == MessageSender.DirectSendResult.SUCCESS) {
                sent++;
            } else if (result == MessageSender.DirectSendResult.BLOCKED) {
                blocked++;
                // Auto-disable notifications for users who blocked the bot
                user.setNotifyEnabled(false);
                userSettingsRepo.save(user);
            }

            // Throttle: 40ms between sends
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("[NOTIFY DONE] Time={}: sent={}, blocked={}, skipped={}", currentTime, sent, blocked, skipped);
    }

    /**
     * Build notification message text based on user role.
     */
    private String buildNotificationText(UserSettings user) {
        String role = user.getRole();

        if ("student".equals(role) && user.getGroupName() != null && !user.getGroupName().isBlank()) {
            return changesParser.getFormattedChanges(user.getGroupName());
        }

        if ("teacher".equals(role) && user.getTeacherName() != null && !user.getTeacherName().isBlank()) {
            return changesParser.getFormattedChangesForTeacher(user.getTeacherName());
        }

        // User hasn't completed setup — skip
        return null;
    }
}
