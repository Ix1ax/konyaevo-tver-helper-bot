package dev.ix1ax.main.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScheduleShareServiceTest {
    @Test void acceptsFiveHundredImagesAndRejectsTheNextUntilSpaceIsReleased() {
        var service = new ScheduleShareService(new ObjectMapper(), "test-token", "https://api.telegram.org/bot", "https://example.test/api", "example_bot");
        String first = null;
        for (int user = 1; user <= 500; user++) {
            String id = ReflectionTestUtils.invokeMethod(service, "store", (long) user, new byte[]{1});
            if (user == 1) first = id;
        }
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "store", 501L, new byte[]{1})).getStatusCode().value());
        @SuppressWarnings("unchecked")
        var images = (java.util.Map<String, Object>) ReflectionTestUtils.getField(service, "images");
        images.remove(first);
        assertNotNull(ReflectionTestUtils.invokeMethod(service, "store", 501L, new byte[]{1}));
    }
    private byte[] picture(int width, String format) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, 100, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }
    @Test void acceptsJpegAndRejectsWrongFormatDimensionsAndInvalidInput() throws Exception {
        byte[] jpeg = picture(1080,"jpeg");
        assertArrayEquals(jpeg, ScheduleShareService.validate(Base64.getEncoder().encodeToString(jpeg)));
        for (String input : new String[]{"garbage!", Base64.getEncoder().encodeToString(picture(390,"jpeg")),
                Base64.getEncoder().encodeToString(picture(1080,"png"))})
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> ScheduleShareService.validate(input)).getStatusCode().value());
    }
    @SuppressWarnings("unchecked")
    @Test void preparesOnlyForRequestedUserAndServesImageWithRateLimit() throws Exception {
        var service = new ScheduleShareService(new ObjectMapper(),"test-token","https://api.telegram.org/bot","https://example.test/api","example_bot");
        var http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"ok\":true,\"result\":{\"id\":\"prepared-123\"}}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        ReflectionTestUtils.setField(service,"http",http);
        byte[] jpeg = picture(1080,"jpeg");
        String encoded = Base64.getEncoder().encodeToString(jpeg);
        var result = service.prepare(123, encoded, "Ваше расписание на 01.10.2026\nГруппа: 2-МР3");
        assertEquals("prepared-123",result.id());
        String imageId = result.imageUrl().substring(result.imageUrl().lastIndexOf('/')+1).replace(".jpg", "");
        assertArrayEquals(jpeg,service.image(imageId));
        assertEquals(429,assertThrows(ResponseStatusException.class, () -> service.prepare(123,encoded,"Подпись")).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class, () -> service.image("unknown")).getStatusCode().value());
        verify(http,times(1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @SuppressWarnings("unchecked")
    @Test void usesExternalImageUrlWithoutCachingJpegAndExpiresUploadOnTelegramFailure() throws Exception {
        var service = new ScheduleShareService(new ObjectMapper(), "test-token", "https://api.telegram.org/bot", "https://example.test/api", "example_bot");
        var storage = mock(ImageKitStorage.class);
        when(storage.enabled()).thenReturn(true);
        long expiry = System.currentTimeMillis() + 1_800_000;
        when(storage.upload(any(byte[].class), anyString())).thenReturn(new ImageKitStorage.Uploaded("remote-id", "https://ik.imagekit.io/test/image.jpg", expiry));
        ReflectionTestUtils.setField(service, "storage", storage);
        var http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"ok\":true,\"result\":{\"id\":\"external-prepared\"}}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        ReflectionTestUtils.setField(service, "http", http);
        String jpeg = Base64.getEncoder().encodeToString(picture(1080, "jpeg"));
        var prepared = service.prepare(123, jpeg, "Расписание");
        assertEquals("https://ik.imagekit.io/test/image.jpg", prepared.imageUrl());
        assertEquals(expiry, prepared.expiresAt());
        var images = (java.util.Map<String, ScheduleShareService.Image>) ReflectionTestUtils.getField(service, "images");
        assertEquals(0, images.values().iterator().next().bytes().length);
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("{\"ok\":false,\"description\":\"test failure\"}");
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> service.prepare(456, jpeg, "Расписание")).getStatusCode().value());
        verify(storage).expireNow("remote-id");
    }
    @Test void usesConfiguredHttpProxyForPreparedMessages() throws Exception {
        var proxyServer = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var received = new java.util.concurrent.atomic.AtomicReference<String>();
        proxyServer.createContext("/", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] response = "{\"ok\":true,\"result\":{\"id\":\"proxy-message\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        });
        proxyServer.start();
        try {
            var service = new ScheduleShareService(new ObjectMapper(), "test-token", "http://telegram.invalid/bot",
                    "https://example.test/api", "example_bot", "HTTP", "127.0.0.1", proxyServer.getAddress().getPort());
            var result = service.prepare(123, Base64.getEncoder().encodeToString(picture(1080, "jpeg")), "Расписание");
            assertEquals("proxy-message", result.id());
            assertEquals(123, new ObjectMapper().readTree(received.get()).path("user_id").asLong());
        } finally { proxyServer.stop(0); }
    }

}
