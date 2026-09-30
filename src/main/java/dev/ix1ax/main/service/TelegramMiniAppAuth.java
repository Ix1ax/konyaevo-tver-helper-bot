package dev.ix1ax.main.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Проверяет подпись Telegram до чтения идентификатора пользователя. */
@Service
public class TelegramMiniAppAuth {
    private final String botToken;
    private final ObjectMapper mapper;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TelegramMiniAppAuth(@Value("${bot.token}") String botToken, ObjectMapper mapper) {
        this(botToken, mapper, Clock.systemUTC());
    }

    TelegramMiniAppAuth(String botToken, ObjectMapper mapper, Clock clock) {
        this.botToken = botToken;
        this.mapper = mapper;
        this.clock = clock;
    }

    public long requireUserId(String initData) {
        try {
            if (initData == null || initData.isBlank() || initData.length() > 16384) throw unauthorized();
            var fields = new TreeMap<String, String>();
            for (String pair : initData.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length != 2) throw unauthorized();
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                if (fields.putIfAbsent(key, value) != null) throw unauthorized();
            }
            String hash = fields.remove("hash");
            if (hash == null || !hash.matches("[0-9a-fA-F]{64}")) throw unauthorized();
            String checkString = fields.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("\n"));
            byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken);
            if (!MessageDigest.isEqual(hmac(secret, checkString), HexFormat.of().parseHex(hash))) throw unauthorized();

            long issued = Long.parseLong(fields.get("auth_date"));
            long now = Instant.now(clock).getEpochSecond();
            // Старые данные запуска не дают бессрочного доступа к панели.
            if (issued < now - 3600 || issued > now + 30) throw unauthorized();
            var user = mapper.readTree(fields.get("user"));
            if (!user.path("id").isIntegralNumber() || !user.path("id").canConvertToLong()) throw unauthorized();
            long id = user.path("id").longValue();
            if (id <= 0) throw unauthorized();
            return id;
        } catch (Exception ignored) {
            // Не возвращаем содержимое initData и детали проверки подписи клиенту.
            throw unauthorized();
        }
    }

    private static byte[] hmac(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Откройте приложение заново через Telegram");
    }
}
