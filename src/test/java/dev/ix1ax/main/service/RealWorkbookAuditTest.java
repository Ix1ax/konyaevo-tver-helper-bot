package dev.ix1ax.main.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ix1ax.main.model.DaySchedule;
import dev.ix1ax.main.model.Lesson;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen public source plus an independently extracted XML reference, no network calls. */
class RealWorkbookAuditTest {
    @Test void matchesEveryPopulatedCellAcrossAllFourCourses() throws Exception {
        var parser = new ScheduleParserService();
        Map<String, Map<String, DaySchedule>> schedules = new LinkedHashMap<>();
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Set<String> teachers = new TreeSet<>();
        try (var in = getClass().getResourceAsStream("/audit/schedule-2026-09-30.xlsx");
             var workbook = new XSSFWorkbook(in)) {
            assertEquals(4, workbook.getNumberOfSheets());
            for (var sheet : workbook) parser.parseSheetPoi(sheet, sheet.getSheetName(), schedules, groups, teachers);
        }
        var reference = new ObjectMapper().readTree(getClass().getResourceAsStream("/audit/expected-schedule.json"));
        assertEquals(953, reference.size());
        assertEquals(reference.size(), schedules.values().stream().flatMap(m -> m.values().stream())
                .mapToInt(d -> d.getLessons().size()).sum());
        List<org.junit.jupiter.api.function.Executable> checks = new ArrayList<>();
        for (var expected : reference) {
            String group = expected.get("group").asText();
            String day = expected.get("day").asText();
            String label = "course " + expected.get("course") + " cell " + expected.get("cell") + " " + group;
            checks.add(() -> {
                List<Lesson> matches = schedules.get(group).get(day).getLessons().stream()
                        .filter(l -> l.getLessonNumber() == expected.get("slot").asInt()
                                && l.getSubject().equals(expected.get("subject").asText())
                                && l.getTeacher().equals(expected.get("teacher").asText())).toList();
                assertEquals(1, matches.size(), label);
                var lesson = matches.get(0);
                assertEquals(expected.get("time").asText(), lesson.getTime(), label);
                assertEquals(expected.get("room").asText(), lesson.getRoom(), label);
                assertEquals(expected.get("week").isNull() ? null : expected.get("week").asText(), lesson.getWeekType(), label);
            });
        }
        assertAll(checks);
        ReflectionTestUtils.setField(parser, "scheduleByGroup", schedules);
        assertTrue(parser.getAllRooms().contains("с/з"));
        assertFalse(parser.getAllRooms().contains("с"));
        assertFalse(parser.getAllRooms().contains("з"));
    }
    @Test void resolvesAllRealChangesAndClassroomOccupancy() throws Exception {
        var parser = new ScheduleParserService();
        Map<String, Map<String, DaySchedule>> schedules = new LinkedHashMap<>();
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Set<String> teachers = new TreeSet<>();
        try (var in = getClass().getResourceAsStream("/audit/schedule-2026-09-30.xlsx");
             var workbook = new XSSFWorkbook(in)) {
            for (var sheet : workbook) parser.parseSheetPoi(sheet, sheet.getSheetName(), schedules, groups, teachers);
        }
        ReflectionTestUtils.setField(parser, "scheduleByGroup", schedules);
        ReflectionTestUtils.setField(parser, "groupsByCourse", groups);
        ReflectionTestUtils.setField(parser, "allTeachers", teachers);
        var method = ScheduleParserService.class.getDeclaredMethod("buildTeacherSchedule", Map.class);
        method.setAccessible(true);
        ReflectionTestUtils.setField(parser, "scheduleByTeacher", method.invoke(parser, schedules));
        var changes = new ChangesParserService(parser, java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-30T15:00:00Z"), java.time.ZoneId.of("Europe/Moscow")));
        List<String[]> rows;
        try (var reader = new com.opencsv.CSVReader(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/audit/changes-2026-10-01.csv"), java.nio.charset.StandardCharsets.UTF_8))) {
            rows = reader.readAll();
        }
        changes.parseChangesCsv(rows);
        var service = new ScheduleService(parser, changes, null);
        assertEquals(15, changes.getAllChanges().size());
        assertEquals(28, changes.getAllChanges().values().stream().mapToInt(Map::size).sum());
        assertEquals("Четверг", changes.getOverlayDayName());
        for (String[] row : rows.subList(2, rows.size())) {
            if (row[0].isBlank()) continue;
            var lessons = service.getResolvedGroupSchedule(row[0]).get("Четверг").getLessons();
            for (int slot = 1; slot <= 6; slot++) {
                if (row[slot].isBlank()) continue;
                final int number = slot;
                var actual = lessons.stream().filter(l -> l.getLessonNumber() == number && l.isChanged()).toList();
                assertEquals(1, actual.size(), row[0] + " slot " + slot);
                var lesson = actual.get(0);
                if (row[slot].equals("ОТМЕНА")) { assertTrue(lesson.isCanceled()); continue; }
                String[] lines = row[slot].trim().split("\n");
                assertEquals(lines[0], lesson.getSubject());
                assertEquals(lines[1], lesson.getTeacher());
                assertEquals(lines.length > 2 ? lines[2].replace("ауд.", "").trim() : "", lesson.getRoom());
                for (String name : lines[1].split(",\\s*")) {
                    var teacherLessons = service.getResolvedTeacherSchedule(name).get("Четверг").getLessons();
                    assertTrue(teacherLessons.stream().anyMatch(l -> l.getLessonNumber() == number && l.isChanged()
                            && !l.isCanceled() && l.getGroupName().equals(row[0])
                            && l.getRoom().equals(dev.ix1ax.main.util.Subgroups.teacherRoom(lines[1], lesson.getRoom(), name))), name);
                }
            }
        }
        assertTrue(service.getAllTeachers().contains("Янникова А.А."));
        assertTrue(service.getTeachersByLetter("Я").contains("Янникова А.А."));
        var controller = new dev.ix1ax.main.controller.ScheduleApiController(service, parser, changes);
        for (String week : List.of("red", "blue")) {
            for (int slot = 1; slot <= 6; slot++) {
                Set<String> occupied = new TreeSet<>();
                final int number = slot;
                for (String group : schedules.keySet()) {
                    for (var l : service.getResolvedGroupSchedule(group).get("Четверг").getLessons()) {
                        if (l.getLessonNumber() == number && !l.isCanceled() && (l.getWeekType() == null || l.getWeekType().equals(week)))
                            occupied.addAll(dev.ix1ax.main.util.Subgroups.rooms(l.getRoom()));
                    }
                }
                var result = controller.getFreeClassrooms("Четверг", slot, week).getBody();
                assertNotNull(result);
                assertEquals(occupied, new TreeSet<>(result.getOccupiedRooms().stream().map(r -> r.getRoom()).toList()));
                assertTrue(Collections.disjoint(result.getFreeRooms(), occupied));
            }
        }
    }

}
