package dev.ix1ax.main.bot;

import dev.ix1ax.main.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class MessageSenderTest {
    @Test void longSchedulePagesAreNavigableAndRetainFinalText() throws Exception {
        AbsSender bot = mock(AbsSender.class);
        var sender = new MessageSender(mock(ScheduleService.class)); sender.init(bot);
        sender.editMessage(42, 10, "<b>" + "Расписание ".repeat(500) + "ПОСЛЕДНЯЯ ПАРА</b>", KeyboardFactory.buildScheduleKeyboard());
        var first = ArgumentCaptor.forClass(EditMessageText.class);
        verify(bot).execute(first.capture());
        var next = first.getValue().getReplyMarkup().getKeyboard().get(0).get(1).getCallbackData();
        assertTrue(sender.showPage(42, 10, next));
        var all = ArgumentCaptor.forClass(EditMessageText.class);
        verify(bot, times(2)).execute(all.capture());
        assertTrue(all.getAllValues().get(1).getText().contains("ПОСЛЕДНЯЯ ПАРА"));
        assertFalse(sender.showPage(43, 10, next));
        assertFalse(sender.showPage(42, 10, "page:wrong:1"));
    }
    @Test void networkFailureDoesNotRetryAndDuplicateNotification() throws Exception {
        AbsSender bot = mock(AbsSender.class);
        when(bot.execute(any(SendMessage.class))).thenThrow(new TelegramApiException("Connection timeout"));
        var sender = new MessageSender(mock(ScheduleService.class)); sender.init(bot);
        assertEquals(MessageSender.DirectSendResult.ERROR, sender.sendDirectMessage(42, "Пары", null));
        verify(bot, times(1)).execute(any(SendMessage.class));
    }
    @Test void standaloneLongNotificationIsSentCompletely() throws Exception {
        AbsSender bot = mock(AbsSender.class);
        var sender = new MessageSender(mock(ScheduleService.class)); sender.init(bot);
        String text = "Пары на завтра ".repeat(400);
        assertEquals(MessageSender.DirectSendResult.SUCCESS, sender.sendDirectMessage(42, text, null));
        var messages = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot, atLeast(2)).execute(messages.capture());
        assertEquals(text, messages.getAllValues().stream().map(SendMessage::getText).collect(java.util.stream.Collectors.joining()));
    }
}
