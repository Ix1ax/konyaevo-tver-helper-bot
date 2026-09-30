package dev.ix1ax.main.service;

import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.repository.UserSettingsRepository;
import dev.ix1ax.main.bot.MessageSender;
import org.junit.jupiter.api.Test;
import java.time.DayOfWeek;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TomorrowNotificationTest {
    @Test void usesTomorrowScheduleAndSelectedDaysAndDisablesBlockedRecipient() {
        var repo = mock(UserSettingsRepository.class);
        var sender = mock(MessageSender.class);
        var schedule = mock(ScheduleService.class);
        var changes = mock(ChangesParserService.class);
        UserSettings student = new UserSettings(42L); student.setRole("student"); student.setGroupName("1-ИС"); student.setNotifyTomorrow(true);
        UserSettings skipped = new UserSettings(43L); skipped.setRole("teacher"); skipped.setTeacherName("Иванов И.И."); skipped.setNotifyDays("none");
        when(repo.findByNotifyTomorrowTrueAndNotifyTime("18:30")).thenReturn(List.of(student, skipped));
        when(schedule.getTomorrowName()).thenReturn("Вторник");
        when(schedule.getScheduleTextForGroup("1-ИС", "Вторник")).thenReturn("Завтрашние пары");
        when(sender.sendDirectMessage(eq(42L), anyString(), isNull())).thenReturn(MessageSender.DirectSendResult.BLOCKED);
        new NotificationService(repo, changes, sender, schedule).processTomorrowNotifications("18:30", DayOfWeek.MONDAY);
        verify(sender).sendDirectMessage(42L, "<b>Расписание на завтра</b>\n\nЗавтрашние пары", null);
        verify(sender, never()).sendDirectMessage(eq(43L), anyString(), any());
        assertFalse(student.getNotifyTomorrow()); verify(repo).save(student);
        verifyNoInteractions(changes);
    }
}
