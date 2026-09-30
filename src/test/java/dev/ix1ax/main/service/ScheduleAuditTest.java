package dev.ix1ax.main.service;

import dev.ix1ax.main.controller.ScheduleApiController;
import dev.ix1ax.main.model.DaySchedule;
import dev.ix1ax.main.model.Lesson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ScheduleAuditTest {
    private ScheduleParserService parser;
    private ChangesParserService changes;
    private ScheduleService service;
    private MockMvc api;

    @BeforeEach
    void setUp() {
        parser = mock(ScheduleParserService.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
        changes = new ChangesParserService(null, java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-07T09:00:00Z"), java.time.ZoneId.of("Europe/Moscow")));
        ReflectionTestUtils.setField(changes, "changesDate", "7 сентября (понедельник)");
        when(parser.getDays()).thenReturn(new String[]{"Понедельник"});
        when(parser.getScheduleForGroup(anyString())).thenReturn(Map.of());
        when(parser.getScheduleForTeacher(anyString())).thenReturn(Map.of());
        when(parser.getGroupsByCourse()).thenReturn(Map.of("4 курс", List.of("4-ИС2")));
        when(parser.getAllTeachers()).thenReturn(Set.of("Евстигнеев А.С."));
        when(parser.getTeacherFirstLetters()).thenReturn(List.of("Е"));
        service = new ScheduleService(parser, changes, null);
        api = MockMvcBuilders.standaloneSetup(new ScheduleApiController(service, parser, changes)).build();
    }

    @Test
    void weekDisplaysNameOnceAndSeparatesEveryDay() {
        when(parser.getDays()).thenReturn(new String[]{"Понедельник", "Вторник", "Среда"});
        String text = service.getWeekScheduleTextForTeacher("Авдоян Д.Т.");
        assertEquals(1, text.split("Авдоян Д.Т.", -1).length - 1);
        assertEquals(3, text.split("──────────────", -1).length - 1);
        assertTrue(text.contains("<b>Понедельник</b>"));
        assertTrue(text.contains("<b>Вторник</b>"));
    }

    @Test
    void cancellationPhrases() {
        for (String text : List.of("отмена", "Пара снята", "ПАР НЕТ", "пар\nнет")) {
            assertTrue(changes.isCancellation(text), text);
        }
        assertFalse(changes.isCancellation("Информатика"));
        assertFalse(changes.isCancellation(null));
    }

    @Test
    void semesterAndMondayBoundaries() {
        assertEquals("red", AcademicWeek.typeFor(LocalDate.of(2026, 9, 1)));
        assertEquals("red", AcademicWeek.typeFor(LocalDate.of(2026, 9, 6)));
        assertEquals("blue", AcademicWeek.typeFor(LocalDate.of(2026, 9, 7)));
        assertEquals("red", AcademicWeek.typeFor(LocalDate.of(2026, 2, 1)));
        assertEquals("blue", AcademicWeek.typeFor(LocalDate.of(2026, 2, 2)));
    }

    @Test
    void groupReplacementKeepsOriginalAndDoesNotInventRoom() throws Exception {
        DaySchedule day = new DaySchedule("Понедельник");
        day.addLesson(new Lesson(2, "10:15 - 11:50", "Математика", "Бидыло С.И.", "201"));
        when(parser.getScheduleForGroup("4-ИС2")).thenReturn(Map.of("Понедельник", day));
        setChanges(Map.of("4-ИС2", Map.of(2, "Информатика\nЕвстигнеев А.С.", 1, "Физика\nБобков Д.И.\n302", 6, "пар нет")));
        api.perform(get("/api/schedule/group/4-ИС2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Понедельник.lessons.length()").value(2))
                .andExpect(jsonPath("$.Понедельник.lessons[0].lessonNumber").value(1))
                .andExpect(jsonPath("$.Понедельник.lessons[1].originalSubject").value("Математика"))
                .andExpect(jsonPath("$.Понедельник.lessons[1].originalTeacher").value("Бидыло С.И."))
                .andExpect(jsonPath("$.Понедельник.lessons[1].originalRoom").value("201"))
                .andExpect(jsonPath("$.Понедельник.lessons[1].room").value(""))
                .andExpect(jsonPath("$.Понедельник.lessons[1].changed").value(true));
        String bot = service.getScheduleTextForGroup("4-ИС2", "Понедельник");
        assertFalse(bot.contains("<s>"));
        assertTrue(bot.contains("<b>ЗАМЕНА:</b>"));
        assertTrue(bot.contains("Информатика"));
        String week = service.getWeekScheduleTextForGroup("4-ИС2");
        assertTrue(week.contains("Математика"));
        assertFalse(week.contains("Информатика"));
        assertFalse(week.contains("Физика"));
        assertFalse(week.contains("<b>ЗАМЕНА:</b>"));
        assertTrue(week.contains("без замен и отмен"));
    }

    @Test
    void teacherCancellationIsNotAddedAgainAndNewGroupsAreSorted() throws Exception {
        DaySchedule day = new DaySchedule("Понедельник");
        day.addLesson(new Lesson(4, "14:30 - 16:00", "МДК", "4-ИС2", "307"));
        when(parser.getScheduleForTeacher("Евстигнеев А.С.")).thenReturn(Map.of("Понедельник", day));
        setChanges(Map.of("4-ИС2", Map.of(4, "отмена\nЕвстигнеев А.С.", 1, "МДК\nЕвстигнеев А.С.\n307"),
                "4-ИС3", Map.of(1, "МДК\nЕвстигнеев А.С.\n308", 3, "МДК\nЕвстигнеев А.С.\n308")));
        api.perform(get("/api/schedule/teacher/Евстигнеев А.С."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Понедельник.lessons.length()").value(4))
                .andExpect(jsonPath("$.Понедельник.lessons[0].groupName").value("4-ИС2"))
                .andExpect(jsonPath("$.Понедельник.lessons[1].groupName").value("4-ИС3"))
                .andExpect(jsonPath("$.Понедельник.lessons[2].lessonNumber").value(3))
                .andExpect(jsonPath("$.Понедельник.lessons[3].canceled").value(true));
    }

    @Test
    void replacementInOppositeWeekSlotRemainsVisible() {
        DaySchedule day = new DaySchedule("Понедельник");
        day.addLesson(new Lesson(1, "08:30", "Математика", "Бидыло С.И.", "201", "red"));
        when(parser.getScheduleForGroup("4-ИС2")).thenReturn(Map.of("Понедельник", day));
        setChanges(Map.of("4-ИС2", Map.of(1, "МДК\nЕвстигнеев А.С.\n307")));
        var lessons = service.getResolvedGroupSchedule("4-ИС2").get("Понедельник").getLessons();
        assertEquals(2, lessons.size());
        assertFalse(lessons.get(0).isChanged());
        assertEquals("blue", lessons.get(1).getWeekType());
        assertTrue(lessons.get(1).isChanged());
    }

    @Test
    void apiContractsAndClassroomPartition() throws Exception {
        api.perform(get("/api/info")).andExpect(status().isOk())
                .andExpect(jsonPath("$.isRedWeek").isBoolean())
                .andExpect(jsonPath("$.tomorrowRedWeek").isBoolean())
                .andExpect(jsonPath("$.weekBadge").isString())
                .andExpect(jsonPath("$.todayName").isString())
                .andExpect(jsonPath("$.tomorrowName").isString())
                .andExpect(jsonPath("$.changesDate").isString())
                .andExpect(jsonPath("$.serverTime").isString());
        api.perform(get("/api/groups")).andExpect(jsonPath("$.groupsByCourse['4 курс'][0]").value("4-ИС2"));
        api.perform(get("/api/teachers")).andExpect(jsonPath("$.letters[0]").value("Е"));
        setChanges(Map.of("4-ИС2", Map.of(2, "пар нет")));
        api.perform(get("/api/changes/group/4-ИС2")).andExpect(jsonPath("$.items[0].canceled").value(true));
        when(parser.getAllRooms()).thenReturn(List.of("201", "307"));
        DaySchedule day = new DaySchedule("Понедельник");
        day.addLesson(new Lesson(2, "10:15", "МДК", "Евстигнеев А.С.", "307", "red"));
        when(parser.getScheduleForGroup("4-ИС2")).thenReturn(Map.of("Понедельник", day));
        api.perform(get("/api/classrooms/free").param("day", "Понедельник").param("slot", "2").param("weekType", "red"))
                .andExpect(jsonPath("$.freeRooms[0]").value("201"))
                .andExpect(jsonPath("$.occupiedRooms[0].room").value("307"));
    }

    @Test
    void oldChangesDoNotReplaceCurrentLessons() {
        ReflectionTestUtils.setField(changes, "changesDate", "31 августа (понедельник)");
        setChanges(Map.of("4-ИС2", Map.of(1, "МДК\nЕвстигнеев А.С.\n307")));
        assertNull(changes.getOverlayDayName());
        assertTrue(service.getResolvedGroupSchedule("4-ИС2").get("Понедельник").getLessons().isEmpty());
    }

    @Test
    void teacherInitialsDistinguishNamesakes() {
        assertTrue(changes.teacherMatches("МДК\nРоманов Ю.М.\n307", "Романов Ю.М."));
        assertFalse(changes.teacherMatches("МДК\nРоманов Ю.М.\n307", "Романов М.А."));
        assertTrue(changes.teacherMatches("Романов М.А., Романов Ю.М.", "Романов Ю.М."));
        assertTrue(changes.teacherMatches("Романов М. А.", "Романов М.А."));
        assertFalse(changes.teacherMatches("Математика", ""));
    }

    @Test
    void classroomRequestRejectsUnknownSlotAndWeek() throws Exception {
        api.perform(get("/api/classrooms/free").param("day", "Понедельник").param("slot", "7"))
                .andExpect(status().isBadRequest());
        api.perform(get("/api/classrooms/free").param("day", "Понедельник").param("weekType", "green"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void datedHeaderRespectsYearAndNewYearBoundary() {
        var january = new ChangesParserService(null, java.time.Clock.fixed(
                java.time.Instant.parse("2027-01-01T09:00:00Z"), java.time.ZoneId.of("Europe/Moscow")));
        ReflectionTestUtils.setField(january, "changesDate", "31 декабря");
        assertEquals("Четверг", january.getOverlayDayName());
        ReflectionTestUtils.setField(january, "changesDate", "31 декабря 2025");
        assertNull(january.getOverlayDayName());
    }

    private void setChanges(Map<String, Map<Integer, String>> data) {
        ReflectionTestUtils.setField(changes, "changesByGroup", data);
    }
}
