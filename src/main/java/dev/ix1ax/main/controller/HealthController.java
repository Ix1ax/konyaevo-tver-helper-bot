package dev.ix1ax.main.controller;

import dev.ix1ax.main.bot.TelegramRoutes;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Проверка после обновления: API отвечает, бот на связи с Telegram и каким маршрутом. Адресов и прокси не раскрывает. */
@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final TelegramRoutes telegram;
    public HealthController(TelegramRoutes telegram) { this.telegram = telegram; }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        var state = telegram.health();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("api", "ok", "telegram", state.telegram() ? "ok" : "down", "route", state.route()));
    }
}
