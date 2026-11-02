package dev.ix1ax.main.service;

import dev.ix1ax.main.dto.DayScheduleDto;
import dev.ix1ax.main.model.ShortDay;
import dev.ix1ax.main.repository.ShortDayRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

/**
 * Сокращённые пары: в отмеченные администратором дни у всех курсов одинаковое расписание звонков
 * (пары по 60 минут, перемены по 10). Время подменяется в расписании для приложения, бота и уведомлений.
 */
@Service
public class ShortDayService {
    /** Расписание звонков сокращённого дня (приказ № 88-кл от 28.09.2026), в формате таблицы колледжа. */
    public static final Map<Integer, String> TIMES = Map.of(1, "8:30 - 9:30", 2, "9:40 - 10:40", 3, "10:50 - 11:50",
            4, "12:00 - 13:00", 5, "13:10 - 14:10", 6, "14:20 - 15:20");

    private final ShortDayRepository repository;
    private volatile Set<LocalDate> days;

    public ShortDayService(ShortDayRepository repository) { this.repository = repository; }

    private Set<LocalDate> days() {
        Set<LocalDate> current = days;
        if (current == null) {
            current = new HashSet<>();
            for (ShortDay day : repository.findAll()) current.add(day.getDay());
            days = current;
        }
        return current;
    }

    public boolean isShort(LocalDate date) { return date != null && days().contains(date); }

    /** Сегодняшний и будущие сокращённые дни по порядку. */
    public List<LocalDate> upcoming(LocalDate today) {
        return days().stream().filter(day -> !day.isBefore(today)).sorted().toList();
    }

    public synchronized void add(LocalDate date) { repository.save(new ShortDay(date)); days = null; }

    public synchronized void remove(LocalDate date) { repository.deleteById(date); days = null; }

    /**
     * Подменяет время пар для сегодняшнего и завтрашнего дня, если они сокращённые.
     * Кеш расписания не трогается: возвращаются копии дней.
     */
    public Map<String, DayScheduleDto> apply(Map<String, DayScheduleDto> week, LocalDate today, String todayName, String tomorrowName) {
        if (week == null || week.isEmpty()) return week;
        Map<String, DayScheduleDto> result = null;
        for (var entry : List.of(Map.entry(todayName, today), Map.entry(tomorrowName, today.plusDays(1)))) {
            String name = entry.getKey();
            if (name == null || name.isBlank() || !isShort(entry.getValue()) || !week.containsKey(name)) continue;
            if (result == null) result = new LinkedHashMap<>(week);
            DayScheduleDto day = week.get(name);
            result.put(name, day.toBuilder().lessons(day.getLessons().stream()
                    .map(lesson -> lesson.toBuilder().time(TIMES.getOrDefault(lesson.getLessonNumber(), lesson.getTime())).build()).toList()).build());
        }
        return result == null ? week : result;
    }
}
