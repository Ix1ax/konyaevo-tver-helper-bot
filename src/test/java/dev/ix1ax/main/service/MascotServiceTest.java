package dev.ix1ax.main.service;

import dev.ix1ax.main.bot.MessageSender;
import dev.ix1ax.main.dto.DayScheduleDto;
import dev.ix1ax.main.dto.LessonDto;
import dev.ix1ax.main.model.MascotProgress;
import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.repository.MascotProgressRepository;
import dev.ix1ax.main.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MascotServiceTest {
    private final Map<Long, MascotProgress> saved = new HashMap<>();
    private final MascotProgressRepository repository = mock(MascotProgressRepository.class);
    private final UserSettingsRepository users = mock(UserSettingsRepository.class);
    private final ScheduleService schedule = mock(ScheduleService.class);
    private final MessageSender sender = mock(MessageSender.class);
    private final MascotService mascot = new MascotService(repository, users, schedule, sender);
    // 2026-10-05 — понедельник.
    private static final LocalDate MON = LocalDate.of(2026, 10, 5);

    MascotServiceTest() {
        when(repository.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(saved.get(call.<Long>getArgument(0))));
        when(repository.save(any())).thenAnswer(call -> { MascotProgress p = call.getArgument(0); saved.put(p.getChatId(), p); return p; });
        when(repository.findByReminderEnabledTrueAndStreakGreaterThan(0)).thenAnswer(call ->
                saved.values().stream().filter(p -> p.isReminderEnabled() && p.getStreak() > 0).toList());
        UserSettings student = new UserSettings(42L); student.setRole("student"); student.setGroupName("ИСп-21");
        when(users.findById(42L)).thenReturn(Optional.of(student));
        lessonsOn("Понедельник", "Вторник", "Среда", "Четверг", "Пятница");
    }

    private void lessonsOn(String... days) {
        Map<String, DayScheduleDto> week = new HashMap<>();
        for (String day : days) week.put(day, DayScheduleDto.builder().dayName(day)
                .lessons(List.of(LessonDto.builder().lessonNumber(1).subject("Математика").build())).hasLessons(true).build());
        when(schedule.getBaseGroupSchedule("ИСп-21")).thenReturn(week);
    }

    private MascotProgress visit(LocalDate... days) {
        MascotProgress progress = null;
        for (LocalDate day : days) progress = mascot.checkIn(42, day);
        return progress;
    }

    @Test void weekendsAndRepeatedVisitsDoNotChangeTheStreak() {
        MascotProgress progress = visit(MON.plusDays(3), MON.plusDays(3), MON.plusDays(4), MON.plusDays(5), MON.plusDays(7));
        assertEquals(3, progress.getStreak(), "четверг, пятница и понедельник; суббота огонёк не меняет");
        assertEquals(40, progress.getXp(), "опыт за открытие расписания — раз в день, в том числе в субботу");
    }

    @Test void missedStudyDayResetsWithoutFreezeAndUsesFreezeWhenAvailable() {
        assertEquals(1, visit(MON, MON.plusDays(2)).getStreak());

        saved.clear();
        // Неделя подряд даёт заморозку, она закрывает пропущенный вторник следующей недели.
        MascotProgress progress = visit(MON, MON.plusDays(1), MON.plusDays(2), MON.plusDays(3), MON.plusDays(4), MON.plusDays(7), MON.plusDays(8));
        assertEquals(7, progress.getStreak()); assertEquals(1, progress.getFreezes());
        progress = visit(MON.plusDays(10));
        assertEquals(8, progress.getStreak()); assertEquals(0, progress.getFreezes());
        var week = mascot.state(42, MON.plusDays(10)).week();
        assertEquals(List.of("done", "done", "freeze", "done", "future", "off", "off"), week.stream().map(MascotService.WeekDay::state).toList());
    }

    @Test void milestoneGivesFreezeExperienceAndRewards() {
        visit(MON, MON.plusDays(1), MON.plusDays(2), MON.plusDays(3), MON.plusDays(4), MON.plusDays(7), MON.plusDays(8));
        var state = mascot.state(42, MON.plusDays(8));
        assertEquals(7 * 10 + MascotService.MILESTONE_XP, state.xp());
        assertEquals(2, state.level()); assertEquals("Слушатель", state.levelName());
        assertEquals(List.of(new MascotService.Reward("level", 2, "autumn"), new MascotService.Reward("streak", 7, null)), state.rewards());
        assertTrue(mascot.acknowledgeRewards(42, MON.plusDays(8)).rewards().isEmpty());
    }

    @Test void daysWithoutLessonsAndHolidaysDoNotBreakTheStreak() {
        lessonsOn("Понедельник", "Пятница");
        assertEquals(2, visit(MON, MON.plusDays(4)).getStreak());

        saved.clear();
        lessonsOn("Понедельник", "Вторник", "Среда", "Четверг", "Пятница");
        // 30 декабря — среда, следующий учебный день после каникул — понедельник 11 января.
        assertEquals(2, visit(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 11)).getStreak());
    }

    @Test void deadStreakIsShownAsZeroUntilTheNextVisit() {
        visit(MON, MON.plusDays(1));
        assertEquals(2, mascot.state(42, MON.plusDays(2)).streak(), "сегодняшний день ещё не пропущен");
        assertEquals(0, mascot.state(42, MON.plusDays(3)).streak());
        assertEquals(2, mascot.state(42, MON.plusDays(3)).best());
    }

    @Test void eveningTomorrowAndShareTasksCountOncePerDay() {
        visit(MON);
        assertEquals(10, mascot.event(42, "tomorrow", MON.atTime(17, 59)).xp());
        assertEquals(25, mascot.event(42, "tomorrow", MON.atTime(18, 0)).xp());
        assertEquals(25, mascot.event(42, "tomorrow", MON.atTime(20, 0)).xp());
        assertEquals(45, mascot.event(42, "share", MON.atTime(20, 0)).xp());
        assertEquals(45, mascot.event(42, "share", MON.atTime(21, 0)).xp());
        assertThrows(IllegalArgumentException.class, () -> mascot.event(42, "hack", MON.atTime(21, 0)));
    }

    @Test void lockedLookCannotBeSelected() {
        visit(MON);
        assertThrows(IllegalArgumentException.class, () -> mascot.settings(42, "graduate", null, MON));
        assertThrows(IllegalArgumentException.class, () -> mascot.settings(42, "unknown", null, MON));
        var state = mascot.settings(42, "classic", false, MON);
        assertEquals("classic", state.look()); assertFalse(state.reminder());
    }

    @Test void remindsOnlyOnTheDayTheStreakWouldGoOut() {
        when(sender.sendDirectMessage(eq(42L), anyString(), any())).thenReturn(MessageSender.DirectSendResult.SUCCESS);
        visit(MON, MON.plusDays(1));
        mascot.sendReminders(MON.plusDays(1));
        verifyNoInteractions(sender);

        mascot.sendReminders(MON.plusDays(2));
        mascot.sendReminders(MON.plusDays(2));
        verify(sender, times(1)).sendDirectMessage(eq(42L), eq(MascotService.reminderText(2)), any());

        mascot.sendReminders(MON.plusDays(3));
        verify(sender, times(1)).sendDirectMessage(anyLong(), anyString(), any());
        assertEquals("🔥 <b>Огонёк 2 дня скоро погаснет!</b>\nЗагляни в расписание до 23:59 — Парыч сохранит серию.", MascotService.reminderText(2));
    }

    @Test void neverRemindsWhileTheStreakIsZero() {
        when(sender.sendDirectMessage(anyLong(), anyString(), any())).thenReturn(MessageSender.DirectSendResult.SUCCESS);
        // Пользователь заходил только в субботу: прогресс есть, огонька нет.
        mascot.checkIn(42, MON.plusDays(5));
        assertEquals(0, saved.get(42L).getStreak());
        for (int day = 7; day <= 11; day++) mascot.sendReminders(MON.plusDays(day));
        verifyNoInteractions(sender);
    }

    @Test void remindsFromTheFirstDayOfTheStreak() {
        when(sender.sendDirectMessage(eq(42L), anyString(), any())).thenReturn(MessageSender.DirectSendResult.SUCCESS);
        visit(MON);
        mascot.sendReminders(MON.plusDays(1));
        verify(sender).sendDirectMessage(eq(42L), eq(MascotService.reminderText(1)), any());
        assertTrue(MascotService.reminderText(1).contains("Огонёк 1 день"));
    }

    @Test void deadStreakShownAsZeroGetsNoReminders() {
        when(sender.sendDirectMessage(anyLong(), anyString(), any())).thenReturn(MessageSender.DirectSendResult.SUCCESS);
        visit(MON, MON.plusDays(1), MON.plusDays(2), MON.plusDays(3), MON.plusDays(4));
        // Понедельник — последний шанс: одно напоминание. Его пропустили, огонёк погас:
        // в базе всё ещё 5, пользователь видит 0, и дальше напоминаний нет.
        for (int day = 7; day <= 11; day++) mascot.sendReminders(MON.plusDays(day));
        assertEquals(5, saved.get(42L).getStreak());
        assertEquals(0, mascot.state(42, MON.plusDays(9)).streak());
        verify(sender, times(1)).sendDirectMessage(anyLong(), anyString(), any());
        assertEquals(MON.plusDays(7), saved.get(42L).getLastReminder());
    }

    @Test void blockedRecipientStopsReceivingReminders() {
        when(sender.sendDirectMessage(eq(42L), anyString(), any())).thenReturn(MessageSender.DirectSendResult.BLOCKED);
        visit(MON);
        mascot.sendReminders(MON.plusDays(1));
        assertFalse(saved.get(42L).isReminderEnabled());
    }

    @Test void pluralizesDays() {
        assertEquals("1 день", MascotService.days(1));
        assertEquals("3 дня", MascotService.days(3));
        assertEquals("11 дней", MascotService.days(11));
        assertEquals("22 дня", MascotService.days(22));
    }
}
