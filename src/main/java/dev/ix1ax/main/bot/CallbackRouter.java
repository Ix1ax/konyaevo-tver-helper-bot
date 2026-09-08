package dev.ix1ax.main.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.service.ScheduleService;

/**
 * Routes Telegram callback queries to the appropriate screen.
 * Handles both student and teacher flows, eliminating duplication.
 */
@Component
public class CallbackRouter {

    private static final Logger log = LoggerFactory.getLogger(CallbackRouter.class);

    private final ScheduleService scheduleService;
    private final MessageSender messageSender;
    private final dev.ix1ax.main.service.AdminService adminService;

    public CallbackRouter(ScheduleService scheduleService,
                          MessageSender messageSender,
                          dev.ix1ax.main.service.AdminService adminService) {
        this.scheduleService = scheduleService;
        this.messageSender = messageSender;
        this.adminService = adminService;
    }

    /**
     * Route a callback query to the appropriate handler.
     */
    public void route(CallbackQuery callback) {
        String data = callback.getData();
        long chatId = callback.getMessage().getChatId();
        int messageId = callback.getMessage().getMessageId();

        UserSettings user = scheduleService.getOrCreateUser(chatId);
        user.setMessageId(messageId);

        String sender = callback.getFrom() != null
                ? (callback.getFrom().getUserName() != null
                    ? "@" + callback.getFrom().getUserName()
                    : callback.getFrom().getFirstName())
                : "id:" + chatId;

        log.info("[USER CLICK] ChatId: {} ({}) clicked: '{}'", chatId, sender, data);

        // ===== Admin flow =====
        if (data.startsWith("admin:")) {
            if (!adminService.isAdmin(chatId)) {
                log.warn("[SECURITY] Non-admin chatId {} clicked admin callback: {}", chatId, data);
                return;
            }
            handleAdminCallback(chatId, messageId, data);
            return;
        }

        // ===== Navigation =====
        if (data.equals("logout")) {
            user = scheduleService.resetUser(chatId);
            showMainMenu(chatId, messageId);
        } else if (data.equals("main")) {
            showMainMenu(chatId, messageId);

        // ===== Student flow =====
        } else if (data.equals("role:student")) {
            user.setRole("student");
            scheduleService.saveUser(user);
            showCourseSelection(chatId, messageId);
        } else if (data.startsWith("course:")) {
            String courseName = data.substring("course:".length());
            user.setCourse(Integer.parseInt(courseName.replaceAll("\\D", "")));
            scheduleService.saveUser(user);
            showGroupSelection(chatId, messageId, courseName);
        } else if (data.startsWith("group:")) {
            String groupName = data.substring("group:".length());
            user.setGroupName(groupName);
            scheduleService.saveUser(user);
            showStudentActions(chatId, messageId, groupName);
        } else if (data.startsWith("s_today:")) {
            showScheduleDay(chatId, messageId, data.substring("s_today:".length()),
                    scheduleService.getTodayName(), false);
        } else if (data.startsWith("s_tomorrow:")) {
            showScheduleDay(chatId, messageId, data.substring("s_tomorrow:".length()),
                    scheduleService.getTomorrowName(), false);
        } else if (data.startsWith("s_week:")) {
            showWeekSchedule(chatId, messageId, data.substring("s_week:".length()), false);
        } else if (data.startsWith("s_changes:")) {
            showChanges(chatId, messageId, data.substring("s_changes:".length()), false);

        // ===== Teacher flow =====
        } else if (data.equals("role:teacher")) {
            user.setRole("teacher");
            scheduleService.saveUser(user);
            showTeacherLetters(chatId, messageId);
        } else if (data.startsWith("tletter:")) {
            showTeachersByLetter(chatId, messageId, data.substring("tletter:".length()));
        } else if (data.startsWith("teacher:")) {
            String teacherName = data.substring("teacher:".length());
            user.setTeacherName(teacherName);
            scheduleService.saveUser(user);
            showTeacherActions(chatId, messageId, teacherName);
        } else if (data.startsWith("t_today:")) {
            showScheduleDay(chatId, messageId, data.substring("t_today:".length()),
                    scheduleService.getTodayName(), true);
        } else if (data.startsWith("t_tomorrow:")) {
            showScheduleDay(chatId, messageId, data.substring("t_tomorrow:".length()),
                    scheduleService.getTomorrowName(), true);
        } else if (data.startsWith("t_week:")) {
            showWeekSchedule(chatId, messageId, data.substring("t_week:".length()), true);
        } else if (data.startsWith("t_changes:")) {
            showChanges(chatId, messageId, data.substring("t_changes:".length()), true);

        // ===== Notification flow =====
        } else if (data.equals("notify:settings")) {
            showNotifySettings(chatId, messageId, user);
        } else if (data.equals("notify:enable") || data.equals("notify:change_time")) {
            showTimePicker(chatId, messageId, user);
        } else if (data.equals("notify:disable")) {
            disableNotifications(chatId, messageId, user);
        } else if (data.startsWith("notify:time:")) {
            String time = data.substring("notify:time:".length());
            enableNotifications(chatId, messageId, user, time);
        } else if (data.equals("notify:custom_time")) {
            promptCustomTime(chatId, messageId, user);

        // ===== Back navigation =====
        } else if (data.equals("back:courses")) {
            showCourseSelection(chatId, messageId);
        } else if (data.startsWith("back:groups:")) {
            showGroupSelection(chatId, messageId, data.substring("back:groups:".length()));
        } else if (data.equals("back:tletters")) {
            showTeacherLetters(chatId, messageId);
        } else if (data.startsWith("back:tlist:")) {
            showTeachersByLetter(chatId, messageId, data.substring("back:tlist:".length()));
        } else if (data.startsWith("back:sactions:")) {
            showStudentActions(chatId, messageId, data.substring("back:sactions:".length()));
        } else if (data.startsWith("back:tactions:")) {
            showTeacherActions(chatId, messageId, data.substring("back:tactions:".length()));
        }
    }

