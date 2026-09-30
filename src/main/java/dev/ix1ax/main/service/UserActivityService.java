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
    public UserActivityService(UserActivityRepository repository) { this.repository = repository; }

    public void record(long userId) {
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
