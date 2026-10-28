package dev.ix1ax.main.controller;

import dev.ix1ax.main.service.DonationService;
import dev.ix1ax.main.service.TelegramMiniAppAuth;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/** Счёт в звёздах для кнопки «Поддержать» в мини-аппе. */
@RestController
@RequestMapping("/api/donate")
public class DonationApiController {
    private final TelegramMiniAppAuth auth;
    private final DonationService donations;
    public DonationApiController(TelegramMiniAppAuth auth, DonationService donations) { this.auth = auth; this.donations = donations; }

    public record Request(Integer stars) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> invoice(@RequestHeader(value = "X-Telegram-Init-Data", required = false) String data,
                                                       @RequestBody Request request) {
        long user = auth.requireUserId(data);
        if (request == null || request.stars() == null || !DonationService.allowed(request.stars()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сумма — от " + DonationService.MIN + " до " + DonationService.MAX + " звёзд");
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("url", donations.invoiceLink(user, request.stars())));
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось создать счёт. Попробуйте ещё раз.");
        }
    }
}
