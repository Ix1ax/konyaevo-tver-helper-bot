package dev.ix1ax.main.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Temporary, unguessable image URLs used by Telegram's prepared-message API. */
@Service
public class ScheduleShareService {
    private static final int MAX_BYTES = 650_000;
    private final Map<String, Image> images = new LinkedHashMap<>();
    private final ObjectMapper json;
    private final String token, apiBase, publicBase, username;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    record Image(byte[] bytes, long user, long created, long expires) {}
    public record Prepared(String id, String imageUrl) {}

    public ScheduleShareService(ObjectMapper json, @Value("${bot.token}") String token,
            @Value("${bot.base-url:https://api.telegram.org/bot}") String apiBase,
            @Value("${share.public-api-url:https://konyaevo-api.ixlax.space/api}") String publicBase,
            @Value("${bot.username:konyaevo_tver_helper_bot}") String username) {
        this.json = json; this.token = token; this.apiBase = apiBase;
        this.publicBase = publicBase.replaceAll("/$", ""); this.username = username;
    }

    static byte[] validate(String value) {
        if (value == null || value.length() > 870_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length > MAX_BYTES || bytes.length < 24 || bytes[0] != (byte) 0xff || bytes[1] != (byte) 0xd8)
                throw new IllegalArgumentException();
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException();
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    if (!reader.getFormatName().equalsIgnoreCase("JPEG")) throw new IllegalArgumentException();
                    if (reader.getWidth(0) != 1080 || reader.getHeight(0) < 1 || reader.getHeight(0) > 6000)
                        throw new IllegalArgumentException();
                } finally { reader.dispose(); }
            }
            return bytes;
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректная картинка"); }
    }

    private synchronized String store(long user, byte[] bytes) {
        long now = System.currentTimeMillis();
        images.entrySet().removeIf(e -> e.getValue().expires < now);
        if (images.values().stream().anyMatch(image -> image.user == user && now - image.created < 10_000))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Подождите несколько секунд");
        if (images.size() >= 50) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Попробуйте позже");
        String id = UUID.randomUUID().toString();
        images.put(id, new Image(bytes, user, now, now + Duration.ofMinutes(30).toMillis()));
        return id;
    }

    public synchronized byte[] image(String id) {
        Image image = images.get(id);
        if (image == null || image.expires < System.currentTimeMillis()) {
            images.remove(id); throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return image.bytes;
    }

    public Prepared prepare(long user, String encoded, String caption) {
        if (caption == null || caption.length() > 900) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректная подпись");
        byte[] bytes = validate(encoded);
        String id = store(user, bytes);
        String url = publicBase + "/share/images/" + id + ".jpg";
        String botLink = "https://t.me/" + username;
        var result = Map.of("type", "photo", "id", id, "photo_url", url, "thumbnail_url", url,
                "caption", caption,
                "reply_markup", Map.of("inline_keyboard", List.of(List.of(Map.of("text", "Открыть расписание", "url", botLink + "?startapp")))));
        try {
            var body = Map.of("user_id", user, "result", result, "allow_user_chats", true,
                    "allow_group_chats", true, "allow_channel_chats", true);
            var request = HttpRequest.newBuilder(URI.create(apiBase + token + "/savePreparedInlineMessage"))
                    .timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            var data = json.readTree(response.body());
            String preparedId = data.path("result").path("id").asText();
            if (response.statusCode() != 200 || !data.path("ok").asBoolean() || preparedId.isBlank())
                throw new IllegalStateException();
            return new Prepared(preparedId, url);
        } catch (Exception e) {
            synchronized (this) { images.remove(id); }
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            // Do not include request URLs or tokens in errors/logs.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось подготовить отправку в Telegram");
        }
    }
}
