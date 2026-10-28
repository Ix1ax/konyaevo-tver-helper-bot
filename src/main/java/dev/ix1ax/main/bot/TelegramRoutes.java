package dev.ix1ax.main.bot;

import dev.ix1ax.main.util.EnvValue;
import org.apache.http.client.config.RequestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Маршруты до Telegram Bot API: основной и запасной (например, Cloudflare Worker и api.telegram.org
 * через локальный SOCKS5). Запрос идёт активным маршрутом; на второй он переходит, только если соединение
 * не установилось, поэтому сообщение не уйдёт дважды. Опрос обновлений переключается при любой сетевой ошибке.
 */
@Component
public class TelegramRoutes {
    private static final Logger log = LoggerFactory.getLogger(TelegramRoutes.class);
    static final String TELEGRAM = "https://api.telegram.org/bot";
    // Обычный ответ Telegram приходит за ~100 мс: зависший запрос лучше оборвать быстро.
    private static final RequestConfig SENDING = RequestConfig.custom()
            .setConnectTimeout(4000).setSocketTimeout(5000).setConnectionRequestTimeout(4000).build();
    // Long polling держит соединение до 15 секунд (timeout getUpdates).
    private static final RequestConfig POLLING = RequestConfig.custom()
            .setConnectTimeout(6000).setSocketTimeout(25000).setConnectionRequestTimeout(6000).build();
    static final int POLL_TIMEOUT = 15;

    public interface Call<T> { T run(AbsSender sender) throws TelegramApiException; }
    public record Response(int status, String body) {}
    public record Health(boolean telegram, String route) {}

    record Route(String name, String baseUrl, Proxy proxy, AbsSender sender, AbsSender poller) {
        String describe() {
            String host = URI.create(baseUrl).getHost();
            return proxy == Proxy.NO_PROXY ? name + " (" + host + ")" : name + " (" + host + " via " + proxy + ")";
        }
    }

    private final List<Route> routes;
    private final String token;
    private final AtomicInteger active = new AtomicInteger();
    private volatile long lastContact;
    private HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    public TelegramRoutes(@Value("${bot.token}") String token,
                          @Value("${bot.base-url:" + TELEGRAM + "}") String baseUrl,
                          @Value("${bot.proxy.type:NO_PROXY}") String proxyType,
                          @Value("${bot.proxy.host:127.0.0.1}") String proxyHost,
                          @Value("${bot.proxy.port:10808}") int proxyPort,
                          @Value("${bot.fallback.base-url:}") String fallbackUrl,
                          @Value("${bot.fallback.proxy.type:NO_PROXY}") String fallbackType,
                          @Value("${bot.fallback.proxy.host:127.0.0.1}") String fallbackHost,
                          @Value("${bot.fallback.proxy.port:10808}") int fallbackPort) {
        this.token = EnvValue.clean(token);
        var list = new ArrayList<Route>();
        list.add(route("primary", this.token, baseUrl, proxyType, proxyHost, proxyPort));
        // Указан только прокси — запасной маршрут идёт напрямую в api.telegram.org через него.
        boolean fallback = !EnvValue.clean(fallbackUrl).isEmpty() || proxyType(fallbackType) != DefaultBotOptions.ProxyType.NO_PROXY;
        if (fallback) {
            Route second = route("fallback", this.token, EnvValue.clean(fallbackUrl).isEmpty() ? TELEGRAM : fallbackUrl,
                    fallbackType, fallbackHost, fallbackPort);
            if (second.baseUrl().equals(list.get(0).baseUrl()) && second.proxy().equals(list.get(0).proxy()))
                log.warn("[TELEGRAM ROUTE] Fallback route repeats the primary one and is ignored");
            else list.add(second);
        }
        routes = List.copyOf(list);
        routes.forEach(route -> log.info("[TELEGRAM ROUTE] {}", route.describe()));
    }

    TelegramRoutes(String token, List<Route> routes) {
        this.token = token;
        this.routes = List.copyOf(routes);
    }

