package dev.ix1ax.main.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import dev.ix1ax.main.bot.KeyboardFactory;
import dev.ix1ax.main.bot.MessageSender;
import dev.ix1ax.main.repository.UserSettingsRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service managing admin authorization, statistics reporting,
 * broadcast message delivery with rate limiting, and cache refreshes.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    @Value("${bot.admin-ids:1669683599}")
    private String adminIdsConfig;

    private final Set<Long> adminIds = ConcurrentHashMap.newKeySet();
    private final Map<String, BroadcastDraft> drafts = new ConcurrentHashMap<>();
    private final AtomicBoolean broadcastRunning = new AtomicBoolean(false);

    private final UserSettingsRepository userSettingsRepo;
    private final ScheduleParserService scheduleParser;
    private final ChangesParserService changesParser;
    private final ScheduleService scheduleService;
    private final MessageSender messageSender;
    private final UserActivityService activity;

    public AdminService(UserSettingsRepository userSettingsRepo,
                        ScheduleParserService scheduleParser,
                        ChangesParserService changesParser,
                        ScheduleService scheduleService,
                        MessageSender messageSender) {
        this(userSettingsRepo, scheduleParser, changesParser, scheduleService, messageSender, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminService(UserSettingsRepository userSettingsRepo, ScheduleParserService scheduleParser,
                        ChangesParserService changesParser, ScheduleService scheduleService,
                        MessageSender messageSender, UserActivityService activity) {
        this.activity = activity;
        this.userSettingsRepo = userSettingsRepo;
        this.scheduleParser = scheduleParser;
        this.changesParser = changesParser;
        this.scheduleService = scheduleService;
        this.messageSender = messageSender;
    }

    @PostConstruct
    public void init() {
        if (adminIdsConfig != null && !adminIdsConfig.isBlank()) {
            for (String rawId : adminIdsConfig.split(",")) {
                try {
                    adminIds.add(Long.parseLong(rawId.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        // Always ensure primary admin ID 1669683599
        adminIds.add(1669683599L);
        log.info("[ADMIN INIT] Configured admin chat IDs: {}", adminIds);
    }

    /**
     * Check if the specified chat ID belongs to an administrator.
     */
    public boolean isAdmin(Long chatId) {
        return chatId != null && adminIds.contains(chatId);
    }

    // ===== Statistics =====

    /**
     * Generate detailed system and user statistics formatted in HTML.
     */
    public String getStatsReport() {
        long totalUsers = userSettingsRepo.count();
        long studentCount = userSettingsRepo.countByRole("student");
        long teacherCount = userSettingsRepo.countByRole("teacher");
        long notifyCount = userSettingsRepo.countWithNotifications();
        long unconfiguredCount = Math.max(0, totalUsers - studentCount - teacherCount);

        long course1 = userSettingsRepo.countByCourse(1);
        long course2 = userSettingsRepo.countByCourse(2);
        long course3 = userSettingsRepo.countByCourse(3);
        long course4 = userSettingsRepo.countByCourse(4);

        List<Object[]> topGroups = userSettingsRepo.findTopGroups(PageRequest.of(0, 5));

        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemoryMb = runtime.maxMemory() / (1024 * 1024);

        String currentWeek = scheduleService.getCurrentWeekBadge();
        int totalScheduleGroups = scheduleParser.getGroupsByCourse().values().stream()
                .mapToInt(List::size)
                .sum();
        int totalTeachers = scheduleParser.getAllTeachers().size();

        StringBuilder sb = new StringBuilder();
        sb.append("<b>Панель статистики Коняево-бота</b>\n\n");

        sb.append("<b>Пользователи:</b>\n");
        sb.append(" • Всего в базе: <b>").append(totalUsers).append("</b>\n");
        sb.append(" • Студенты: <b>").append(studentCount).append("</b>\n");
        sb.append(" • Преподаватели: <b>").append(teacherCount).append("</b>\n");
        sb.append(" • С уведомлениями: <b>").append(notifyCount).append("</b>\n");
        if (unconfiguredCount > 0) {
            sb.append(" • В процессе выбора: <b>").append(unconfiguredCount).append("</b>\n");
        }

        if (activity != null) {
            var active = activity.snapshot();
            sb.append("\n<b>Активность:</b>\n")
                    .append(" • За 24 часа: <b>").append(active.day()).append("</b>\n")
                    .append(" • За 7 дней: <b>").append(active.week()).append("</b>\n")
                    .append(" • За 30 дней: <b>").append(active.month()).append("</b>\n")
                    .append(" • Новых за 7 дней: <b>").append(active.newWeek()).append("</b>\n");
        }
        sb.append("\n<b>Студенты по курсам:</b>\n");
        sb.append(" • 1 курс: <b>").append(course1).append("</b>\n");
        sb.append(" • 2 курс: <b>").append(course2).append("</b>\n");
        sb.append(" • 3 курс: <b>").append(course3).append("</b>\n");
        sb.append(" • 4 курс: <b>").append(course4).append("</b>\n");

        if (!topGroups.isEmpty()) {
            sb.append("\n<b>ТОП-5 групп по подписчикам:</b>\n");
            int rank = 1;
            for (Object[] row : topGroups) {
                String groupName = (String) row[0];
                long count = (Long) row[1];
                sb.append(" ").append(rank++).append(". <b>").append(groupName).append("</b> — ")
                        .append(count).append(" чел.\n");
            }
        }

        sb.append("\n<b>Данные расписания:</b>\n");
        sb.append(" • Неделя: <b>").append(currentWeek).append("</b>\n");
        sb.append(" • Всего групп в расписании: <b>").append(totalScheduleGroups).append("</b>\n");
        sb.append(" • Преподавателей: <b>").append(totalTeachers).append("</b>\n");

        sb.append("\n<b>Сервер:</b>\n");
        sb.append(" • Память JVM: <b>").append(usedMemoryMb).append(" MB / ").append(maxMemoryMb).append(" MB</b>\n");

        return sb.toString();
    }

    // ===== Cache Refresh =====

    /**
     * Refresh Google Sheets cache for schedule and changes immediately.
     */
    public void refreshCache() {
        log.info("[ADMIN ACTION] Manual refresh of schedule and changes requested");
        scheduleParser.refreshSchedule();
        changesParser.refreshChanges();
    }

    // ===== Broadcast System =====

    public record BroadcastDraft(String id, long adminChatId, String text, long createdAt, dev.ix1ax.main.bot.BroadcastMedia media) {
        public BroadcastDraft(String id, long adminChatId, String text) {
            this(id, adminChatId, text, System.currentTimeMillis(), null);
        }
    }

    public BroadcastDraft createDraft(long adminChatId, String text) {
        return createDraft(adminChatId, text, null);
    }

    public BroadcastDraft createMediaDraft(long adminChatId, dev.ix1ax.main.bot.BroadcastMedia media) {
        return createDraft(adminChatId, "", media);
    }

    private BroadcastDraft createDraft(long adminChatId, String text, dev.ix1ax.main.bot.BroadcastMedia media) {
        if (!isAdmin(adminChatId)) throw new IllegalArgumentException("Рассылка доступна только администратору");
        // Purge drafts older than 1 hour
        long now = System.currentTimeMillis();
        drafts.entrySet().removeIf(e -> now - e.getValue().createdAt() > 3600_000);

        String id = UUID.randomUUID().toString();
        BroadcastDraft draft = new BroadcastDraft(id, adminChatId, text, now, media);
        drafts.put(id, draft);
        return draft;
    }

    public BroadcastDraft getDraft(String id) {
        BroadcastDraft draft = drafts.get(id);
        if (draft != null && System.currentTimeMillis() - draft.createdAt() > 3600_000) { drafts.remove(id); return null; }
        return draft;
    }

    public void removeDraft(String id) {
        drafts.remove(id);
    }

    public boolean isBroadcastRunning() {
        return broadcastRunning.get();
    }

    public long getTotalRecipients() {
        return userSettingsRepo.count();
    }

    /**
     * Asynchronously execute broadcast to all registered chat IDs in database.
     */
    public void startBroadcast(String draftId, int statusMessageId) {
        BroadcastDraft draft = getDraft(draftId);
        if (draft == null) {
            log.warn("[BROADCAST] Cannot start: draft {} not found", draftId);
            return;
        }

        long adminChatId = draft.adminChatId();
        String text = draft.text();

        if (!broadcastRunning.compareAndSet(false, true)) {
            messageSender.editMessage(adminChatId, statusMessageId,
                    "<i>В данный момент уже запущена другая рассылка. Пожалуйста, дождитесь её окончания.</i>",
                    KeyboardFactory.buildAdminBackKeyboard());
            return;
        }

        removeDraft(draftId);
        messageSender.editMessage(adminChatId, statusMessageId,
                "<b>Рассылка запущена...</b>\n\nИдёт отправка сообщений пользователям бота. Это может занять некоторое время.",
                null);

        CompletableFuture.runAsync(() -> {
            try {
            long startTime = System.currentTimeMillis();
            List<Long> chatIds = userSettingsRepo.findAllChatIds();
            int total = chatIds.size();
            int sent = 0;
            int blocked = 0;
            int failed = 0;

            log.info("[BROADCAST] Starting broadcast to {} recipients by admin {}", total, adminChatId);

            for (Long targetChatId : chatIds) {
                if (targetChatId == null) continue;

                MessageSender.DirectSendResult res = deliver(targetChatId, draft);
                if (res == MessageSender.DirectSendResult.SUCCESS) {
                    sent++;
                } else if (res == MessageSender.DirectSendResult.BLOCKED) {
                    blocked++;
                } else if (res == MessageSender.DirectSendResult.RATE_LIMIT) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    // Retry once after rate limit wait
                    MessageSender.DirectSendResult retryRes = deliver(targetChatId, draft);
                    if (retryRes == MessageSender.DirectSendResult.SUCCESS) {
                        sent++;
                    } else if (retryRes == MessageSender.DirectSendResult.BLOCKED) {
                        blocked++;
                    } else {
                        failed++;
                    }
                } else {
                    failed++;
                }

                // Throttle: 40ms sleep ≈ 25 messages/sec (under Telegram's 30/s limit)
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }


            double duration = (System.currentTimeMillis() - startTime) / 1000.0;
            log.info("[BROADCAST COMPLETE] Sent to {} users (success={}, blocked={}, failed={}) in {}s",
                    total, sent, blocked, failed, String.format("%.1f", duration));

            String report = "<b>Отчёт о рассылке</b>\n\n" +
                    "Время выполнения: <b>" + String.format("%.1f", duration) + " сек.</b>\n" +
                    "Всего получателей: <b>" + total + "</b>\n" +
                    "Успешно доставлено: <b>" + sent + "</b>\n" +
                    "Заблокировали бота: <b>" + blocked + "</b>\n" +
                    "Ошибок отправки: <b>" + failed + "</b>";

            messageSender.sendDirectMessage(adminChatId, report, KeyboardFactory.buildAdminBackKeyboard());
            } catch (Exception e) {
                log.error("Рассылка завершилась с ошибкой", e);
                messageSender.sendDirectMessage(adminChatId, "<b>Рассылка прервана</b>\nНе удалось завершить отправку. Проверьте журнал сервера.", KeyboardFactory.buildAdminBackKeyboard());
            } finally {
                broadcastRunning.set(false);
                removeDraft(draftId);
            }
        });
    }    private MessageSender.DirectSendResult deliver(long chatId, BroadcastDraft draft) {
        return draft.media() == null ? messageSender.sendDirectMessage(chatId, draft.text(), null)
                : messageSender.sendDirectMedia(chatId, draft.media());
    }

}
