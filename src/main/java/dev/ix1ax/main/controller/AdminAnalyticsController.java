package dev.ix1ax.main.controller;

import dev.ix1ax.main.service.AdminAnalyticsService;
import dev.ix1ax.main.service.AdminService;
import dev.ix1ax.main.service.TelegramMiniAppAuth;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;

/** Графики админки: активность по дням, курсы и группы, огонёк, поддержка. Только для администраторов. */
@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {
    private final TelegramMiniAppAuth auth;
    private final AdminService admins;
    private final AdminAnalyticsService analytics;
    public AdminAnalyticsController(TelegramMiniAppAuth auth, AdminService admins, AdminAnalyticsService analytics) {
        this.auth = auth; this.admins = admins; this.analytics = analytics;
    }

    @GetMapping
    public ResponseEntity<AdminAnalyticsService.Analytics> get(@RequestHeader(value = "X-Telegram-Init-Data", required = false) String data,
                                                               @RequestParam(defaultValue = "30") int days) {
        long user = auth.requireUserId(data);
        if (!admins.isAdmin(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(analytics.analytics(days, LocalDate.now(ZoneId.of("Europe/Moscow"))));
    }
}
