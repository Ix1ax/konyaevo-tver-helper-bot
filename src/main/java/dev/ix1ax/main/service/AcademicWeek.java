package dev.ix1ax.main.service;

import dev.ix1ax.main.model.Lesson;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Первая неделя семестра — красная; смена цвета происходит в понедельник. */
public final class AcademicWeek {
    private AcademicWeek() {}

    public static String typeFor(LocalDate date) {
        int year = date.getYear();
        LocalDate start = date.getMonthValue() >= 9 ? LocalDate.of(year, 9, 1)
                : date.getMonthValue() == 1 ? LocalDate.of(year - 1, 9, 1)
                : LocalDate.of(year, 2, 1);
        long weeks = ChronoUnit.WEEKS.between(start.with(DayOfWeek.MONDAY), date.with(DayOfWeek.MONDAY));
        return weeks % 2 == 0 ? Lesson.WEEK_RED : Lesson.WEEK_BLUE;
    }
}
