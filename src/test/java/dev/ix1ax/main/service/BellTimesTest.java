package dev.ix1ax.main.service;

import dev.ix1ax.main.model.DaySchedule;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BellTimesTest {
    @Test void everyGroupGetsAllBellSlotsEvenWhenItsSubjectCellIsEmpty() throws Exception {
        var parser = new ScheduleParserService();
        Map<String, Map<String, DaySchedule>> schedules = new LinkedHashMap<>();
        Map<String, List<String>> courses = new LinkedHashMap<>();
        var formatter = new DataFormatter();
        try (var input = getClass().getResourceAsStream("/audit/schedule-2026-09-30.xlsx");
             var workbook = new XSSFWorkbook(input)) {
            for (var sheet : workbook) {
                parser.parseSheetPoi(sheet, sheet.getSheetName(), schedules, courses, new TreeSet<>());
                String day = null;
                for (var row : sheet) {
                    if (row.getRowNum() == 0) continue;
                    String dayText = formatter.formatCellValue(row.getCell(0)).trim();
                    if (!dayText.isBlank()) day = dayText;
                    String time = formatter.formatCellValue(row.getCell(1)).trim();
                    String slot = formatter.formatCellValue(row.getCell(2)).trim();
                    if (day == null || time.isEmpty() || !slot.matches("[1-6]")) continue;
                    for (String group : courses.get(sheet.getSheetName())) {
                        assertEquals(time, schedules.get(group).get(day).getSlotTime(Integer.parseInt(slot)), group + " " + day + " " + slot);
                    }
                }
            }
        }
        ReflectionTestUtils.setField(parser, "scheduleByGroup", schedules);
        var changes = mock(ChangesParserService.class);
        when(changes.getTargetWeekType()).thenReturn("red");
        when(changes.teacherMatches(anyString(), eq("Смирнова О.А."))).thenReturn(true);
        var resolver = new ScheduleResolver(parser, changes);
        for (String day : parser.getDays()) {
            when(changes.getOverlayDayName()).thenReturn(day);
            for (String group : schedules.keySet()) {
                when(changes.getChangesForGroup(group)).thenReturn(Map.of(5, "История\nСмирнова О.А.\n404", 6, "История\nСмирнова О.А.\n404"));
                when(changes.getAllChanges()).thenReturn(Map.of(group, Map.of(5, "История\nСмирнова О.А.\n404", 6, "История\nСмирнова О.А.\n404")));
                for (int slot : List.of(5, 6)) {
                    String expected = schedules.get(group).get(day).getSlotTime(slot);
                    final int number = slot;
                    assertTrue(resolver.getScheduleForGroup(group).get(day).getLessons().stream()
                            .anyMatch(l -> l.getLessonNumber() == number && l.isChanged() && expected.equals(l.getTime())), group + " " + day);
                    assertTrue(resolver.getScheduleForTeacher("Смирнова О.А.").get(day).getLessons().stream()
                            .anyMatch(l -> l.getLessonNumber() == number && group.equals(l.getGroupName()) && expected.equals(l.getTime())), group + " teacher " + day);
                }
            }
        }
        assertEquals("15:45 - 17:15", schedules.get("1-БД").get("Пятница").getSlotTime(5));
        assertEquals("16:10 - 17:40", schedules.get("1-БД").get("Понедельник").getSlotTime(5));
        // Empty slots remain metadata, not phantom lessons.
        assertFalse(schedules.get("1-БД").get("Пятница").getLessons().stream().anyMatch(l -> l.getLessonNumber() == 5));
    }
}
