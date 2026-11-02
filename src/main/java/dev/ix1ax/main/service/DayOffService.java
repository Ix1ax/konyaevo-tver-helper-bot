package dev.ix1ax.main.service;

import dev.ix1ax.main.model.DayOff;
import dev.ix1ax.main.repository.DayOffRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.*;

/**
 * Праздники и каникулы: в эти дни огонёк Парыча отдыхает — не гаснет и не напоминает о себе.
 * Государственные праздники встроены, каникулы колледжа и переносы выходных добавляет администратор.
 */
@Service
public class DayOffService {
    static final String EXTRA = "Выходной";
    private static final Map<MonthDay, String> HOLIDAYS = Map.of(
            MonthDay.of(2, 23), "День защитника Отечества", MonthDay.of(3, 8), "Международный женский день",
            MonthDay.of(5, 1), "Праздник Весны и Труда", MonthDay.of(5, 9), "День Победы",
            MonthDay.of(6, 12), "День России", MonthDay.of(11, 4), "День народного единства",
            MonthDay.of(12, 31), "Новый год");
    /** Сколько дней подряд можно отметить за раз — с запасом на зимние каникулы. */
    public static final int MAX_PERIOD = 31;

    public record Period(String from, String to, String title, boolean builtIn) {}

    private final DayOffRepository repository;
    private volatile Set<LocalDate> days;

    public DayOffService(DayOffRepository repository) { this.repository = repository; }

    /** Встроенный праздник или каникулы по календарю; null — обычный день. */
    public static String holiday(LocalDate date) {
        int month = date.getMonthValue(), day = date.getDayOfMonth();
        if (month == 7 || month == 8) return "Летние каникулы";
        if (month == 1 && day <= 8) return "Новогодние каникулы";
        return HOLIDAYS.get(MonthDay.from(date));
    }

    private Set<LocalDate> days() {
        Set<LocalDate> current = days;
        if (current == null) {
            current = new HashSet<>();
            for (DayOff day : repository.findAll()) current.add(day.getDay());
            days = current;
        }
        return current;
    }

    /** Отмечен ли день администратором (встроенные праздники не в счёт). */
    public boolean isExtra(LocalDate date) { return date != null && days().contains(date); }

    /** Почему день выходной: название праздника, «Выходной» или null. */
    public String reason(LocalDate date) {
        String holiday = holiday(date);
        return holiday != null ? holiday : isExtra(date) ? EXTRA : null;
    }

    /** Праздники и выходные на ближайшие дни, подряд идущие склеены в периоды. */
    public List<Period> upcoming(LocalDate today, int ahead) {
        List<Period> result = new ArrayList<>();
        LocalDate start = null, previous = null;
        String title = null;
        for (LocalDate date = today; !date.isAfter(today.plusDays(ahead)); date = date.plusDays(1)) {
            String reason = reason(date);
            if (Objects.equals(reason, title) && reason != null) { previous = date; continue; }
            if (title != null) result.add(new Period(start.toString(), previous.toString(), title, !EXTRA.equals(title)));
            start = previous = date; title = reason;
        }
        if (title != null) result.add(new Period(start.toString(), previous.toString(), title, !EXTRA.equals(title)));
        return result;
    }

    public synchronized void add(LocalDate from, LocalDate to) {
        List<DayOff> added = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) added.add(new DayOff(date));
        repository.saveAll(added);
        days = null;
    }

    public synchronized void remove(LocalDate from, LocalDate to) {
        List<LocalDate> removed = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) removed.add(date);
        repository.deleteAllById(removed);
        days = null;
    }
}
