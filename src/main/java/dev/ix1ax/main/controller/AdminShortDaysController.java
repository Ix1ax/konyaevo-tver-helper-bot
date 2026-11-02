package dev.ix1ax.main.controller;

import dev.ix1ax.main.service.AdminService;
import dev.ix1ax.main.service.ShortDayService;
import dev.ix1ax.main.service.TelegramMiniAppAuth;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.TreeMap;

/** Сокращённые дни для администратора: список ближайших, добавить и убрать дату. */
@RestController
@RequestMapping("/api/admin/short-days")
public class AdminShortDaysController {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private final TelegramMiniAppAuth auth;
    private final AdminService admins;
    private final ShortDayService shortDays;
    public AdminShortDaysController(TelegramMiniAppAuth auth, AdminService admins, ShortDayService shortDays) {
        this.auth = auth; this.admins = admins; this.shortDays = shortDays;
    }
    public record Request(String date) {}

    private void requireAdmin(String data) {
        if (!admins.isAdmin(auth.requireUserId(data))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<Map<String, Object>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "days", shortDays.upcoming(LocalDate.now(MOSCOW)).stream().map(LocalDate::toString).toList(),
                "times", new TreeMap<>(ShortDayService.TIMES)));
    }

    private static LocalDate parse(String value) {
        try { return LocalDate.parse(value); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата в формате ГГГГ-ММ-ДД"); }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> get(@RequestHeader(value = "X-Telegram-Init-Data", required = false) String data) {
        requireAdmin(data);
        return list();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> add(@RequestHeader(value = "X-Telegram-Init-Data", required = false) String data, @RequestBody Request request) {
        requireAdmin(data);
        LocalDate date = parse(request == null ? null : request.date());
        if (date.isBefore(LocalDate.now(MOSCOW))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата уже прошла");
        shortDays.add(date);
        return list();
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Map<String, Object>> remove(@RequestHeader(value = "X-Telegram-Init-Data", required = false) String data, @PathVariable String date) {
        requireAdmin(data);
        shortDays.remove(parse(date));
        return list();
    }
}
