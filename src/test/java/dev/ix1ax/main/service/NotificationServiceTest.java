package dev.ix1ax.main.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import dev.ix1ax.main.bot.MessageSender;
import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.repository.UserSettingsRepository;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationServiceTest {

    private NotificationService notificationService;
    private TestUserSettingsRepository userRepo;
    private TestMessageSender messageSender;
    private TestChangesParser changesParser;

    @BeforeEach
    public void setUp() {
        userRepo = new TestUserSettingsRepository();
        messageSender = new TestMessageSender();
        changesParser = new TestChangesParser();
        notificationService = new NotificationService(userRepo, changesParser, messageSender);
    }

    @Test
    public void testNoUsersForCurrentTime() {
        notificationService.processNotificationsForTime("07:00");
        assertTrue(messageSender.sentMessages.isEmpty());
    }

    @Test
    public void testStudentNotificationSent() {
        UserSettings student = new UserSettings(100L);
        student.setRole("student");
        student.setGroupName("3-ИС4");
        student.setNotifyEnabled(true);
        student.setNotifyTime("07:30");
        userRepo.users.add(student);

        notificationService.processNotificationsForTime("07:30");

        assertEquals(1, messageSender.sentMessages.size());
        TestMessageSender.SentRecord record = messageSender.sentMessages.get(0);
        assertEquals(100L, record.chatId);
        assertTrue(record.text.contains("3-ИС4"));
    }

    @Test
    public void testTeacherNotificationSent() {
        UserSettings teacher = new UserSettings(200L);
        teacher.setRole("teacher");
        teacher.setTeacherName("Самсонов И.В.");
        teacher.setNotifyEnabled(true);
        teacher.setNotifyTime("08:00");
        userRepo.users.add(teacher);

        notificationService.processNotificationsForTime("08:00");

        assertEquals(1, messageSender.sentMessages.size());
        TestMessageSender.SentRecord record = messageSender.sentMessages.get(0);
        assertEquals(200L, record.chatId);
        assertTrue(record.text.contains("Самсонов И.В."));
    }

    @Test
    public void testBlockedUserAutoDisabled() {
        UserSettings blockedUser = new UserSettings(300L);
        blockedUser.setRole("student");
        blockedUser.setGroupName("1-ТМС");
        blockedUser.setNotifyEnabled(true);
        blockedUser.setNotifyTime("07:00");
        userRepo.users.add(blockedUser);

        messageSender.resultToReturn = MessageSender.DirectSendResult.BLOCKED;

        notificationService.processNotificationsForTime("07:00");

        assertEquals(1, messageSender.sentMessages.size());
        assertFalse(blockedUser.getNotifyEnabled(), "Blocked user's notifications should be automatically disabled");
    }

    @Test
    public void testUnconfiguredUserSkipped() {
        UserSettings unconfigured = new UserSettings(400L);
        unconfigured.setNotifyEnabled(true);
        unconfigured.setNotifyTime("07:00");
        userRepo.users.add(unconfigured);

        notificationService.processNotificationsForTime("07:00");

        assertTrue(messageSender.sentMessages.isEmpty(), "User without role/group should be skipped");
    }

    // ===== Test Stubs =====

    private static class TestChangesParser extends ChangesParserService {
        @Override
        public String getFormattedChanges(String groupName) {
            return "⚡️ Изменения для группы " + groupName;
        }

        @Override
        public String getFormattedChangesForTeacher(String teacherName) {
            return "⚡️ Изменения для преподавателя " + teacherName;
        }
    }

    private static class TestMessageSender extends MessageSender {
        static class SentRecord {
            final long chatId;
            final String text;
            SentRecord(long chatId, String text) {
                this.chatId = chatId;
                this.text = text;
            }
        }

        final List<SentRecord> sentMessages = new ArrayList<>();
        DirectSendResult resultToReturn = DirectSendResult.SUCCESS;

        TestMessageSender() {
            super(null);
        }

        @Override
        public DirectSendResult sendDirectMessage(long chatId, String text, InlineKeyboardMarkup keyboard) {
            sentMessages.add(new SentRecord(chatId, text));
            return resultToReturn;
        }
    }

    private static class TestUserSettingsRepository implements UserSettingsRepository {
        final List<UserSettings> users = new ArrayList<>();

        @Override
        public List<UserSettings> findByNotifyEnabledTrueAndNotifyTime(String time) {
            List<UserSettings> matched = new ArrayList<>();
            for (UserSettings u : users) {
                if (Boolean.TRUE.equals(u.getNotifyEnabled()) && time.equals(u.getNotifyTime())) {
                    matched.add(u);
                }
            }
            return matched;
        }

        @Override
        public UserSettings save(UserSettings entity) {
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).getChatId().equals(entity.getChatId())) {
                    users.set(i, entity);
                    return entity;
                }
            }
            users.add(entity);
            return entity;
        }

        @Override public long countByRole(String role) { return 0; }
        @Override public long countByCourse(Integer course) { return 0; }
        @Override public long countByNotifyEnabledTrue() {
            return users.stream().filter(u -> Boolean.TRUE.equals(u.getNotifyEnabled())).count();
        }
        @Override public List<Long> findAllChatIds() { return Collections.emptyList(); }
        @Override public List<Object[]> findTopGroups(Pageable pageable) { return Collections.emptyList(); }
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
        @Override public <S extends UserSettings> Page<S> findAll(Example<S> example, Pageable pageable) { return null; }
        @Override public <S extends UserSettings> long count(Example<S> example) { return 0; }
        @Override public <S extends UserSettings> boolean exists(Example<S> example) { return false; }
        @Override public <S extends UserSettings, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends UserSettings> List<S> saveAll(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public Optional<UserSettings> findById(Long aLong) {
            return users.stream().filter(u -> u.getChatId().equals(aLong)).findFirst();
        }
        @Override public boolean existsById(Long aLong) { return false; }
        @Override public List<UserSettings> findAll() { return users; }
        @Override public List<UserSettings> findAllById(Iterable<Long> longs) { return Collections.emptyList(); }
        @Override public long count() { return users.size(); }
        @Override public void deleteById(Long aLong) {}
        @Override public void delete(UserSettings entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends UserSettings> entities) {}
        @Override public void deleteAll() {}
        @Override public List<UserSettings> findAll(Sort sort) { return Collections.emptyList(); }
        @Override public Page<UserSettings> findAll(Pageable pageable) { return null; }
    }
}
