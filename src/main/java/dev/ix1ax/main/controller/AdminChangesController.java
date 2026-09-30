package dev.ix1ax.main.controller;

import dev.ix1ax.main.dto.ChangesResponseDto.ChangeItemDto;
import dev.ix1ax.main.service.AdminService;
import dev.ix1ax.main.service.ChangesParserService;
import dev.ix1ax.main.service.TelegramMiniAppAuth;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Общий список замен доступен только администратору, включая прежний адрес API. */
@RestController
public class AdminChangesController {
    private final TelegramMiniAppAuth auth;
    private final AdminService admins;
    private final ChangesParserService changes;
    public AdminChangesController(TelegramMiniAppAuth auth, AdminService admins, ChangesParserService changes) {
        this.auth = auth; this.admins = admins; this.changes = changes;
    }
    @GetMapping({"/api/admin/changes", "/api/changes/all"})
    public ResponseEntity<List<ChangeItemDto>> all(@RequestHeader(value="X-Telegram-Init-Data", required=false) String data) {
        long id = auth.requireUserId(data);
        if (!admins.isAdmin(id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        List<ChangeItemDto> items = new ArrayList<>();
        changes.getAllChanges().forEach((group, slots) -> slots.forEach((slot, text) -> items.add(
                ChangeItemDto.builder().groupName(group).slot(slot).text(text).canceled(changes.isCancellation(text)).build())));
        items.sort(Comparator.comparing(ChangeItemDto::getGroupName).thenComparingInt(ChangeItemDto::getSlot));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(items);
    }
}
