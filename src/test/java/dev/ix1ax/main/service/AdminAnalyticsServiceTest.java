package dev.ix1ax.main.service;

import dev.ix1ax.main.model.Donation;
import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.repository.ActivityDayRepository;
import dev.ix1ax.main.repository.DonationRepository;
import dev.ix1ax.main.repository.UserActivityRepository;
import dev.ix1ax.main.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAnalyticsServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private final UserSettingsRepository users = mock(UserSettingsRepository.class);
    private final ActivityDayRepository days = mock(ActivityDayRepository.class);
    private final MascotService mascot = mock(MascotService.class);
    private final DonationRepository donations = mock(DonationRepository.class);
    private final AdminAnalyticsService service = new AdminAnalyticsService(users, days, mascot, donations);

    private static UserSettings user(long id, String role, String target, LocalDate firstSeen, boolean activeToday) {
        var user = new UserSettings(id);
        user.setRole(role);
        if ("teacher".equals(role)) user.setTeacherName(target); else user.setGroupName(target);
        ReflectionTestUtils.setField(user, "firstSeen", firstSeen.atTime(12, 0).atZone(MOSCOW).toInstant());
        if (activeToday) ReflectionTestUtils.setField(user, "lastActive", TODAY.atTime(9, 0).atZone(MOSCOW).toInstant());
        return user;
    }

    private static UserSettings unconfigured(long id) {
        var user = new UserSettings(id);
        ReflectionTestUtils.setField(user, "firstSeen", TODAY.minusDays(40).atStartOfDay(MOSCOW).toInstant());
        return user;
    }

    @Test void buildsDailyActivityCoursesGroupsStreaksAndSupport() {
        when(users.findAll()).thenReturn(List.of(
                user(1, "student", "2-МР3", TODAY, true), user(2, "student", "2-МР3", TODAY.minusDays(40), true),
                user(3, "student", "1-ИС1", TODAY.minusDays(1), false), user(4, "teacher", "Иванова А.С.", TODAY.minusDays(40), true),
                unconfigured(5)));
        when(days.countByDay(TODAY.minusDays(6))).thenReturn(List.of(new Object[]{TODAY, 3L}, new Object[]{TODAY.minusDays(1), 2L}));
        when(mascot.streakRows(eq(TODAY), anyMap())).thenReturn(List.of(
                new MascotService.StreakRow(1, 5, 5, true, "star", 50), new MascotService.StreakRow(2, 0, 2, false, "autumn", 0),
                new MascotService.StreakRow(3, 1, 1, true, "classic", 0), new MascotService.StreakRow(5, 0, 0, false, "classic", 0)));
        when(mascot.lookName(anyString())).thenAnswer(call -> call.getArgument(0));
        Instant paid = TODAY.atTime(20, 0).atZone(MOSCOW).toInstant();
        when(donations.findAll()).thenReturn(List.of(new Donation("a", 1, 50, paid), new Donation("b", 1, 25, paid.minus(Duration.ofDays(2))),
                new Donation("c", 4, 1, paid)));

        var result = service.analytics(3, TODAY); // меньше недели не бывает
        assertEquals(7, result.activity().size());
        assertEquals(new AdminAnalyticsService.DayPoint(TODAY.toString(), 3, 1), result.activity().get(6));
        assertEquals(new AdminAnalyticsService.DayPoint(TODAY.minusDays(1).toString(), 2, 1), result.activity().get(5));
        assertEquals(3, result.activeToday());
        assertEquals(2, result.activeYesterday());

        assertEquals(List.of(new AdminAnalyticsService.Bar("1 курс", 1, 0), new AdminAnalyticsService.Bar("2 курс", 2, 2),
                new AdminAnalyticsService.Bar("Преподаватели", 1, 1)), result.courses());
        assertEquals(new AdminAnalyticsService.Bar("2-МР3", 2, 2), result.groups().get(0));

        var streak = result.streak();
        assertEquals(3, streak.lit());          // лучшая серия ≥ 1
        assertEquals(2, streak.burning());      // серия жива сейчас
        assertEquals(2, streak.countedToday());
        assertEquals(5, streak.record());
        assertEquals(List.of(1L, 0L, 1L, 0L, 0L), streak.buckets().stream().map(AdminAnalyticsService.Bar::value).toList());
        assertEquals(new AdminAnalyticsService.GroupStreak("2-МР3", 2, 1, 5), streak.groups().get(0));

        var support = result.support();
        assertEquals(76, support.stars());
        assertEquals(2, support.supporters());
        assertEquals(3, support.payments());
        assertEquals(new AdminAnalyticsService.StarDay(TODAY.toString(), 51), support.days().get(support.days().size() - 1));
        assertEquals("2-МР3", support.recent().get(0).target());
    }

    @Test void courseFallsBackToGroupDigit() {
        var student = new UserSettings(1L); student.setRole("student"); student.setGroupName("3-ИС2");
        assertEquals("3 курс", AdminAnalyticsService.course(student));
        student.setGroupName("ИС"); assertEquals("Без курса", AdminAnalyticsService.course(student));
        assertNull(AdminAnalyticsService.target(new UserSettings(2L)));
    }

    @Test void activityDayIsMarkedOncePerUserPerDay() {
        var repository = mock(UserActivityRepository.class);
        var activity = new UserActivityService(repository, null, days);
        activity.markDay(42, TODAY);
        activity.markDay(42, TODAY);
        activity.markDay(42, TODAY.plusDays(1));
        verify(days, times(1)).mark(TODAY, 42);
        verify(days, times(1)).mark(TODAY.plusDays(1), 42);
    }
}
