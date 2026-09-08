package dev.ix1ax.main.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import dev.ix1ax.main.bot.KeyboardFactory;
import dev.ix1ax.main.bot.MessageSender;
import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.repository.UserSettingsRepository;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class AdminServiceTest {

    private AdminService adminService;
    private StubUserSettingsRepository userRepo;

    @BeforeEach
    public void setUp() {
        userRepo = new StubUserSettingsRepository();
        // Null or lightweight stubs
        adminService = new AdminService(
                userRepo,
                new ScheduleParserService(),
                new ChangesParserService(),
                new ScheduleService(null, null, null),
                new MessageSender(null)
        );
        adminService.init();
    }

    @Test
    public void testAdminAuthorization() {
        // Primary admin 1669683599 is always authorized
        assertTrue(adminService.isAdmin(1669683599L));

        // Random users are not authorized
        assertFalse(adminService.isAdmin(123456789L));
        assertFalse(adminService.isAdmin(null));
    }

    @Test
    public void testBroadcastDraftManagement() {
        var draft = adminService.createDraft(1669683599L, "Привет, студенты!");
        assertNotNull(draft);
        assertNotNull(draft.id());
        assertEquals("Привет, студенты!", draft.text());
        assertEquals(1669683599L, draft.adminChatId());

        // Retrieve draft
        var fetched = adminService.getDraft(draft.id());
        assertNotNull(fetched);
        assertEquals(draft.id(), fetched.id());

        // Total recipients
        userRepo.totalCount = 42;
        assertEquals(42, adminService.getTotalRecipients());

        // Remove draft
        adminService.removeDraft(draft.id());
        assertNull(adminService.getDraft(draft.id()));
    }

    @Test
    public void testAdminKeyboards() {
        var adminKb = KeyboardFactory.buildAdminKeyboard();
        assertNotNull(adminKb);
        assertEquals(3, adminKb.getKeyboard().size());
        assertEquals("admin:stats", adminKb.getKeyboard().get(0).get(0).getCallbackData());
        assertEquals("admin:refresh", adminKb.getKeyboard().get(0).get(1).getCallbackData());
        assertEquals("admin:broadcast_info", adminKb.getKeyboard().get(1).get(0).getCallbackData());
        assertEquals("admin:close", adminKb.getKeyboard().get(2).get(0).getCallbackData());

        var confirmKb = KeyboardFactory.buildBroadcastConfirmKeyboard("draft123");
        assertNotNull(confirmKb);
        assertEquals(1, confirmKb.getKeyboard().size());
        assertEquals("admin:bc_send:draft123", confirmKb.getKeyboard().get(0).get(0).getCallbackData());
        assertEquals("admin:bc_cancel:draft123", confirmKb.getKeyboard().get(0).get(1).getCallbackData());

        var backKb = KeyboardFactory.buildAdminBackKeyboard();
        assertNotNull(backKb);
        assertEquals("admin:menu", backKb.getKeyboard().get(0).get(0).getCallbackData());
    }

    // Lightweight stub for UserSettingsRepository
    private static class StubUserSettingsRepository implements UserSettingsRepository {
        long totalCount = 0;

        @Override public long count() { return totalCount; }
        @Override public long countByRole(String role) { return 0; }
        @Override public long countByCourse(Integer course) { return 0; }
        @Override public List<Long> findAllChatIds() { return List.of(1669683599L); }
        @Override public List<Object[]> findTopGroups(Pageable pageable) { return Collections.emptyList(); }
        @Override public List<UserSettings> findByNotifyEnabledTrueAndNotifyTime(String time) { return Collections.emptyList(); }
        @Override public long countByNotifyEnabledTrue() { return 0; }

        @Override public void flush() {}
        @Override public <S extends UserSettings> S saveAndFlush(S entity) { return entity; }
        @Override public <S extends UserSettings> List<S> saveAllAndFlush(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public void deleteAllInBatch(Iterable<UserSettings> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public UserSettings getOne(Long aLong) { return null; }
        @Override public UserSettings getById(Long aLong) { return null; }
        @Override public UserSettings getReferenceById(Long aLong) { return null; }
        @Override public <S extends UserSettings> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends UserSettings> List<S> findAll(Example<S> example) { return Collections.emptyList(); }
        @Override public <S extends UserSettings> List<S> findAll(Example<S> example, Sort sort) { return Collections.emptyList(); }
        @Override public <S extends UserSettings> Page<S> findAll(Example<S> example, Pageable pageable) { return new PageImpl<>(Collections.emptyList()); }
        @Override public <S extends UserSettings> long count(Example<S> example) { return 0; }
        @Override public <S extends UserSettings> boolean exists(Example<S> example) { return false; }
        @Override public <S extends UserSettings, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends UserSettings> S save(S entity) { return entity; }
        @Override public <S extends UserSettings> List<S> saveAll(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public Optional<UserSettings> findById(Long aLong) { return Optional.empty(); }
        @Override public boolean existsById(Long aLong) { return false; }
        @Override public List<UserSettings> findAll() { return Collections.emptyList(); }
        @Override public List<UserSettings> findAllById(Iterable<Long> longs) { return Collections.emptyList(); }
        @Override public void deleteById(Long aLong) {}
        @Override public void delete(UserSettings entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends UserSettings> entities) {}
        @Override public void deleteAll() {}
        @Override public List<UserSettings> findAll(Sort sort) { return Collections.emptyList(); }
        @Override public Page<UserSettings> findAll(Pageable pageable) { return new PageImpl<>(Collections.emptyList()); }
    }
}
