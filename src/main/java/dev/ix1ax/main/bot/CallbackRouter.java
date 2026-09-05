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

    public CallbackRouter(ScheduleService scheduleService, MessageSender messageSender) {
        this.scheduleService = scheduleService;
        this.messageSender = messageSender;
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
}
