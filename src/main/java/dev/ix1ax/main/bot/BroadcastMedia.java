package dev.ix1ax.main.bot;

import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import java.util.List;

/** Файл остаётся на серверах Telegram; для рассылки достаточно его file_id. */
public record BroadcastMedia(Type type, String fileId, String caption, List<MessageEntity> entities) {
    public enum Type { PHOTO, VIDEO, ANIMATION, DOCUMENT }
    public static BroadcastMedia from(Message message) {
        if (message == null) return null;
        Type type;
        String id;
        if (message.hasAnimation()) { type = Type.ANIMATION; id = message.getAnimation().getFileId(); }
        else if (message.hasVideo()) { type = Type.VIDEO; id = message.getVideo().getFileId(); }
        else if (message.hasPhoto()) { type = Type.PHOTO; id = message.getPhoto().get(message.getPhoto().size() - 1).getFileId(); }
        else if (message.hasDocument()) { type = Type.DOCUMENT; id = message.getDocument().getFileId(); }
        else return null;
        return new BroadcastMedia(type, id, message.getCaption(), message.getCaptionEntities() == null ? List.of() : List.copyOf(message.getCaptionEntities()));
    }
}