    /** Один готовый отправитель без переключения — для тестов. */
    public static TelegramRoutes of(AbsSender sender) {
        return new TelegramRoutes("", List.of(new Route("primary", TELEGRAM, Proxy.NO_PROXY, sender, sender)));
    }

    static String baseUrl(String value) {
        String url = EnvValue.clean(value);
        if (url.isEmpty()) return TELEGRAM;
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url.endsWith("/bot") ? url : url + "/bot";
    }

    static DefaultBotOptions.ProxyType proxyType(String value) {
        return switch (EnvValue.clean(value).toUpperCase(Locale.ROOT)) {
            case "SOCKS5" -> DefaultBotOptions.ProxyType.SOCKS5;
            case "SOCKS4" -> DefaultBotOptions.ProxyType.SOCKS4;
            case "HTTP" -> DefaultBotOptions.ProxyType.HTTP;
            default -> DefaultBotOptions.ProxyType.NO_PROXY;
        };
    }

    private static Route route(String name, String token, String url, String type, String host, int port) {
        String base = baseUrl(url);
        DefaultBotOptions.ProxyType proxyType = proxyType(type);
        String proxyHost = EnvValue.clean(host).isEmpty() ? "127.0.0.1" : EnvValue.clean(host);
        int proxyPort = port > 0 ? port : 10808;
        Proxy proxy = proxyType == DefaultBotOptions.ProxyType.NO_PROXY ? Proxy.NO_PROXY
                : new Proxy(proxyType == DefaultBotOptions.ProxyType.HTTP ? Proxy.Type.HTTP : Proxy.Type.SOCKS,
                        new InetSocketAddress(proxyHost, proxyPort));
        return new Route(name, base, proxy, sender(token, base, proxyType, proxyHost, proxyPort, SENDING),
                sender(token, base, proxyType, proxyHost, proxyPort, POLLING));
    }

    private static AbsSender sender(String token, String base, DefaultBotOptions.ProxyType type, String host, int port, RequestConfig config) {
        DefaultBotOptions options = new DefaultBotOptions();
        options.setMaxThreads(1); // асинхронные вызовы не используются
        options.setBaseUrl(base);
        options.setRequestConfig(config);
        options.setGetUpdatesTimeout(POLL_TIMEOUT);
        if (type != DefaultBotOptions.ProxyType.NO_PROXY) {
            options.setProxyType(type);
            options.setProxyHost(host);
            options.setProxyPort(port);
        }
        return new DefaultAbsSender(options, token) {};
    }

    /** Отправка: на запасной маршрут только если до Telegram не удалось подключиться. */
    public <T> T call(Call<T> call) throws TelegramApiException {
        return run(call, false);
    }

    /** Опрос обновлений и служебные запросы, которые безопасно повторить. */
    public <T> T poll(Call<T> call) throws TelegramApiException {
        return run(call, true);
    }

    private <T> T run(Call<T> call, boolean repeatable) throws TelegramApiException {
        int first = active.get();
        TelegramApiException failure = null;
        for (int i = 0; i < routes.size(); i++) {
            int index = (first + i) % routes.size();
            Route route = routes.get(index);
            try {
                T result = call.run(repeatable ? route.poller() : route.sender());
                lastContact = System.currentTimeMillis();
                activate(index);
                return result;
            } catch (TelegramApiException e) {
                boolean answered = e instanceof TelegramApiRequestException request && answered(request);
                if (answered || !(notConnected(e) || repeatable && TelegramErrorClassifier.isNetworkError(e))) throw e;
                failure = e;
                if (routes.size() > 1) log.warn("[TELEGRAM ROUTE] {} unavailable: {}", route.describe(), reason(e));
            }
        }
        throw failure;
    }

    private void activate(int index) {
        int previous = active.getAndSet(index);
        if (previous != index)
            log.warn("[TELEGRAM ROUTE] Switched from {} to {}", routes.get(previous).describe(), routes.get(index).describe());
    }

