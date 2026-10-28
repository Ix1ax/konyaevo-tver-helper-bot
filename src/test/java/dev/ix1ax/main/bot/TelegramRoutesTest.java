package dev.ix1ax.main.bot;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramRoutesTest {
    private static TelegramRoutes.Route route(String name, AbsSender sender) {
        return new TelegramRoutes.Route(name, TelegramRoutes.TELEGRAM, Proxy.NO_PROXY, sender, sender);
    }

    private static SendMessage message() {
        return SendMessage.builder().chatId("42").text("Пары").build();
    }

    private static TelegramApiException connectionRefused() {
        return new TelegramApiException("Unable to execute sendmessage method", new ConnectException("Connection refused"));
    }

    @Test void normalizesAddressesFromEnv() {
        assertEquals("https://konyaevo.workers.dev/bot", TelegramRoutes.baseUrl("'https://konyaevo.workers.dev/'"));
        assertEquals("https://api.telegram.org/bot", TelegramRoutes.baseUrl("https://api.telegram.org/bot/"));
        assertEquals(TelegramRoutes.TELEGRAM, TelegramRoutes.baseUrl(" "));
    }

    @Test void fallbackIsBuiltFromProxyAloneAndDuplicatesAreIgnored() {
        var routes = new TelegramRoutes("1:test", "https://konyaevo.workers.dev", "NO_PROXY", "", 0,
                "", "SOCKS5", "127.0.0.1", 10808);
        assertEquals(2, routes.proxies().size());
        assertTrue(routes.describe().contains("fallback (api.telegram.org via SOCKS"));
        assertEquals(1, new TelegramRoutes("1:test", "", "NO_PROXY", "", 0, "", "NO_PROXY", "", 0).proxies().size());
        var duplicate = new TelegramRoutes("1:test", "", "SOCKS5", "127.0.0.1", 10808, "", "SOCKS5", "127.0.0.1", 10808);
        assertFalse(duplicate.describe().contains("fallback"));
    }

    @Test void unreachableRouteSwitchesToFallbackAndStaysThere() throws Exception {
        AbsSender primary = mock(AbsSender.class), fallback = mock(AbsSender.class);
        when(primary.execute(any(SendMessage.class))).thenThrow(connectionRefused());
        when(fallback.execute(any(SendMessage.class))).thenReturn(new Message());
        var routes = new TelegramRoutes("1:test", List.of(route("primary", primary), route("fallback", fallback)));
        assertNotNull(routes.call(sender -> sender.execute(message())));
        assertNotNull(routes.call(sender -> sender.execute(message())));
        verify(primary, times(1)).execute(any(SendMessage.class));
        verify(fallback, times(2)).execute(any(SendMessage.class));
        assertEquals(new TelegramRoutes.Health(true, "fallback"), routes.health());
    }

    @Test void answeredOrPossiblyDeliveredMessageIsNotSentTwice() throws Exception {
        AbsSender primary = mock(AbsSender.class), fallback = mock(AbsSender.class);
        var rejected = mock(TelegramApiRequestException.class);
        when(rejected.getErrorCode()).thenReturn(403);
        when(primary.execute(any(SendMessage.class))).thenThrow(rejected)
                .thenThrow(new TelegramApiException("Unable to execute sendmessage method", new SocketTimeoutException("Read timed out")));
        var routes = new TelegramRoutes("1:test", List.of(route("primary", primary), route("fallback", fallback)));
        assertSame(rejected, assertThrows(TelegramApiException.class, () -> routes.call(sender -> sender.execute(message()))));
        // Таймаут чтения: Telegram мог уже принять сообщение, повтор через второй маршрут дал бы дубль.
        assertThrows(TelegramApiException.class, () -> routes.call(sender -> sender.execute(message())));
        verifyNoInteractions(fallback);
    }

    @Test void pollingFailsOverOnAnyNetworkErrorAndReturnsToPrimary() throws Exception {
        AbsSender primary = mock(AbsSender.class), fallback = mock(AbsSender.class);
        var me = new User(1L, "Konyaevo", true);
        when(primary.execute(any(GetMe.class)))
                .thenThrow(new TelegramApiException("Unable to execute getme method", new SocketTimeoutException("Read timed out")))
                .thenReturn(me);
        when(fallback.execute(any(GetMe.class))).thenReturn(me);
        var routes = new TelegramRoutes("1:test", List.of(route("primary", primary), route("fallback", fallback)));
        assertEquals(me, routes.poll(sender -> sender.execute(new GetMe())));
        assertEquals("fallback", routes.health().route());
        routes.preferPrimary();
        assertEquals("primary", routes.health().route());
    }

    @Test void gatewayPageInsteadOfTelegramAnswerCountsAsUnreachable() {
        var page = new TelegramApiRequestException("Unable to deserialize response", new RuntimeException("Unexpected character '<'"));
        assertTrue(TelegramRoutes.notConnected(new TelegramApiException("wrapped", page)));
        assertFalse(TelegramRoutes.notConnected(new TelegramApiException("Unable to execute", new SocketTimeoutException())));
    }

    @Test void healthIsDownUntilTelegramAnswers() {
        var routes = new TelegramRoutes("1:test", List.of(route("primary", mock(AbsSender.class))));
        assertEquals(new TelegramRoutes.Health(false, "primary"), routes.health());
    }

    @Test void preparedMessageGoesThroughNextRouteWhenProxyIsDown() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var received = new AtomicReference<String>();
        var path = new AtomicReference<String>();
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().toString());
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"ok\":true,\"result\":{\"id\":\"via-fallback\"},\"echo\":\"1:secret\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        int closedPort;
        try (var socket = new ServerSocket(0)) { closedPort = socket.getLocalPort(); }
        try {
            var dead = new TelegramRoutes.Route("primary", "http://telegram.invalid/bot",
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", closedPort)), null, null);
            var alive = new TelegramRoutes.Route("fallback", "http://telegram.invalid/bot",
                    new Proxy(Proxy.Type.HTTP, server.getAddress()), null, null);
            var routes = new TelegramRoutes("1:secret", List.of(dead, alive));
            var response = routes.postJson("savePreparedInlineMessage", "{\"user_id\":123}");
            assertEquals(200, response.status());
            assertTrue(response.body().contains("via-fallback"));
            assertFalse(response.body().contains("1:secret"), "token must be redacted");
            assertTrue(path.get().endsWith("/bot1:secret/savePreparedInlineMessage"));
            assertEquals("{\"user_id\":123}", received.get());
            assertEquals("fallback", routes.health().route());
        } finally {
            server.stop(0);
        }
    }
}
