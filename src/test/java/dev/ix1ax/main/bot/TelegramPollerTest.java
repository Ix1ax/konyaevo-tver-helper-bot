package dev.ix1ax.main.bot;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.methods.updates.GetUpdates;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramPollerTest {
    private static Update update(int id) {
        var update = new Update();
        update.setUpdateId(id);
        return update;
    }

    @Test void clearsWebhookOnceAndConfirmsHandledUpdates() throws Exception {
        AbsSender sender = mock(AbsSender.class);
        when(sender.execute(any(DeleteWebhook.class))).thenReturn(true);
        when(sender.execute(any(GetUpdates.class)))
                .thenReturn(new ArrayList<>(List.of(update(7), update(8))))
                .thenReturn(new ArrayList<>());
        var bot = mock(KonyaevoBot.class);
        var poller = new TelegramPoller(TelegramRoutes.of(sender), bot);
        poller.pollOnce();
        poller.pollOnce();
        verify(sender, times(1)).execute(any(DeleteWebhook.class));
        verify(bot, times(2)).onUpdateReceived(any(Update.class));
        var requests = ArgumentCaptor.forClass(GetUpdates.class);
        verify(sender, times(2)).execute(requests.capture());
        assertEquals(0, requests.getAllValues().get(0).getOffset());
        assertEquals(9, requests.getAllValues().get(1).getOffset());
        assertEquals(TelegramRoutes.POLL_TIMEOUT, requests.getAllValues().get(1).getTimeout());
    }

    @Test void retriesGrowUpToThirtySeconds() {
        assertEquals(2000, TelegramPoller.backoff(1));
        assertEquals(16000, TelegramPoller.backoff(4));
        assertEquals(30000, TelegramPoller.backoff(9));
    }
}