    /** Запрос до Telegram не дошёл: нет соединения с прокси или Worker вернул не ответ Telegram. */
    static boolean notConnected(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.apache.http.conn.ConnectTimeoutException
                    || cause instanceof ConnectException
                    || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException
                    || cause instanceof javax.net.ssl.SSLHandshakeException
                    || cause instanceof HttpConnectTimeoutException)
                return true;
            if (cause instanceof SocketException && cause.getMessage() != null && cause.getMessage().contains("SOCKS")) return true;
            if (cause instanceof TelegramApiRequestException request && !answered(request)
                    && request.getMessage() != null && request.getMessage().startsWith("Unable to deserialize")) return true;
        }
        return false;
    }

    /** Telegram вернул код ошибки (по умолчанию в библиотеке 0 — ответа не было). */
    private static boolean answered(TelegramApiRequestException error) {
        return error.getErrorCode() != null && error.getErrorCode() > 0;
    }

    static String reason(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage() == null ? "" : ": " + root.getMessage();
        return root.getClass().getSimpleName() + message;
    }

    /**
     * Запрос к методу Bot API, которого нет в библиотеке (например, savePreparedInlineMessage).
     * Повторяется на втором маршруте, если первый не ответил или вместо Telegram ответил шлюз.
     */
    public Response postJson(String method, String json) throws IOException, InterruptedException {
        int first = active.get();
        IOException failure = null;
        for (int i = 0; i < routes.size(); i++) {
            int index = (first + i) % routes.size();
            Route route = routes.get(index);
            try {
                Response response = post(route, method, json);
                if (response.status() >= 500 && !response.body().trim().startsWith("{"))
                    throw new IOException("Gateway HTTP " + response.status());
                lastContact = System.currentTimeMillis();
                activate(index);
                return response;
            } catch (IOException e) {
                failure = e;
                if (routes.size() > 1) log.warn("[TELEGRAM ROUTE] {} unavailable for {}: {}", route.describe(), method, reason(e));
            }
        }
        throw failure;
    }

    private Response post(Route route, String method, String json) throws IOException, InterruptedException {
        URI uri = URI.create(route.baseUrl() + token + "/" + method);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        if (route.proxy() == Proxy.NO_PROXY) {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), redact(response.body()));
        }
        // JDK HttpClient не умеет SOCKS: прокси задаётся на запрос, без глобальных настроек JVM.
        var connection = (HttpURLConnection) uri.toURL().openConnection(route.proxy());
        try {
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (var out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            try (var in = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                return new Response(status, redact(in == null ? "{}" : new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
        } finally {
            connection.disconnect();
        }
    }

    private String redact(String text) {
        return token.isEmpty() ? text : text.replace(token, "[redacted]");
    }

    /** Пока бот работает через запасной маршрут, каждые 5 минут проверяем, ожил ли основной. */
    @Scheduled(fixedDelay = 300000, initialDelay = 300000)
    public void preferPrimary() {
        if (active.get() == 0 || routes.size() < 2) return;
        try {
            routes.get(0).poller().execute(new GetMe());
            activate(0);
        } catch (TelegramApiException e) {
            log.debug("[TELEGRAM ROUTE] {} still unavailable: {}", routes.get(0).describe(), reason(e));
        }
    }

    /** Прокси маршрутов по порядку: через них ходят и другие внешние запросы (ImageKit). */
    public List<Proxy> proxies() {
        return routes.stream().map(Route::proxy).distinct().toList();
    }

    public String describe() {
        return routes.stream().map(Route::describe).reduce((a, b) -> a + ", " + b).orElse("");
    }

    public String activeRoute() {
        return routes.get(active.get()).describe();
    }

    /** Связь считается живой, если Telegram отвечал за последние полторы минуты (опрос идёт каждые 15 секунд). */
    public Health health() {
        boolean alive = System.currentTimeMillis() - lastContact < 90_000;
        return new Health(alive, routes.get(active.get()).name());
    }

    void setHttp(HttpClient http) {
        this.http = http;
    }
}
