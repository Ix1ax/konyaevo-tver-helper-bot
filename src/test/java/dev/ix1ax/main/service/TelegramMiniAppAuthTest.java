package dev.ix1ax.main.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ix1ax.main.controller.AdminApiController;
import dev.ix1ax.main.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TelegramMiniAppAuthTest {
    private static final String TOKEN = "123:test-token-for-unit-tests";
    private static final long NOW = 1790755200L;
    private final TelegramMiniAppAuth auth = new TelegramMiniAppAuth(TOKEN, new ObjectMapper(),
            Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));

    @Test
    void acceptsSignedDataAndRejectsTampering() throws Exception {
        String signed = signed(1669683599L, NOW);
        assertEquals(1669683599L, auth.requireUserId(signed));
        assertThrows(ResponseStatusException.class, () -> auth.requireUserId(signed.replace("1669683599", "1669683598")));
        assertThrows(ResponseStatusException.class, () -> auth.requireUserId(signed + "&auth_date=" + NOW));
    }

    @Test
    void rejectsMissingExpiredFutureAndMalformedData() throws Exception {
        for (String input : new String[]{null, "", "user=%QQ", signed(42, NOW - 3601), signed(42, NOW + 31), signed(-1, NOW)}) {
            assertThrows(ResponseStatusException.class, () -> auth.requireUserId(input));
        }
    }

    @Test
    void adminEndpointChecksAuthorizationBeforeReadingStatistics() throws Exception {
        AdminService admins = mock(AdminService.class);
        UserSettingsRepository users = mock(UserSettingsRepository.class);
        ScheduleParserService schedule = mock(ScheduleParserService.class);
        ChangesParserService changes = mock(ChangesParserService.class);
        when(admins.isAdmin(1669683599L)).thenReturn(true);
        var mvc = MockMvcBuilders.standaloneSetup(new AdminApiController(auth, admins, users, schedule, changes)).build();
        mvc.perform(get("/api/admin/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/overview").header("X-Telegram-Init-Data", signed(42, NOW)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/overview").header("X-Telegram-Init-Data", "user=1669683599"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users, schedule, changes);
        when(users.count()).thenReturn(100L);
        when(users.countByRole("student")).thenReturn(80L);
        when(users.countByRole("teacher")).thenReturn(12L);
        when(users.countWithNotifications()).thenReturn(60L);
        mvc.perform(get("/api/admin/overview").header("X-Telegram-Init-Data", signed(1669683599L, NOW)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.users").value(100))
                .andExpect(jsonPath("$.students").value(80))
                .andExpect(jsonPath("$.teachersUsingBot").value(12))
                .andExpect(jsonPath("$.unconfigured").value(8))
                .andExpect(jsonPath("$.notifications").value(60));
    }

    @Test
    void profileUsesSignedIdentityAndValidatesBeforeSaving() throws Exception {
        ScheduleService users = mock(ScheduleService.class);
        ScheduleParserService schedule = mock(ScheduleParserService.class);
        var user = new dev.ix1ax.main.model.UserSettings(42L);
        when(users.getOrCreateUser(42L)).thenReturn(user);
        when(schedule.getGroupsByCourse()).thenReturn(java.util.Map.of("1 курс", java.util.List.of("1-ИС")));
        var mvc = MockMvcBuilders.standaloneSetup(new dev.ix1ax.main.controller.ProfileApiController(auth, users, schedule)).build();
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/profile")
                .header("X-Telegram-Init-Data", signed(42, NOW)).contentType("application/json")
                .content("{\"role\":\"student\",\"group\":\"1-ИС\",\"changes\":true,\"tomorrow\":true,\"time\":\"18:30\",\"days\":[1,3,5]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changes").value(true))
                .andExpect(jsonPath("$.tomorrow").value(true)).andExpect(jsonPath("$.time").value("18:30"));
        assertEquals(java.util.Set.of(1,3,5), user.getNotifyDaysSet());
        assertEquals("1-ИС", user.getGroupName());
        verify(users).saveUser(user);
        clearInvocations(users);
        for (String invalid : new String[]{"{\"time\":\"25:00\"}", "{\"days\":[8]}", "{\"group\":\"missing\"}", "{\"role\":\"admin\"}"}) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/profile")
                    .header("X-Telegram-Init-Data", signed(42, NOW)).contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        }
        verify(users, never()).saveUser(any());
        mvc.perform(get("/api/profile").header("X-Telegram-Init-Data", signed(42, NOW)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.group").value("1-ИС"));
    }

    @Test
    void collegeChangesRequireAdminOnBothOldAndNewRoutes() throws Exception {
        AdminService admins = mock(AdminService.class);
        ChangesParserService changes = mock(ChangesParserService.class);
        when(admins.isAdmin(1669683599L)).thenReturn(true);
        when(changes.getAllChanges()).thenReturn(java.util.Map.of("4-ИС2", java.util.Map.of(2, "отмена")));
        when(changes.isCancellation("отмена")).thenReturn(true);
        var mvc = MockMvcBuilders.standaloneSetup(new dev.ix1ax.main.controller.AdminChangesController(auth, admins, changes)).build();
        for (String path : new String[]{"/api/changes/all", "/api/admin/changes"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("X-Telegram-Init-Data", signed(42, NOW))).andExpect(status().isForbidden());
            mvc.perform(get(path).header("X-Telegram-Init-Data", signed(1669683599L, NOW)))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$[0].groupName").value("4-ИС2"))
                    .andExpect(jsonPath("$[0].canceled").value(true));
        }
        verify(changes, times(2)).getAllChanges();
    }

    private String signed(long id, long date) throws Exception {
        var fields = new TreeMap<String, String>();
        fields.put("user", "{\"id\":" + id + ",\"first_name\":\"Тест & QA\"}");
        fields.put("auth_date", Long.toString(date));
        fields.put("query_id", "test-query");
        fields.put("signature", "test-signature");
        String check = fields.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n"));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] secret = mac.doFinal(TOKEN.getBytes(StandardCharsets.UTF_8));
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        fields.put("hash", HexFormat.of().formatHex(mac.doFinal(check.getBytes(StandardCharsets.UTF_8))));
        return fields.entrySet().stream().map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
