package dev.ix1ax.main.bot;

import dev.ix1ax.main.service.*;
import dev.ix1ax.main.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.bots.AbsSender;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BroadcastMediaTest {
    @Test void preservesVideoCaptionAndUsesTelegramFileId() throws Exception {
        Message message=new Message(); Video video=new Video(); video.setFileId("cached-video-id"); message.setVideo(video); message.setCaption("Обновление бота");
        MessageEntity bold=new MessageEntity(); bold.setType("bold"); bold.setOffset(0); bold.setLength(10); message.setCaptionEntities(List.of(bold));
        var media=BroadcastMedia.from(message); assertEquals(BroadcastMedia.Type.VIDEO,media.type());
        AbsSender bot=mock(AbsSender.class); var sender=new MessageSender(mock(ScheduleService.class)); sender.init(bot);
        assertEquals(MessageSender.DirectSendResult.SUCCESS,sender.sendDirectMedia(42,media));
        var sent=org.mockito.ArgumentCaptor.forClass(SendVideo.class); verify(bot).execute(sent.capture());
        assertEquals("Обновление бота",sent.getValue().getCaption()); assertEquals(List.of(bold),sent.getValue().getCaptionEntities());
        assertTrue(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(sent.getValue()).contains("cached-video-id"));
    }
    @Test void mediaBroadcastRequiresAdminAndIsNotSentBeforeConfirmation() {
        var repo=mock(UserSettingsRepository.class); var sender=mock(MessageSender.class);
        var service=new AdminService(repo,mock(ScheduleParserService.class),mock(ChangesParserService.class),mock(ScheduleService.class),sender);
        service.init(); var media=new BroadcastMedia(BroadcastMedia.Type.VIDEO,"file","Видео",List.of());
        assertThrows(IllegalArgumentException.class,()->service.createMediaDraft(42,media));
        var draft=service.createMediaDraft(1669683599L,media); assertEquals(media,draft.media());
        verifyNoInteractions(sender);
        when(repo.findAllChatIds()).thenReturn(List.of(42L)); when(sender.sendDirectMedia(42,media)).thenReturn(MessageSender.DirectSendResult.SUCCESS);
        service.startBroadcast(draft.id(),10);
        verify(sender,timeout(3000)).sendDirectMedia(42,media);
        verify(sender,never()).sendDirectMessage(eq(42L),anyString(),any());
        assertNull(service.getDraft(draft.id()));
    }
}
