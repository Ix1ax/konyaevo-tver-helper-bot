package dev.ix1ax.main.service;

import org.junit.jupiter.api.Test;
import dev.ix1ax.main.model.DaySchedule;
import dev.ix1ax.main.model.Lesson;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ScheduleParsingTest {

    @Test
    public void testLessonFormatting() {
        Lesson regular = new Lesson(1, "8:30 - 10:05", "МДК 01.04", "Евстигнеев А.С.", "307");
        String formatted = regular.format();
        assertTrue(formatted.contains("🔹 <b>1 пара</b> · <code>8:30 - 10:05</code>"));
        assertTrue(formatted.contains("📖 <b>МДК 01.04</b>"));
        assertTrue(formatted.contains("👨‍🏫 Евстигнеев А.С."));
        assertTrue(formatted.contains("📍 Ауд. <b>307</b>"));

        Lesson redLesson = new Lesson(2, "10:15 - 11:50", "МДК 01.02", "Комигачев К.А.", "310", Lesson.WEEK_RED);
        String redFormatted = redLesson.format();
        assertTrue(redFormatted.contains("🔴 <b>2 пара</b> · <code>10:15 - 11:50</code> · <i>Красная неделя</i>"));

        Lesson blueLesson = new Lesson(2, "10:15 - 11:50", "МДК 02.01", "Петрушенко Л.Л.", "310", Lesson.WEEK_BLUE);
        String blueFormatted = blueLesson.format();
        assertTrue(blueFormatted.contains("🔵 <b>2 пара</b> · <code>10:15 - 11:50</code> · <i>Синяя неделя</i>"));

        // Teacher format
        String teacherFormatted = regular.formatForTeacher("3-ИС4");
        assertTrue(teacherFormatted.contains("👥 Группа: <b>3-ИС4</b>"));
    }

    @Test
    public void testDayScheduleSorting() {
        DaySchedule schedule = new DaySchedule("Пятница");
        // Add lessons out of order
        schedule.addLesson(new Lesson(4, "14:05 - 15:35", "Технология проектирования", "Самсонов И.В.", "103"));
        schedule.addLesson(new Lesson(2, "10:15 - 11:50", "МДК 02.01", "Петрушенко Л.Л.", "310"));
        schedule.addLesson(new Lesson(1, "8:30 - 10:05", "МДК 01.04", "Евстигнеев А.С.", "307"));
        schedule.addLesson(new Lesson(3, "12:20 - 13:55", "МДК 02.02", "Бобков Д.И.", "302"));

        List<Lesson> lessons = schedule.getLessons();
        assertEquals(4, lessons.size());
        assertEquals(1, lessons.get(0).getLessonNumber());
        assertEquals(2, lessons.get(1).getLessonNumber());
        assertEquals(3, lessons.get(2).getLessonNumber());
        assertEquals(4, lessons.get(3).getLessonNumber());
    }

    @Test
    public void testDayScheduleRedBeforeBlueSorting() {
        DaySchedule schedule = new DaySchedule("Вторник");
        schedule.addLesson(new Lesson(1, "8:30 - 10:05", "МДК 02.01", "Петрушенко Л.Л.", "310", Lesson.WEEK_BLUE));
        schedule.addLesson(new Lesson(1, "8:30 - 10:05", "МДК 01.02", "Комигачев К.А.", "310", Lesson.WEEK_RED));

        List<Lesson> lessons = schedule.getLessons();
        assertEquals(2, lessons.size());
        assertEquals(Lesson.WEEK_RED, lessons.get(0).getWeekType());
        assertEquals(Lesson.WEEK_BLUE, lessons.get(1).getWeekType());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParseSubjectCellMultilineSubject() throws Exception {
        ScheduleParserService parser = new ScheduleParserService();
        Method parseMethod = ScheduleParserService.class.getDeclaredMethod("parseSubjectCell", String.class);
        parseMethod.setAccessible(true);

        // Long subject split across lines: should NOT be split into two lessons!
        String cell = "Технология проектирования\n информационных систем\nСамсонов И.В.";
        Object parsed = parseMethod.invoke(parser, cell);

        var entriesField = parsed.getClass().getDeclaredField("entries");
        entriesField.setAccessible(true);
        List<?> entries = (List<?>) entriesField.get(parsed);

        assertEquals(1, entries.size(), "Subject should NOT be split into two lessons!");
        Object entry = entries.get(0);

        var subjectField = entry.getClass().getDeclaredField("subject");
        subjectField.setAccessible(true);
        var teacherField = entry.getClass().getDeclaredField("teacher");
        teacherField.setAccessible(true);

        assertEquals("Технология проектирования информационных систем", subjectField.get(entry));
        assertEquals("Самсонов И.В.", teacherField.get(entry));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParseSubjectCellAlternatingWeeks() throws Exception {
        ScheduleParserService parser = new ScheduleParserService();
        Method parseMethod = ScheduleParserService.class.getDeclaredMethod("parseSubjectCell", String.class);
        parseMethod.setAccessible(true);

        String cell = "МДК 01.02\nКомигачев К.А.\nМДК 02.01\nПетрушенко Л.Л.";
        Object parsed = parseMethod.invoke(parser, cell);

        var entriesField = parsed.getClass().getDeclaredField("entries");
        entriesField.setAccessible(true);
        List<?> entries = (List<?>) entriesField.get(parsed);

        assertEquals(2, entries.size(), "Should parse into two entries for alternating weeks");

        Object entry1 = entries.get(0);
        Object entry2 = entries.get(1);

        var subjectField = entry1.getClass().getDeclaredField("subject");
        subjectField.setAccessible(true);
        var teacherField = entry1.getClass().getDeclaredField("teacher");
        teacherField.setAccessible(true);

        assertEquals("МДК 01.02", subjectField.get(entry1));
        assertEquals("Комигачев К.А.", teacherField.get(entry1));

        assertEquals("МДК 02.01", subjectField.get(entry2));
        assertEquals("Петрушенко Л.Л.", teacherField.get(entry2));
    }

    @Test
    public void testWeekBadgeFormat() {
        ScheduleService service = new ScheduleService(null, null, null);
        String badge = service.getCurrentWeekBadge();
        assertTrue(badge.equals("🔴 Красная неделя") || badge.equals("🔵 Синяя неделя"),
                "Week badge should be either Red or Blue week: " + badge);
    }

    @Test
    public void testParseSheetWithPoiBidyloTuesday() throws Exception {
        java.io.File file = new java.io.File("src/test/resources/schedule.xlsx");
        if (!file.exists()) return;

        ScheduleParserService parser = new ScheduleParserService();
        Map<String, Map<String, DaySchedule>> scheduleMap = new HashMap<>();
        Map<String, List<String>> groupsMap = new HashMap<>();
        Set<String> teachers = new TreeSet<>();

        org.apache.poi.ss.usermodel.DataFormatter df = new org.apache.poi.ss.usermodel.DataFormatter();
        try (org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(file)) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(0); // 1 курс

            Method parseSheetMethod = ScheduleParserService.class.getDeclaredMethod(
                    "parseSheetPoi",
                    org.apache.poi.ss.usermodel.Sheet.class,
                    String.class,
                    Map.class,
                    Map.class,
                    Set.class
            );
            parseSheetMethod.setAccessible(true);
            parseSheetMethod.invoke(parser, sheet, "1 курс", scheduleMap, groupsMap, teachers);
        }

        // Check 1-Ю2 Tuesday lesson 4
        DaySchedule u2Tuesday = scheduleMap.get("1-Ю2").get("Вторник");
        assertNotNull(u2Tuesday);
        Lesson u2Lesson4 = u2Tuesday.getLessons().stream()
                .filter(l -> l.getLessonNumber() == 4)
                .findFirst().orElse(null);
        assertNotNull(u2Lesson4);
        assertEquals("Математика", u2Lesson4.getSubject());
        assertEquals(Lesson.WEEK_RED, u2Lesson4.getWeekType(), "1-Ю2 lesson 4 must be RED week due to TOP alignment");

        // Check 1-Ю3 Tuesday lesson 4
        DaySchedule u3Tuesday = scheduleMap.get("1-Ю3").get("Вторник");
        assertNotNull(u3Tuesday);
        List<Lesson> u3Lessons4 = u3Tuesday.getLessons().stream()
                .filter(l -> l.getLessonNumber() == 4)
                .toList();
        assertEquals(2, u3Lessons4.size(), "1-Ю3 lesson 4 has 2 alternating lessons");
        assertEquals(Lesson.WEEK_RED, u3Lessons4.get(0).getWeekType());
        assertEquals("Литература", u3Lessons4.get(0).getSubject());
        assertEquals(Lesson.WEEK_BLUE, u3Lessons4.get(1).getWeekType());
        assertEquals("Математика", u3Lessons4.get(1).getSubject());

        // Build teacher schedule
        Method buildTeacherMethod = ScheduleParserService.class.getDeclaredMethod(
                "buildTeacherSchedule",
                Map.class
        );
        buildTeacherMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, DaySchedule>> teacherSchedule =
                (Map<String, Map<String, DaySchedule>>) buildTeacherMethod.invoke(parser, scheduleMap);

        DaySchedule bidyloTuesday = teacherSchedule.get("Бидыло С.И.").get("Вторник");
        assertNotNull(bidyloTuesday);
        System.out.println("=== Бидыло С.И. Вторник (Parsed from XLSX) ===");
        for (Lesson l : bidyloTuesday.getLessons()) {
            System.out.println(l.formatForTeacher(l.getTeacher()));
        }

        // Verify Tuesday lesson 4 has both 1-Ю2 (RED) and 1-Ю3 (BLUE)
        List<Lesson> bidyloL4 = bidyloTuesday.getLessons().stream()
                .filter(l -> l.getLessonNumber() == 4)
                .toList();
        assertEquals(2, bidyloL4.size());
        assertEquals(Lesson.WEEK_RED, bidyloL4.get(0).getWeekType());
        assertEquals("1-Ю2", bidyloL4.get(0).getTeacher()); // teacher field holds group name
        assertEquals(Lesson.WEEK_BLUE, bidyloL4.get(1).getWeekType());
        assertEquals("1-Ю3", bidyloL4.get(1).getTeacher());
    }

    @Test
    public void testTeacherChangesSorting() throws Exception {
        ChangesParserService service = new ChangesParserService();

        var dateField = ChangesParserService.class.getDeclaredField("changesDate");
        dateField.setAccessible(true);
        dateField.set(service, "7 сентября (понедельник)");

        var mapField = ChangesParserService.class.getDeclaredField("changesByGroup");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Map<Integer, String>> map = (Map<String, Map<Integer, String>>) mapField.get(service);

        // Put group 2-Ю3 first (with 3 пара)
        Map<Integer, String> u3 = new HashMap<>();
        u3.put(3, "ТГП\nПискарева Ж.М.\n212 ауд.");
        map.put("2-Ю3", u3);

        // Put group 2-Ю1 second (with 1 пара)
        Map<Integer, String> u1 = new HashMap<>();
        u1.put(1, "ТГП\nПискарева Ж.М.\n407 ауд.");
        map.put("2-Ю1", u1);

        String formatted = service.getFormattedChangesForTeacher("Пискарева Ж.М.");
        System.out.println("=== Formatted Teacher Changes ===");
        System.out.println(formatted);

        int idx1 = formatted.indexOf("1 пара");
        int idx3 = formatted.indexOf("3 пара");

        assertTrue(idx1 != -1, "Should contain 1 пара");
        assertTrue(idx3 != -1, "Should contain 3 пара");
        assertTrue(idx1 < idx3, "1 пара must appear before 3 пара in teacher changes!");
    }

    @Test
    public void testComprehensiveScheduleIntegrity() throws Exception {
        java.io.File file = new java.io.File("src/test/resources/schedule.xlsx");
        if (!file.exists()) return;

        ScheduleParserService parser = new ScheduleParserService();
        Map<String, Map<String, DaySchedule>> scheduleByGroup = new HashMap<>();
        Map<String, List<String>> groupsByCourse = new LinkedHashMap<>();
        Set<String> teachers = new TreeSet<>();

        String[] names = {"1 курс", "2 курс", "3 курс", "4 курс"};

        try (org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(file)) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(i);
                String courseName = i < names.length ? names[i] : sheet.getSheetName();
                parser.parseSheetPoi(sheet, courseName, scheduleByGroup, groupsByCourse, teachers);
            }
        }

        Method buildTeacherMethod = ScheduleParserService.class.getDeclaredMethod("buildTeacherSchedule", Map.class);
        buildTeacherMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, DaySchedule>> scheduleByTeacher =
                (Map<String, Map<String, DaySchedule>>) buildTeacherMethod.invoke(parser, scheduleByGroup);

        System.out.println("Total groups parsed: " + scheduleByGroup.size());
        System.out.println("Total teachers parsed: " + scheduleByTeacher.size());
        System.out.println("Teachers: " + teachers);
        assertTrue(scheduleByGroup.size() > 40, "Should parse at least 40 groups");
        assertTrue(scheduleByTeacher.size() > 50, "Should parse at least 50 teachers");

        int maxGroupWeekLen = 0;
        String maxGroupWeekName = "";
        for (var entry : scheduleByGroup.entrySet()) {
            String group = entry.getKey();
            StringBuilder weekSb = new StringBuilder();
            weekSb.append("🏛 <b>Группа: ").append(group).append("</b>\n");
            weekSb.append("🗓 <b>Расписание на неделю</b> (текущая: 🔴 Красная неделя)\n");
            for (String day : parser.getDays()) {
                weekSb.append("\n──────────────────\n\n");
                DaySchedule ds = entry.getValue().get(day);
                if (ds == null || !ds.hasLessons()) {
                    weekSb.append("<b>").append(day).append("</b>\n✨ <i>Пар нет — свободный день</i>");
                } else {
                    weekSb.append(ds.format());
                    // Verify strictly sorted
                    int prevNum = 0;
                    for (Lesson l : ds.getLessons()) {
                        assertTrue(l.getLessonNumber() >= prevNum,
                                "Lessons must be sorted in group " + group + " on " + day);
                        prevNum = l.getLessonNumber();
                    }
                }
            }
            int len = weekSb.length();
            if (len > maxGroupWeekLen) {
                maxGroupWeekLen = len;
                maxGroupWeekName = group;
            }
        }

        int maxTeacherWeekLen = 0;
        String maxTeacherWeekName = "";
        for (var entry : scheduleByTeacher.entrySet()) {
            String teacher = entry.getKey();
            StringBuilder weekSb = new StringBuilder();
            weekSb.append("👨‍🏫 <b>").append(teacher).append("</b>\n");
            weekSb.append("🗓 <b>Расписание на неделю</b> (текущая: 🔴 Красная неделя)\n");
            for (String day : parser.getDays()) {
                weekSb.append("\n──────────────────\n\n");
                DaySchedule ds = entry.getValue().get(day);
                if (ds == null || !ds.hasLessons()) {
                    weekSb.append("<b>").append(day).append("</b>\n✨ <i>Пар нет — свободный день</i>");
                } else {
                    weekSb.append(ds.formatForTeacher());
                    int prevNum = 0;
                    for (Lesson l : ds.getLessons()) {
                        assertTrue(l.getLessonNumber() >= prevNum,
                                "Lessons must be sorted for teacher " + teacher + " on " + day);
                        prevNum = l.getLessonNumber();
                    }
                }
            }
            int len = weekSb.length();
            if (len > maxTeacherWeekLen) {
                maxTeacherWeekLen = len;
                maxTeacherWeekName = teacher;
            }
        }

        System.out.println("Max group week schedule length: " + maxGroupWeekLen + " chars (Group: " + maxGroupWeekName + ")");
        System.out.println("Max teacher week schedule length: " + maxTeacherWeekLen + " chars (Teacher: " + maxTeacherWeekName + ")");
    }
}
