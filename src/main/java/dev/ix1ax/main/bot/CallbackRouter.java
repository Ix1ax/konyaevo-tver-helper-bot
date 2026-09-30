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

    private final java.util.Set<Long> preserveChangesOnTime = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<Long, Long> pendingTime = new java.util.concurrent.ConcurrentHashMap<>();
    private final dev.ix1ax.main.service.UserActivityService activity;
    private final ScheduleService scheduleService;
    private final MessageSender messageSender;
    private final dev.ix1ax.main.service.AdminService adminService;

    public CallbackRouter(ScheduleService scheduleService,
                          MessageSender messageSender,
                          dev.ix1ax.main.service.AdminService adminService) {
        this(scheduleService, messageSender, adminService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CallbackRouter(ScheduleService scheduleService, MessageSender messageSender,
                          dev.ix1ax.main.service.AdminService adminService, dev.ix1ax.main.service.UserActivityService activity) {
        this.activity = activity;
        this.scheduleService = scheduleService;
        this.messageSender = messageSender;
        this.adminService = adminService;
    }

    /**
     * Route a callback query to the appropriate handler.
     */
    public void route(CallbackQuery callback) {
        if (callback.getMessage() == null || callback.getData() == null || callback.getFrom() == null) return;
        String data = callback.getData();
        long chatId = callback.getMessage().getChatId();
        int messageId = callback.getMessage().getMessageId();

        if (callback.getFrom().getId() != chatId) return;
        UserSettings user = scheduleService.getOrCreateUser(chatId);
        if (activity != null) activity.record(chatId);
        // Старое сообщение не должно откатывать текущий профиль и настройки.
        if (user.getMessageId() != null && user.getMessageId() != messageId && !data.startsWith("admin:")) return;
        user.setMessageId(messageId);
        pendingTime.remove(chatId);
        if (!data.startsWith("notify:time:") && !data.equals("notify:custom_time")) preserveChangesOnTime.remove(chatId);
        if (data.equals("help")) {
            messageSender.editMessage(chatId, messageId, HelpText.forUser(adminService.isAdmin(chatId)), KeyboardFactory.buildScheduleKeyboard());
            return;
        }
        if (data.startsWith("page:")) { messageSender.showPage(chatId, messageId, data); return; }
        if (data.startsWith("teacherid:")) {
            String wanted = data.substring(10);
            String selected = scheduleService.getTeacherFirstLetters().stream().flatMap(letter -> scheduleService.getTeachersByLetter(letter).stream())
                    .filter(name -> java.util.UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().equals(wanted)).findFirst().orElse(null);
            if (selected == null) return;
            data = "teacher:" + selected;
        }
        if (data.equals("dashboard") || data.startsWith("view:")) {
            boolean teacher = "teacher".equals(user.getRole());
            String name = teacher ? user.getTeacherName() : user.getGroupName();
            if (name == null || name.isBlank()) { showMainMenu(chatId, messageId); return; }
            switch (data) {
                case "view:today" -> showScheduleDay(chatId, messageId, name, scheduleService.getTodayName(), teacher, false);
                case "view:tomorrow" -> showScheduleDay(chatId, messageId, name, scheduleService.getTomorrowName(), teacher, true);
                case "view:week" -> showWeekSchedule(chatId, messageId, name, teacher);
                case "view:changes" -> showChanges(chatId, messageId, name, teacher);
                default -> { if (teacher) showTeacherActions(chatId, messageId, name); else showStudentActions(chatId, messageId, name); }
            }
            return;
        }

        String sender = callback.getFrom() != null
                ? (callback.getFrom().getUserName() != null
                    ? "@" + callback.getFrom().getUserName()
                    : callback.getFrom().getFirstName())
                : "id:" + chatId;

        log.debug("[USER CLICK] ChatId: {} ({}) clicked: '{}'", chatId, sender, data);

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
            if (!scheduleService.getCourseNames().contains(courseName)) return;
            user.setCourse(Integer.parseInt(courseName.replaceAll("\\D", "")));
            scheduleService.saveUser(user);
            showGroupSelection(chatId, messageId, courseName);
        } else if (data.startsWith("group:")) {
            String groupName = data.substring("group:".length());
            if (scheduleService.getCourseNames().stream().noneMatch(course -> scheduleService.getGroupsForCourse(course).contains(groupName))) return;
            user.setRole("student");
            user.setGroupName(groupName);
            scheduleService.saveUser(user);
            showStudentActions(chatId, messageId, groupName);
        } else if (data.startsWith("s_today:")) {
            showScheduleDay(chatId, messageId, data.substring("s_today:".length()),
                    scheduleService.getTodayName(), false, false);
        } else if (data.startsWith("s_tomorrow:")) {
            showScheduleDay(chatId, messageId, data.substring("s_tomorrow:".length()),
                    scheduleService.getTomorrowName(), false, true);
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
            if (scheduleService.getTeacherFirstLetters().stream().noneMatch(letter -> scheduleService.getTeachersByLetter(letter).contains(teacherName))) return;
            user.setRole("teacher");
            user.setTeacherName(teacherName);
            scheduleService.saveUser(user);
            showTeacherActions(chatId, messageId, teacherName);
        } else if (data.startsWith("t_today:")) {
            showScheduleDay(chatId, messageId, data.substring("t_today:".length()),
                    scheduleService.getTodayName(), true, false);
        } else if (data.startsWith("t_tomorrow:")) {
            showScheduleDay(chatId, messageId, data.substring("t_tomorrow:".length()),
                    scheduleService.getTomorrowName(), true, true);
        } else if (data.startsWith("t_week:")) {
            showWeekSchedule(chatId, messageId, data.substring("t_week:".length()), true);
        } else if (data.startsWith("t_changes:")) {
            showChanges(chatId, messageId, data.substring("t_changes:".length()), true);

        // ===== Notification flow =====
        } else if (data.equals("notify:settings")) {
            showNotifySettings(chatId, messageId, user);
        } else if (data.equals("notify:tomorrow")) {
            boolean configured = "student".equals(user.getRole()) ? user.getGroupName() != null && !user.getGroupName().isBlank()
                    : "teacher".equals(user.getRole()) && user.getTeacherName() != null && !user.getTeacherName().isBlank();
            if (!configured) { showMainMenu(chatId, messageId); return; }
            user.setNotifyTomorrow(!Boolean.TRUE.equals(user.getNotifyTomorrow()));
            if (user.getNotifyTime() == null) user.setNotifyTime("18:00");
            scheduleService.saveUser(user);
            showNotifySettings(chatId, messageId, user);
        } else if (data.equals("notify:enable") || data.equals("notify:change_time")) {
            if (data.equals("notify:change_time")) preserveChangesOnTime.add(chatId);
            showTimePicker(chatId, messageId, user);
        } else if (data.equals("notify:disable")) {
            disableNotifications(chatId, messageId, user);
        } else if (data.startsWith("notify:time:")) {
            String time = data.substring("notify:time:".length());
            enableNotifications(chatId, messageId, user, time);
        } else if (data.equals("notify:custom_time")) {
            promptCustomTime(chatId, messageId, user);
        } else if (data.equals("notify:days")) {
            showNotifyDays(chatId, messageId, user);
        } else if (data.startsWith("notify:day:")) {
            try {
                int day = Integer.parseInt(data.substring("notify:day:".length()));
                user.toggleNotifyDay(day);
                scheduleService.saveUser(user);
            } catch (NumberFormatException ignored) {
            }
            showNotifyDays(chatId, messageId, user);
        } else if (data.equals("notify:days_all")) {
            user.setNotifyDaysSet(java.util.Set.of(1, 2, 3, 4, 5, 6, 7));
            scheduleService.saveUser(user);
            showNotifyDays(chatId, messageId, user);
        } else if (data.equals("notify:days_weekdays")) {
            user.setNotifyDaysSet(java.util.Set.of(1, 2, 3, 4, 5));
            scheduleService.saveUser(user);
            showNotifyDays(chatId, messageId, user);

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
        messageSender.editMessage(chatId, messageId, getMainMenuText(), KeyboardFactory.buildRoleKeyboard());
    }

    private void showCourseSelection(long chatId, int messageId) {
        String text = "📚 <b>Выберите курс:</b>";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildCourseKeyboard(scheduleService.getCourseNames()));
    }

    private void showGroupSelection(long chatId, int messageId, String courseName) {
        String text = "👥 <b>Выберите группу (" + courseName + "):</b>";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildGroupKeyboard(scheduleService.getGroupsForCourse(courseName), courseName));
    }

    private void showStudentActions(long chatId, int messageId, String groupName) {
        messageSender.editMessage(chatId, messageId, getStudentActionText(groupName),
                KeyboardFactory.buildStudentActionsKeyboard(groupName));
    }

    private void showTeacherLetters(long chatId, int messageId) {
        String text = "🔤 <b>Выберите первую букву фамилии:</b>";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildTeacherLettersKeyboard(scheduleService.getTeacherFirstLetters()));
    }

    private void showTeachersByLetter(long chatId, int messageId, String letter) {
        String text = "👨‍🏫 <b>Преподаватели на букву «" + letter + "»:</b>";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildTeacherListKeyboard(scheduleService.getTeachersByLetter(letter), letter));
    }

    private void showTeacherActions(long chatId, int messageId, String teacherName) {
        messageSender.editMessage(chatId, messageId, getTeacherActionText(teacherName),
                KeyboardFactory.buildTeacherActionsKeyboard(teacherName));
    }

    // ===== Unified schedule display (student & teacher share the same logic) =====

    /**
     * Show schedule for a single day.
     * @param name        group name (student) or teacher name (teacher)
     * @param dayName     day of week in Russian, or empty string for weekend
     * @param isTeacher   true for teacher view, false for student view
     * @param isTomorrow  true if requested for tomorrow, false if for today
     */
    private void showScheduleDay(long chatId, int messageId, String name, String dayName, boolean isTeacher, boolean isTomorrow) {
        String text;
        if (dayName.isEmpty()) {
            String icon = isTeacher ? "" : "";
            String holidayText = isTomorrow ? "Завтра выходной день!" : "Сегодня выходной день!";
            text = icon + " <b>" + name + "</b>\n\n<i>" + holidayText + "</i>";
        } else {
            text = isTeacher
                    ? scheduleService.getScheduleTextForTeacher(name, dayName)
                    : scheduleService.getScheduleTextForGroup(name, dayName);
        }

        String backCallback = isTeacher ? "back:tactions:" + name : "back:sactions:" + name;
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildScheduleKeyboard());
    }

    private void showWeekSchedule(long chatId, int messageId, String name, boolean isTeacher) {
        String text = isTeacher
                ? scheduleService.getWeekScheduleTextForTeacher(name)
                : scheduleService.getWeekScheduleTextForGroup(name);

        String backCallback = isTeacher ? "back:tactions:" + name : "back:sactions:" + name;
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildScheduleKeyboard());
    }

    private void showChanges(long chatId, int messageId, String name, boolean isTeacher) {
        String text = isTeacher
                ? scheduleService.getChangesTextForTeacher(name)
                : scheduleService.getChangesTextForGroup(name);

        String backCallback = isTeacher ? "back:tactions:" + name : "back:sactions:" + name;
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildScheduleKeyboard());
    }

    // ===== Public helpers for auto-login from KonyaevoBot =====

    /**
     * Build the main menu text (used for auto-login).
     */
    public String getMainMenuText() {
        return "🏛 <b>Коняево — Расписание</b>\n\n" +
                "Тверской колледж им. А.Н. Коняева\n" +
                "🗓 Текущая: <b>" + scheduleService.getCurrentWeekBadge() + "</b>\n\n" +
                "Выберите, кто Вы:";
    }

    public String getStudentActionText(String groupName) {
        return "🏛 <b>Коняево — Расписание</b>\n\n" +
                "👥 <b>Группа: " + dev.ix1ax.main.util.HtmlUtils.escapeHtml(groupName) + "</b>\n" +
                "🗓 Текущая: <b>" + scheduleService.getCurrentWeekBadge() + "</b>\n\n" +
                "Выберите действие:";
    }

    public String getTeacherActionText(String teacherName) {
        return "🏛 <b>Коняево — Расписание</b>\n\n" +
                "👤 <b>" + dev.ix1ax.main.util.HtmlUtils.escapeHtml(teacherName) + "</b>\n" +
                "🗓 Текущая: <b>" + scheduleService.getCurrentWeekBadge() + "</b>\n\n" +
                "Выберите действие:";
    }

    // ===== Notification screen handlers =====

    private String getBackCallback(UserSettings user) {
        if ("teacher".equals(user.getRole()) && user.getTeacherName() != null) {
            return "dashboard";
        }
        if ("student".equals(user.getRole()) && user.getGroupName() != null) {
            return "dashboard";
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
            sb.append("Статус: <b>Включены ✅</b>\n");
            sb.append("⏰ Время отправки: <b>").append(time).append(" (МСК)</b>\n");
            sb.append("📅 Дни недели: <b>").append(user.getNotifyDaysSummary()).append("</b>\n\n");
            sb.append("В выбранные дни в указанное время бот отправит\n");
            sb.append("свежие замены для ");
            if ("teacher".equals(user.getRole())) {
                sb.append("Вас.");
            } else {
                sb.append("Вашей группы.");
            }
        } else {
            sb.append("Статус: <b>Выключены 🔕</b>\n");
            sb.append("📅 Дни недели: <b>").append(user.getNotifyDaysSummary()).append("</b>\n\n");
            sb.append("Включите, чтобы получать\n");
            sb.append("свежие замены для ");
            if ("teacher".equals(user.getRole())) {
                sb.append("Вас.");
            } else {
                sb.append("Вашей группы.");
            }
        }

        sb.append("\n\nРасписание на завтра: <b>").append(Boolean.TRUE.equals(user.getNotifyTomorrow()) ? "Включено" : "Выключено").append("</b>");
        if (Boolean.TRUE.equals(user.getNotifyTomorrow())) sb.append("\n").append(user.getNotifyTime()).append(" (МСК) · ").append(user.getNotifyDaysSummary());
        messageSender.editMessage(chatId, messageId, sb.toString(),
                KeyboardFactory.buildNotifySettingsKeyboard(enabled, Boolean.TRUE.equals(user.getNotifyTomorrow()), backCallback));
    }

    private void showNotifyDays(long chatId, int messageId, UserSettings user) {
        String text = "📅 <b>Дни отправки уведомлений</b>\n\n" +
                "Текущие дни: <b>" + user.getNotifyDaysSummary() + "</b>\n\n" +
                "Нажмите на день недели, чтобы изменить его состояние:";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildNotifyDaysKeyboard(user.getNotifyDaysSet(), "notify:settings"));
    }

    private void showTimePicker(long chatId, int messageId, UserSettings user) {
        String text = "⏰ <b>Выберите время уведомлений (МСК):</b>\n\n" +
                "Нажмите на готовое время или введите своё.";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildTimePickerKeyboard("notify:settings"));
    }

    private void enableNotifications(long chatId, int messageId, UserSettings user, String time) {
        if (!time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) return;
        if (!("student".equals(user.getRole()) && user.getGroupName() != null) && !("teacher".equals(user.getRole()) && user.getTeacherName() != null)) return;
        if (!preserveChangesOnTime.remove(chatId)) user.setNotifyEnabled(true);
        user.setNotifyTime(time);
        scheduleService.saveUser(user);

        showNotifySettings(chatId, messageId, user);
    }

    private void disableNotifications(long chatId, int messageId, UserSettings user) {
        user.setNotifyEnabled(false);
        scheduleService.saveUser(user);

        String text = "🔕 <b>Уведомления выключены</b>\n\n" +
                "Вы больше не будете получать ежедневные замены.\n" +
                "Включить обратно можно в любой момент.";
        String backCallback = getBackCallback(user);
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildNotifySettingsKeyboard(false, Boolean.TRUE.equals(user.getNotifyTomorrow()), backCallback));
    }

    private void promptCustomTime(long chatId, int messageId, UserSettings user) {
        pendingTime.put(chatId, System.currentTimeMillis());
        String text = "✏️ <b>Введите время в формате ЧЧ:ММ</b>\n\n" +
                "Например: <code>07:30</code> или <code>17:53</code>\n\n" +
                "Отправьте время сообщением в чат.";
        messageSender.editMessage(chatId, messageId, text,
                KeyboardFactory.buildBackKeyboard("◀️ Назад", "notify:settings"));
    }

    /**
     * Called from KonyaevoBot when user types a time like "07:30".
     * Returns true if time was valid and saved, false otherwise.
     */
    public void recordActivity(long chatId) { if (activity != null) activity.record(chatId); }

    public void cancelInput(long chatId) { pendingTime.remove(chatId); preserveChangesOnTime.remove(chatId); }

    private boolean invalidTime(long chatId) {
        UserSettings user = scheduleService.getOrCreateUser(chatId);
        String text = "Введите время от <b>00:00</b> до <b>23:59</b> по Москве. Например: <code>07:30</code>.";
        if (user.getMessageId() != null) messageSender.editMessage(chatId, user.getMessageId(), text,
                KeyboardFactory.buildBackKeyboard("Назад", "notify:settings"));
        return true;
    }

    public boolean handleCustomTimeInput(long chatId, String text) {
        Long started = pendingTime.get(chatId);
        if (started == null || System.currentTimeMillis() - started > 300000) { pendingTime.remove(chatId); return false; }
        String trimmed = text.trim();

        // Validate HH:MM format
        if (!trimmed.matches("^\\d{1,2}:\\d{2}$")) return invalidTime(chatId);

        String[] parts = trimmed.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return invalidTime(chatId);

        // Normalize to HH:mm
        String normalizedTime = String.format("%02d:%02d", hour, minute);

        UserSettings user = scheduleService.getOrCreateUser(chatId);
        if (user.getRole() == null || (user.getGroupName() == null && user.getTeacherName() == null)) {
            return false; // User hasn't completed setup
        }

        if (!preserveChangesOnTime.remove(chatId)) user.setNotifyEnabled(true);
        user.setNotifyTime(normalizedTime);
        pendingTime.remove(chatId);
        scheduleService.saveUser(user);

        String resultText = "<b>Время уведомлений сохранено</b>\n\n" +
                "В выбранные дни в <b>" + normalizedTime + " (МСК)</b> Вы будете\n" +
                "получать свежие замены пар.";
        String backCallback = getBackCallback(user);

        messageSender.sendNewMessage(chatId, resultText,
                KeyboardFactory.buildNotifySettingsKeyboard(Boolean.TRUE.equals(user.getNotifyEnabled()), Boolean.TRUE.equals(user.getNotifyTomorrow()), backCallback),
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
            var draft = adminService.getDraft(draftId);
            if (draft == null || draft.adminChatId() != chatId) {
                messageSender.editMessage(chatId, messageId, "Предпросмотр устарел. Создайте новую рассылку через /broadcast.", KeyboardFactory.buildAdminBackKeyboard());
                return;
            }
            adminService.startBroadcast(draftId, messageId);
        } else if (data.startsWith("admin:bc_cancel:")) {
            String draftId = data.substring("admin:bc_cancel:".length());
            var draft = adminService.getDraft(draftId);
            if (draft == null || draft.adminChatId() != chatId) return;
            adminService.removeDraft(draftId);
            messageSender.editMessage(chatId, messageId,
                    "<i>Рассылка отменена.</i>",
                    KeyboardFactory.buildAdminBackKeyboard());
        } else if (data.equals("admin:close")) {
            messageSender.editMessage(chatId, messageId,
                    "<i>Панель администратора закрыта. Чтобы открыть её снова, отправьте команду /admin.</i>",
                    null);
        }
    }

    public void showAdminMenu(long chatId, int messageId) {
        String text = "<b>Панель администратора</b>\n\n" +
                "Управление ботом Коняево:\n" +
                "• Просмотр статистики и активности\n" +
                "• Ручное обновление кэша расписания\n" +
                "• Массовая рассылка сообщений";
        messageSender.editMessage(chatId, messageId, text, KeyboardFactory.buildAdminKeyboard());
    }

    public void sendAdminMenu(long chatId) {
        String text = "<b>Панель администратора</b>\n\n" +
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
            String text = "<b>Кэш успешно обновлён!</b>\n\n" +
                    "Расписание и замены повторно загружены из Google Таблиц.";
            messageSender.editMessage(chatId, messageId, text, KeyboardFactory.buildAdminBackKeyboard());
        } catch (Exception e) {
            log.error("[ADMIN REFRESH ERROR]", e);
            messageSender.editMessage(chatId, messageId,
                    "<b>Ошибка обновления кэша:</b> " + e.getMessage(),
                    KeyboardFactory.buildAdminBackKeyboard());
        }
    }

    public void sendAdminRefresh(long chatId) {
        try {
            adminService.refreshCache();
            String text = "<b>Кэш успешно обновлён!</b>\n\n" +
                    "Расписание и замены повторно загружены из Google Таблиц.";
            messageSender.sendDirectMessage(chatId, text, KeyboardFactory.buildAdminBackKeyboard());
        } catch (Exception e) {
            log.error("[ADMIN REFRESH ERROR]", e);
            messageSender.sendDirectMessage(chatId,
                    "<b>Ошибка обновления кэша:</b> " + e.getMessage(),
                    KeyboardFactory.buildAdminBackKeyboard());
        }
    }

    private void showBroadcastInfo(long chatId, int messageId) {
        String text = "<b>Рассылка сообщений</b>\n\n" +
                "Для текста: /broadcast текст. Для видео, фото или файла: /broadcast, затем прикрепите медиа с подписью. Можно также ответить командой /broadcast на готовое сообщение.\n" +
                "<code>/broadcast [текст сообщения]</code>\n\n" +
                "<i>Поддерживается форматирование Telegram HTML (b, i, code, a). Перед фактической отправкой бот покажет предпросмотр и кнопки подтверждения.</i>";
        messageSender.editMessage(chatId, messageId, text, KeyboardFactory.buildAdminBackKeyboard());
    }
}
