package dev.ix1ax.main.service;

import dev.ix1ax.main.model.DaySchedule;
import dev.ix1ax.main.model.Lesson;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaseScheduleTest {
    @Test
    void baseScheduleKeepsOriginalLessonForGroupAndTeacher() {
        var parser = mock(ScheduleParserService.class);
        var changes = mock(ChangesParserService.class);
        when(parser.getDays()).thenReturn(new String[]{"Среда"});
        var group = new DaySchedule("Среда");
        group.addLesson(new Lesson(4, "14:30 - 16:00", "Исходный предмет", "Преподаватель", "302", "red"));
        var teacher = new DaySchedule("Среда");
        teacher.addLesson(new Lesson(4, "14:30 - 16:00", "Исходный предмет", "4-ИС1", "302", "red"));
        when(parser.getScheduleForGroup("4-ИС1")).thenReturn(Map.of("Среда", group));
        when(parser.getScheduleForTeacher("Преподаватель")).thenReturn(Map.of("Среда", teacher));
        var resolver = new ScheduleResolver(parser, changes);
        for (var result : List.of(resolver.getScheduleForGroup("4-ИС1", false), resolver.getScheduleForTeacher("Преподаватель", false))) {
            var lesson = result.get("Среда").getLessons().get(0);
            assertEquals("Исходный предмет", lesson.getSubject());
            assertEquals("302", lesson.getRoom());
            assertFalse(lesson.isChanged());
            assertFalse(lesson.isCanceled());
            assertEquals("Пара", lesson.getType());
        }
        verify(changes, never()).getChangesForGroup(anyString());
        verify(changes, never()).getAllChanges();
        verify(changes, never()).getOverlayDayName();
    }
}
