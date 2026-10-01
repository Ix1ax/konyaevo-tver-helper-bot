package dev.ix1ax.main.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ix1ax.main.model.SharedImage;
import dev.ix1ax.main.repository.SharedImageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class ImageKitStorage {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ImageKitStorage.class);
    private final String key, endpoint;
    private final ObjectMapper json;
    private final SharedImageRepository cleanup;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Proxy proxy;
    public record Uploaded(String fileId, String url, long expiresAt) {}
    record Response(int status, String body) {}

    public ImageKitStorage(ObjectMapper json, SharedImageRepository cleanup,
            @Value("${IMAGEKIT_PRIVATE_KEY:}") String key,
            @Value("${IMAGEKIT_URL_ENDPOINT:}") String endpoint,
            @Value("${bot.proxy.type:NO_PROXY}") String proxyType,
            @Value("${bot.proxy.host:127.0.0.1}") String host,
            @Value("${bot.proxy.port:10808}") int port) {
        this.json = json; this.cleanup = cleanup; this.key = key.trim();
        this.endpoint = endpoint.trim().replaceAll("/$", "");
        proxy = "HTTP".equalsIgnoreCase(proxyType) ? new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port))
                : proxyType.toUpperCase(Locale.ROOT).startsWith("SOCKS") ? new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port)) : Proxy.NO_PROXY;
        if (enabled() && !this.endpoint.matches("https://ik\\.imagekit\\.io/[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("IMAGEKIT_URL_ENDPOINT must be https://ik.imagekit.io/your_id");
    }
    public boolean enabled() { return !key.isBlank(); }

    public Uploaded upload(byte[] jpeg, String id) throws Exception {
        String boundary = "share" + UUID.randomUUID().toString().replace("-", "");
        var fields = new LinkedHashMap<String, String>();
        fields.put("file", Base64.getEncoder().encodeToString(jpeg));
        fields.put("fileName", id + ".jpg");
        fields.put("folder", "/konyaevo-schedule-share");
        fields.put("useUniqueFileName", "false");
        fields.put("tags", "konyaevo-schedule-share");
        StringBuilder multipart = new StringBuilder();
        fields.forEach((name, value) -> multipart.append("--").append(boundary)
                .append("\r\nContent-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
                .append(value).append("\r\n"));
        multipart.append("--").append(boundary).append("--\r\n");
        Response response = request("POST", "https://upload.imagekit.io/api/v1/files/upload",
                "multipart/form-data; boundary=" + boundary, multipart.toString().getBytes(StandardCharsets.UTF_8));
        if (response.status != 200 && response.status != 201) {
            log.warn("[SCHEDULE SHARE] ImageKit upload rejected image={} http={}", id, response.status);
            throw new IllegalStateException("ImageKit upload rejected");
        }
        var data = json.readTree(response.body);
        String fileId = data.path("fileId").asText();
        if (!fileId.matches("[A-Za-z0-9_-]+")) throw new IllegalStateException("Missing ImageKit fileId");
        long expiresAt = System.currentTimeMillis() + Duration.ofMinutes(30).toMillis();
        try {
            // Persist before preparing Telegram so cleanup also survives a failed preparation or restart.
            cleanup.save(new SharedImage(fileId, expiresAt));
        } catch (RuntimeException e) {
            try { delete(fileId); } catch (Exception ignored) { log.warn("[SCHEDULE SHARE] ImageKit orphan file={} needs cleanup", fileId); }
            throw e;
        }
        String url = data.path("url").asText();
        if (!url.startsWith(endpoint + "/") || !url.endsWith(".jpg")) {
            expireNow(fileId);
            throw new IllegalStateException("Unexpected ImageKit image URL");
        }
        log.info("[SCHEDULE SHARE] ImageKit uploaded image={} file={} bytes={} expiresAt={}", id, fileId, jpeg.length, expiresAt);
        return new Uploaded(fileId, url, expiresAt);
    }

    public void expireNow(String fileId) { cleanup.save(new SharedImage(fileId, 0)); }

    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void clearExpired() {
        if (!enabled()) return;
        for (SharedImage image : cleanup.findTop50ByExpiresAtLessThanOrderByExpiresAtAsc(System.currentTimeMillis())) {
            try {
                delete(image.getFileId());
                cleanup.deleteById(image.getFileId());
                log.info("[SCHEDULE SHARE] ImageKit deleted file={}", image.getFileId());
            } catch (Exception e) {
                if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); return; }
                log.warn("[SCHEDULE SHARE] ImageKit cleanup retry file={} error={}", image.getFileId(), e.getClass().getSimpleName());
            }
        }
    }
    private void delete(String fileId) throws Exception {
        Response result = request("DELETE", "https://api.imagekit.io/v1/files/" + fileId, null, new byte[0]);
        if (result.status != 204 && result.status != 200 && result.status != 404)
            throw new IllegalStateException("ImageKit deletion HTTP " + result.status);
        // CDN copies may remain cached: deletion removes the origin asset, not an exact URL expiry guarantee.
    }
    private Response request(String method, String url, String contentType, byte[] body) throws Exception {
        String auth = "Basic " + Base64.getEncoder().encodeToString((key + ":").getBytes(StandardCharsets.UTF_8));
        if (proxy == Proxy.NO_PROXY) {
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("Authorization", auth).method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
            if (contentType != null) request.header("Content-Type", contentType);
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        }
        var connection = (HttpURLConnection) URI.create(url).toURL().openConnection(proxy);
        try {
            connection.setConnectTimeout(5000); connection.setReadTimeout(15000);
            connection.setRequestMethod(method); connection.setRequestProperty("Authorization", auth);
            if (contentType != null) connection.setRequestProperty("Content-Type", contentType);
            if (body.length != 0) {
                connection.setDoOutput(true); connection.setFixedLengthStreamingMode(body.length);
                try (var out = connection.getOutputStream()) { out.write(body); }
            }
            int status = connection.getResponseCode();
            try (var in = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                return new Response(status, in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
}
