package dev.ix1ax.main.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import dev.ix1ax.main.bot.TelegramRoutes;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.*;

/** Temporary, unguessable image URLs used by Telegram's prepared-message API. */
@Service
public class ScheduleShareService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ScheduleShareService.class);
    private static final int MAX_BYTES = 650_000;
    private static final int MAX_IMAGES = 500;
    private static final long MAX_CACHE_BYTES = 325_000_000L;
    private final Map<String, Image> images = new LinkedHashMap<>();
    private final ObjectMapper json;
    private final String publicBase, username;
    private final TelegramRoutes telegram;
    record Image(byte[] bytes, long user, long created, long expires) {}
    @org.springframework.beans.factory.annotation.Autowired
    private ImageKitStorage storage;
    public record Prepared(String id, String imageUrl, long expiresAt) {}

    public ScheduleShareService(ObjectMapper json, TelegramRoutes telegram,
            @Value("${share.public-api-url:https://konyaevo-api.ixlax.space/api}") String publicBase,
            @Value("${bot.username:konyaevo_tver_helper_bot}") String username) {
        this.json = json; this.telegram = telegram;
        this.publicBase = publicBase.replaceAll("/$", ""); this.username = username;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
    public synchronized void clearExpiredImages() {
        long now = System.currentTimeMillis();
        images.entrySet().removeIf(entry -> entry.getValue().expires < now);
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
        long cachedBytes = images.values().stream().mapToLong(image -> image.bytes.length).sum();
        if (images.size() >= MAX_IMAGES || cachedBytes + bytes.length > MAX_CACHE_BYTES)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Попробуйте позже");
        String id = UUID.randomUUID().toString();
        images.put(id, new Image(bytes, user, now, now + Duration.ofMinutes(30).toMillis()));
        return id;
    }

    public synchronized byte[] image(String id) {
        Image image = images.get(id);
        if (image == null || image.bytes.length == 0 || image.expires < System.currentTimeMillis()) {
            images.remove(id); throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return image.bytes;
    }

    public Prepared prepare(long user, String encoded, String caption) {
        if (caption == null || caption.length() > 900) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректная подпись");
        long started = System.nanoTime();
        byte[] bytes = validate(encoded);
        String id = store(user, storage != null && storage.enabled() ? new byte[0] : bytes);
        String url = publicBase + "/share/images/" + id + ".jpg";
        String botLink = "https://t.me/" + username;
        ImageKitStorage.Uploaded uploaded = null;
        try {
            if (storage != null && storage.enabled()) {
                uploaded = storage.upload(bytes, id);
                url = uploaded.url();
            }
        } catch (Exception e) {
            synchronized (this) { images.remove(id); }
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("[SCHEDULE SHARE] ImageKit preparation failed image={} error={}", id, e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось загрузить картинку для Telegram");
        }
        log.info("[SCHEDULE SHARE] prepare user={} image={} bytes={} photoUrl={}", user, id, bytes.length, url);
        var result = Map.of("type", "photo", "id", id, "photo_url", url, "thumbnail_url", url,
                "caption", caption,
                "reply_markup", Map.of("inline_keyboard", List.of(List.of(Map.of("text", "Открыть расписание", "url", botLink + "?startapp")))));
        try {
            var body = Map.of("user_id", user, "result", result, "allow_user_chats", true,
                    "allow_group_chats", true, "allow_channel_chats", true);
            var response = telegram.postJson("savePreparedInlineMessage", json.writeValueAsString(body));
            int status = response.status();
            String responseBody = response.body();
            var data = json.readTree(responseBody);
            String preparedId = data.path("result").path("id").asText();
            if (status != 200 || !data.path("ok").asBoolean() || preparedId.isBlank()) {
                String description = data.path("description").asText("No description").replaceAll("[\\r\\n]", " ");
                log.warn("[SCHEDULE SHARE] Telegram rejected prepared message: HTTP {}, code {}, description {}",
                        status, data.path("error_code").asInt(), description.substring(0, Math.min(300, description.length())));
                throw new IllegalStateException();
            }
            log.info("[SCHEDULE SHARE] prepared image={} http={} elapsedMs={} expires={}", id, status,
                    (System.nanoTime() - started) / 1_000_000, data.path("result").path("expiration_date").asLong());
            long expiresAt = uploaded == null ? System.currentTimeMillis() + Duration.ofMinutes(30).toMillis() : uploaded.expiresAt();
            long telegramExpiry = data.path("result").path("expiration_date").asLong() * 1000;
            if (telegramExpiry > 0) expiresAt = Math.min(expiresAt, telegramExpiry);
            return new Prepared(preparedId, url, expiresAt);
        } catch (Exception e) {
            synchronized (this) { images.remove(id); }
            if (uploaded != null) {
                try { storage.expireNow(uploaded.fileId()); }
                catch (RuntimeException ignored) { /* Original expiration remains persisted for cleanup. */ }
            }
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            // Exception class identifies timeouts/network failures without logging token-bearing URLs.
            log.warn("[SCHEDULE SHARE] Preparation failed image={}: {}", id, e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось подготовить отправку в Telegram");
        }
    }
}
