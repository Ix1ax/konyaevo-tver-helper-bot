package dev.ix1ax.main.controller;

import dev.ix1ax.main.service.MascotService;
import dev.ix1ax.main.service.TelegramMiniAppAuth;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Supplier;

/** Огонёк, опыт и облики Парыча для мини-аппа. */
@RestController
@RequestMapping("/api/mascot")
public class MascotApiController {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private final TelegramMiniAppAuth auth;
    private final MascotService mascot;
    public MascotApiController(TelegramMiniAppAuth auth, MascotService mascot) { this.auth = auth; this.mascot = mascot; }

    public record Event(String type) {}
    public record Settings(String look, Boolean reminder) {}

    @GetMapping
    public ResponseEntity<MascotService.State> get(@RequestHeader(value="X-Telegram-Init-Data", required=false) String data) {
        long id = auth.requireUserId(data);
        return ok(() -> mascot.state(id, LocalDateTime.now(MOSCOW).toLocalDate()));
    }

    @PostMapping("/event")
    public ResponseEntity<MascotService.State> event(@RequestHeader(value="X-Telegram-Init-Data", required=false) String data,
                                                     @RequestBody Event event) {
        long id = auth.requireUserId(data);
        return ok(() -> mascot.event(id, event.type() == null ? "" : event.type(), LocalDateTime.now(MOSCOW)));
    }

    @PostMapping("/settings")
    public ResponseEntity<MascotService.State> settings(@RequestHeader(value="X-Telegram-Init-Data", required=false) String data,
                                                        @RequestBody Settings settings) {
        long id = auth.requireUserId(data);
        return ok(() -> mascot.settings(id, settings.look(), settings.reminder(), LocalDateTime.now(MOSCOW).toLocalDate()));
    }

    @PostMapping("/rewards/ack")
    public ResponseEntity<MascotService.State> acknowledge(@RequestHeader(value="X-Telegram-Init-Data", required=false) String data) {
        long id = auth.requireUserId(data);
        return ok(() -> mascot.acknowledgeRewards(id, LocalDateTime.now(MOSCOW).toLocalDate()));
    }

    private static ResponseEntity<MascotService.State> ok(Supplier<MascotService.State> action) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