    // ===== Screen renderers =====

    private void showMainMenu(long chatId, int messageId) {
        String text = "🏛 <b>Коняево — Расписание</b>\n\n" +
                "Тверской колледж им. А.Н. Коняева\n" +
                "🗓 Текущая: <b>" + scheduleService.getCurrentWeekBadge() + "</b>\n\n" +
                "Выберите, кто вы:";
        messageSender.editMessage(chatId, messageId, text, KeyboardFactory.buildRoleKeyboard());
    }

    private void showCourseSelection(long chatId, int messageId) {
        String text = "🎓 <b>Выберите курс:</b>";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildCourseKeyboard(scheduleService.getCourseNames()));
    }

    private void showGroupSelection(long chatId, int messageId, String courseName) {
        String text = "👥 <b>Выберите группу (" + courseName + "):</b>";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildGroupKeyboard(scheduleService.getGroupsForCourse(courseName), courseName));
    }

    private void showStudentActions(long chatId, int messageId, String groupName) {
        String text = "🏛 <b>Коняево — Расписание</b>\n\n" +
                "👥 <b>Группа: " + groupName + "</b>\n" +
                "🗓 Текущая: <b>" + scheduleService.getCurrentWeekBadge() + "</b>\n\n" +
                "Выберите действие:";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildStudentActionsKeyboard(groupName));
    }

    private void showTeacherLetters(long chatId, int messageId) {
        String text = "👨‍🏫 <b>Выберите первую букву фамилии:</b>";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildTeacherLettersKeyboard(scheduleService.getTeacherFirstLetters()));
    }

    private void showTeachersByLetter(long chatId, int messageId, String letter) {
        String text = "👨‍🏫 <b>Преподаватели на букву «" + letter + "»:</b>";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildTeacherListKeyboard(scheduleService.getTeachersByLetter(letter), letter));
    }

    private void showTeacherActions(long chatId, int messageId, String teacherName) {
        String text = "🏛 <b>Коняево — Расписание</b>\n\n" +
                "👨‍🏫 <b>" + teacherName + "</b>\n" +
                "🗓 Текущая: <b>" + scheduleService.getCurrentWeekBadge() + "</b>\n\n" +
                "Выберите действие:";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildTeacherActionsKeyboard(teacherName));
    }

    // ===== Unified schedule display (student & teacher share the same logic) =====

    /**
     * Show schedule for a single day.
     * @param name       group name (student) or teacher name (teacher)
     * @param dayName    day of week in Russian, or empty string for weekend
     * @param isTeacher  true for teacher view, false for student view
     */
    private void showScheduleDay(long chatId, int messageId, String name, String dayName, boolean isTeacher) {
        String text;
        if (dayName.isEmpty()) {
            String icon = isTeacher ? "👨‍🏫" : "👥";
            text = icon + " <b>" + name + "</b>\n\n✨ <i>Сегодня выходной день!</i>";
        } else {
            text = isTeacher
                    ? scheduleService.getScheduleTextForTeacher(name, dayName)
                    : scheduleService.getScheduleTextForGroup(name, dayName);
        }

        String backCallback = isTeacher ? "back:tactions:" + name : "back:sactions:" + name;
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildBackKeyboard("‹ Назад в меню", backCallback));
    }

    private void showWeekSchedule(long chatId, int messageId, String name, boolean isTeacher) {
        String text = isTeacher
                ? scheduleService.getWeekScheduleTextForTeacher(name)
                : scheduleService.getWeekScheduleTextForGroup(name);

        String backCallback = isTeacher ? "back:tactions:" + name : "back:sactions:" + name;
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildBackKeyboard("‹ Назад в меню", backCallback));
    }

    private void showChanges(long chatId, int messageId, String name, boolean isTeacher) {
        String text = isTeacher
                ? scheduleService.getChangesTextForTeacher(name)
                : scheduleService.getChangesTextForGroup(name);

        String backCallback = isTeacher ? "back:tactions:" + name : "back:sactions:" + name;
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildBackKeyboard("‹ Назад в меню", backCallback));
    }

    // ===== Public helpers for auto-login from KonyaevoBot =====

    /**
     * Build the main menu text (used for auto-login).
     */
    public String getMainMenuText() {
        return "🏛 <b>Коняево — Расписание</b>\n\n" +
                "Тверской колледж им. А.Н. Коняева\n" +
                "🗓 Текущая: <b>" + scheduleService.getCurrentWeekBadge() + "</b>\n\n" +
                "Выберите, кто вы:";
    }

    public String getStudentActionText(String groupName) {
        return "🏛 <b>Коняево — Расписание</b>\n\n" +
                "👥 <b>Группа: " + groupName + "</b>\n" +
                "🗓 Текущая: <b>" + scheduleService.getCurrentWeekBadge() + "</b>\n\n" +
                "Выберите действие:";
    }

    public String getTeacherActionText(String teacherName) {
        return "🏛 <b>Коняево — Расписание</b>\n\n" +
                "👨‍🏫 <b>" + teacherName + "</b>\n" +
                "🗓 Текущая: <b>" + scheduleService.getCurrentWeekBadge() + "</b>\n\n" +
                "Выберите действие:";
    }

    // ===== Notification screen handlers =====

    private String getBackCallback(UserSettings user) {
        if ("teacher".equals(user.getRole()) && user.getTeacherName() != null) {
            return "back:tactions:" + user.getTeacherName();
        }
        if ("student".equals(user.getRole()) && user.getGroupName() != null) {
            return "back:sactions:" + user.getGroupName();
        }
        return "main";
    }

    private void showNotifySettings(long chatId, int messageId, UserSettings user) {
        boolean enabled = Boolean.TRUE.equals(user.getNotifyEnabled());
        String time = user.getNotifyTime();
        String backCallback = getBackCallback(user);

        StringBuilder sb = new StringBuilder();
        sb.append("🔔 <b>Уведомления об изменениях</b>\n\n");

        if (enabled && time != null) {
            sb.append("Статус: ✅ <b>Включены</b>\n");
            sb.append("⏰ Время отправки: <b>").append(time).append(" (МСК)</b>\n\n");
            sb.append("Каждый день в указанное время бот отправит\n");
            sb.append("свежие замены для ");
            if ("teacher".equals(user.getRole())) {
                sb.append("вашего преподавателя.");
            } else {
                sb.append("вашей группы.");
            }
        } else {
            sb.append("Статус: ❌ <b>Выключены</b>\n\n");
            sb.append("Включите, чтобы каждый день получать\n");
            sb.append("свежие замены для ");
            if ("teacher".equals(user.getRole())) {
                sb.append("вашего преподавателя.");
            } else {
                sb.append("вашей группы.");
            }
        }

        messageSender.editMessage(chatId, messageId, sb.toString(),
                KeyboardFactory.buildNotifySettingsKeyboard(enabled, backCallback));
    }

    private void showTimePicker(long chatId, int messageId, UserSettings user) {
        String text = "⏰ <b>Выберите время уведомлений (МСК):</b>\n\n" +
                "Нажмите на готовое время или введите своё.";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildTimePickerKeyboard("notify:settings"));
    }

    private void enableNotifications(long chatId, int messageId, UserSettings user, String time) {
        user.setNotifyEnabled(true);
        user.setNotifyTime(time);
        scheduleService.saveUser(user);

        String text = "✅ <b>Уведомления включены!</b>\n\n" +
                "⏰ Каждый день в <b>" + time + " (МСК)</b> вы будете\n" +
                "получать свежие замены пар.";
        String backCallback = getBackCallback(user);
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildNotifySettingsKeyboard(true, backCallback));
    }

    private void disableNotifications(long chatId, int messageId, UserSettings user) {
        user.setNotifyEnabled(false);
        scheduleService.saveUser(user);

        String text = "🔕 <b>Уведомления выключены</b>\n\n" +
                "Вы больше не будете получать ежедневные замены.\n" +
                "Включить обратно можно в любой момент.";
        String backCallback = getBackCallback(user);
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildNotifySettingsKeyboard(false, backCallback));
    }

    private void promptCustomTime(long chatId, int messageId, UserSettings user) {
        String text = "⌨️ <b>Введите время в формате ЧЧ:ММ</b>\n\n" +
                "Например: <code>07:30</code> или <code>17:53</code>\n\n" +
                "Отправьте время сообщением в чат.";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildBackKeyboard("‹ Назад", "notify:settings"));
    }

    /**
     * Called from KonyaevoBot when user types a time like "07:30".
     * Returns true if time was valid and saved, false otherwise.
     */
    public boolean handleCustomTimeInput(long chatId, String text) {
        String trimmed = text.trim();

        // Validate HH:MM format
        if (!trimmed.matches("^\\d{1,2}:\\d{2}$")) {
            return false;
        }

        String[] parts = trimmed.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return false;
        }

        // Normalize to HH:mm
        String normalizedTime = String.format("%02d:%02d", hour, minute);

        UserSettings user = scheduleService.getOrCreateUser(chatId);
        if (user.getRole() == null || (user.getGroupName() == null && user.getTeacherName() == null)) {
            return false; // User hasn't completed setup
        }

        user.setNotifyEnabled(true);
        user.setNotifyTime(normalizedTime);
        scheduleService.saveUser(user);

        String resultText = "✅ <b>Уведомления включены!</b>\n\n" +
                "⏰ Каждый день в <b>" + normalizedTime + " (МСК)</b> вы будете\n" +
                "получать свежие замены пар.";
        String backCallback = getBackCallback(user);

        messageSender.sendNewMessage(chatId, resultText,
                KeyboardFactory.buildNotifySettingsKeyboard(true, backCallback),
                "custom notify time confirmation");

        return true;
    }

    // ===== Admin screen handlers =====

    private void handleAdminCallback(long chatId, int messageId, String data) {
        if (data.equals("admin:menu")) {
            showAdminMenu(chatId, messageId);
        } else if (data.equals("admin:stats")) {
            showAdminStats(chatId, messageId);
        } else if (data.equals("admin:refresh")) {
            handleAdminRefresh(chatId, messageId);
        } else if (data.equals("admin:broadcast_info")) {
            showBroadcastInfo(chatId, messageId);
        } else if (data.startsWith("admin:bc_send:")) {
            String draftId = data.substring("admin:bc_send:".length());
            adminService.startBroadcast(draftId, messageId);
        } else if (data.startsWith("admin:bc_cancel:")) {
            String draftId = data.substring("admin:bc_cancel:".length());
            adminService.removeDraft(draftId);
            messageSender.editMessage(chatId, messageId,
                    "❌ <i>Рассылка отменена.</i>",
                    KeyboardFactory.buildAdminBackKeyboard());
        } else if (data.equals("admin:close")) {
            messageSender.editMessage(chatId, messageId,
                    "🚪 <i>Панель администратора закрыта. Чтобы открыть её снова, отправьте команду /admin.</i>",
                    null);
        }
    }

    public void showAdminMenu(long chatId, int messageId) {
        String text = "👑 <b>Панель администратора</b>\n\n" +
                "Управление ботом Коняево:\n" +
                "• Просмотр статистики и активности\n" +
                "• Ручное обновление кэша расписания\n" +
                "• Массовая рассылка сообщений";
        messageSender.editMessage(chatId, messageId, text, KeyboardFactory.buildAdminKeyboard());
    }

    public void sendAdminMenu(long chatId) {
        String text = "👑 <b>Панель администратора</b>\n\n" +
                "Управление ботом Коняево:\n" +
                "• Просмотр статистики и активности\n" +
                "• Ручное обновление кэша расписания\n" +
                "• Массовая рассылка сообщений";
        messageSender.sendNewMessage(chatId, text, KeyboardFactory.buildAdminKeyboard(), "admin menu");
    }

    public void showAdminStats(long chatId, int messageId) {
        String report = adminService.getStatsReport();
        messageSender.editMessage(chatId, messageId, report, KeyboardFactory.buildAdminBackKeyboard());
    }

    public void sendAdminStats(long chatId) {
        String report = adminService.getStatsReport();
        messageSender.sendDirectMessage(chatId, report, KeyboardFactory.buildAdminBackKeyboard());
    }

    private void handleAdminRefresh(long chatId, int messageId) {
        try {
            adminService.refreshCache();
            String text = "🔄 <b>Кэш успешно обновлён!</b>\n\n" +
                    "📅 Расписание и замены повторно загружены из Google Таблиц.";
            messageSender.editMessage(chatId, messageId, text, KeyboardFactory.buildAdminBackKeyboard());
        } catch (Exception e) {
            log.error("[ADMIN REFRESH ERROR]", e);
            messageSender.editMessage(chatId, messageId,
                    "⚠️ <b>Ошибка обновления кэша:</b> " + e.getMessage(),
                    KeyboardFactory.buildAdminBackKeyboard());
        }
    }

    public void sendAdminRefresh(long chatId) {
        try {
            adminService.refreshCache();
            String text = "🔄 <b>Кэш успешно обновлён!</b>\n\n" +
                    "📅 Расписание и замены повторно загружены из Google Таблиц.";
            messageSender.sendDirectMessage(chatId, text, KeyboardFactory.buildAdminBackKeyboard());
        } catch (Exception e) {
            log.error("[ADMIN REFRESH ERROR]", e);
            messageSender.sendDirectMessage(chatId,
                    "⚠️ <b>Ошибка обновления кэша:</b> " + e.getMessage(),
                    KeyboardFactory.buildAdminBackKeyboard());
        }
    }

    private void showBroadcastInfo(long chatId, int messageId) {
        String text = "📢 <b>Рассылка сообщений</b>\n\n" +
                "Для запуска рассылки отправьте команду в чат:\n" +
                "<code>/broadcast [текст сообщения]</code>\n\n" +
                "<i>Поддерживается форматирование Telegram HTML (b, i, code, a). Перед фактической отправкой бот покажет предпросмотр и кнопки подтверждения.</i>";
        messageSender.editMessage(chatId, messageId, text, KeyboardFactory.buildAdminBackKeyboard());
    }
}
