package dev.ix1ax.main.service;

import dev.ix1ax.main.repository.UserActivityRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;

/** Считаем активных пользователей, ограничивая запись в БД одним разом за пять минут. */
@Service
public class UserActivityService {
    private static final Logger log = LoggerFactory.getLogger(UserActivityService.class);
    private final UserActivityRepository repository;
    private final ConcurrentHashMap<Long, Long> recorded = new ConcurrentHashMap<>();
    private volatile Snapshot cached;
    public record Snapshot(long day, long week, long month, long newWeek, long cachedAt) {}
    private final MascotService mascot;
    private final dev.ix1ax.main.repository.ActivityDayRepository days;
    /** Кому день уже отмечен для графика активности: повторные заходы не пишут в базу. */
    private final ConcurrentHashMap<Long, LocalDate> markedDay = new ConcurrentHashMap<>();
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    public UserActivityService(UserActivityRepository repository) { this(repository, null, null); }
    public UserActivityService(UserActivityRepository repository, MascotService mascot) { this(repository, mascot, null); }
    @org.springframework.beans.factory.annotation.Autowired
    public UserActivityService(UserActivityRepository repository, MascotService mascot, dev.ix1ax.main.repository.ActivityDayRepository days) {
        this.repository = repository; this.mascot = mascot; this.days = days;
    }

    public void record(long userId) {
        // Огонёк проверяется до ограничения частоты: активность сразу после полуночи засчитывает новый день.
        if (mascot != null) mascot.recordActivity(userId);
        markDay(userId, LocalDate.now(MOSCOW));
        long now = System.currentTimeMillis();
        Long previous = recorded.putIfAbsent(userId, now);
        if (previous != null && (now - previous < 300000 || !recorded.replace(userId, previous, now))) return;
        // Память ограничена даже при большом количестве разных пользователей.
        if (recorded.size() > 20000) recorded.entrySet().removeIf(entry -> now - entry.getValue() >= 300000);
        if (recorded.size() > 25000) recorded.clear();
        try {
            if (repository.touch(userId, Instant.ofEpochMilli(now)) == 0) recorded.remove(userId, now);
        } catch (Exception e) {
            recorded.remove(userId, now);
            log.warn("Не удалось записать активность пользователя {}", userId);
        }
    }

    void markDay(long userId, LocalDate today) {
        if (days == null || today.equals(markedDay.get(userId))) return;
        if (markedDay.size() > 25000) markedDay.clear();
        markedDay.put(userId, today);
        try { days.mark(today, userId); }
        catch (Exception e) { markedDay.remove(userId, today); log.warn("Не удалось отметить день активности пользователя {}", userId); }
    }

    /** При запуске отмечает сегодняшний день тем, кто уже заходил: история начинается без пробела. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void markToday() {
        if (days == null) return;
        LocalDate today = LocalDate.now(MOSCOW);
        try { repository.activeSince(today.atStartOfDay(MOSCOW).toInstant()).forEach(id -> markDay(id, today)); }
        catch (Exception e) { log.warn("Не удалось отметить сегодняшнюю активность: {}", e.getMessage()); }
    }

    public Snapshot snapshot() {
        long now = System.currentTimeMillis();
        Snapshot current = cached;
        if (current != null && now - current.cachedAt() < 15000) return current;
        synchronized (this) {
            current = cached;
            if (current != null && now - current.cachedAt() < 15000) return current;
            Instant time = Instant.ofEpochMilli(now);
            cached = new Snapshot(repository.countByLastActiveGreaterThanEqual(time.minus(Duration.ofDays(1))),
                    repository.countByLastActiveGreaterThanEqual(time.minus(Duration.ofDays(7))),
                    repository.countByLastActiveGreaterThanEqual(time.minus(Duration.ofDays(30))),
                    repository.countByFirstSeenGreaterThanEqual(time.minus(Duration.ofDays(7))), now);
            return cached;
        }
    }
}
