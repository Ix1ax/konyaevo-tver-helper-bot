package dev.ix1ax.main.bot;

import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.service.*;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CallbackRouterTest {
    private final ScheduleService service = mock(ScheduleService.class);
    private final MessageSender sender = mock(MessageSender.class);
    private final CallbackRouter router = new CallbackRouter(service, sender, mock(AdminService.class));
    private CallbackQuery click(String data, int messageId) {
        Chat chat = new Chat(); chat.setId(42L);
        Message message = new Message(); message.setChat(chat); message.setMessageId(messageId);
        User from = new User(); from.setId(42L);
        CallbackQuery query = new CallbackQuery(); query.setMessage(message); query.setFrom(from); query.setData(data);
        return query;
    }
    @Test void oldMessagesNeverChangeSettingsButShowCurrentMenu() {
        UserSettings user = new UserSettings(42L); user.setMessageId(10); user.setRole("student"); user.setGroupName("1-ИС");
        user.setNotifyEnabled(true);
        when(service.getOrCreateUser(42L)).thenReturn(user);
        router.route(click("notify:disable", 9));
        assertEquals(Boolean.TRUE, user.getNotifyEnabled(), "кнопка старого сообщения не меняет настройки");
        assertEquals(9, user.getMessageId());
        verify(sender).removeKeyboard(42L, 10);
        verify(sender).editMessage(eq(42L), eq(9), anyString(), any());
        CallbackQuery foreign = click("role:student", 9); foreign.getFrom().setId(43L);
        clearInvocations(service);
        router.route(foreign); verify(service, never()).saveUser(any());
    }
    @Test void navigationWorksFromStandaloneMessages() {
        UserSettings user = new UserSettings(42L); user.setMessageId(10); user.setRole("student"); user.setGroupName("1-ИС");
        when(service.getOrCreateUser(42L)).thenReturn(user);
        // «‹ Меню» под сообщением «Спасибо за поддержку» или /help.
        router.route(click("dashboard", 77));
        assertEquals(77, user.getMessageId());
        verify(sender).removeKeyboard(42L, 10);
        verify(sender).editMessage(eq(42L), eq(77), anyString(), any());
        assertTrue(CallbackRouter.navigation("view:today") && CallbackRouter.navigation("help"));
        assertFalse(CallbackRouter.navigation("role:student") || CallbackRouter.navigation("notify:disable"));
    }
    @Test void typedTimeOnlyWorksAfterPromptAndBackCancelsIt() {
        UserSettings user = new UserSettings(42L); user.setMessageId(10); user.setRole("student"); user.setGroupName("1-ИС");
        when(service.getOrCreateUser(42L)).thenReturn(user);
        assertFalse(router.handleCustomTimeInput(42, "07:30"));
        router.route(click("notify:custom_time", 10));
        assertTrue(router.handleCustomTimeInput(42, "7:30"));
        assertEquals("07:30", user.getNotifyTime());
        assertFalse(router.handleCustomTimeInput(42, "09:00"));
        router.route(click("notify:custom_time", 10)); router.route(click("dashboard", 10));
        assertFalse(router.handleCustomTimeInput(42, "09:00"));
    }
    @Test void longTeacherNamesNeverOverflowCallbackLimit() {
        String name = "Оченьдлиннаяфамилияпреподавателя Оченьдлинноеимя";
        var keyboards = java.util.List.of(KeyboardFactory.buildTeacherActionsKeyboard(name), KeyboardFactory.buildTeacherListKeyboard(java.util.List.of(name), "О"));
        for (var keyboard : keyboards) for (var row : keyboard.getKeyboard()) for (var button : row) {
            if (button.getCallbackData() != null) assertTrue(button.getCallbackData().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 64);
        }
    }
}
