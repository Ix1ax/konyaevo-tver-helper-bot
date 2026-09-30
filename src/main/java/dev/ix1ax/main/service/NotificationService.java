package dev.ix1ax.main.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import dev.ix1ax.main.bot.MessageSender;
import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.repository.UserSettingsRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
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

    private final ScheduleService scheduleService;

    public NotificationService(UserSettingsRepository userSettingsRepo,
                               ChangesParserService changesParser,
                               MessageSender messageSender) {
        this(userSettingsRepo, changesParser, messageSender, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationService(UserSettingsRepository userSettingsRepo, ChangesParserService changesParser,
                               MessageSender messageSender, ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
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
        DayOfWeek currentDay = LocalDate.now(MOSCOW).getDayOfWeek();
        processNotificationsForTime(currentTime, currentDay);
        processTomorrowNotifications(currentTime, currentDay);
    }

    public void processTomorrowNotifications(String time, DayOfWeek day) {
        for (UserSettings user : userSettingsRepo.findByNotifyTomorrowTrueAndNotifyTime(time)) {
            if (!user.isNotifyDayEnabled(day.getValue())) continue;
            String tomorrow = scheduleService.getTomorrowName();
            String text;
            if ("student".equals(user.getRole()) && user.getGroupName() != null && !user.getGroupName().isBlank()) {
                text = tomorrow.isEmpty() ? "<b>Завтра выходной</b>\nЗанятий нет." : scheduleService.getScheduleTextForGroup(user.getGroupName(), tomorrow);
            } else if ("teacher".equals(user.getRole()) && user.getTeacherName() != null && !user.getTeacherName().isBlank()) {
                text = tomorrow.isEmpty() ? "<b>Завтра выходной</b>\nЗанятий нет." : scheduleService.getScheduleTextForTeacher(user.getTeacherName(), tomorrow);
            } else continue;
            if (messageSender.sendDirectMessage(user.getChatId(), "<b>Расписание на завтра</b>\n\n" + text, null)
                    == MessageSender.DirectSendResult.BLOCKED) {
                user.setNotifyTomorrow(false);
                userSettingsRepo.save(user);
            }
            try { Thread.sleep(40); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    public void processNotificationsForTime(String currentTime) {
        processNotificationsForTime(currentTime, LocalDate.now(MOSCOW).getDayOfWeek());
    }

    public void processNotificationsForTime(String currentTime, DayOfWeek currentDay) {
        List<UserSettings> users = userSettingsRepo.findByNotifyEnabledTrueAndNotifyTime(currentTime);
        if (users.isEmpty()) {
            return;
        }

        log.info("[NOTIFY] Processing {} notification(s) for time {} on {}", users.size(), currentTime, currentDay);

        int sent = 0;
        int blocked = 0;
        int skipped = 0;
        int dayValue = currentDay.getValue(); // 1 = Monday, 7 = Sunday

        for (UserSettings user : users) {
            if (!user.isNotifyDayEnabled(dayValue)) {
                skipped++;
                continue;
            }

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
