package dev.ix1ax.main.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Factory for building Telegram inline keyboards.
 * Pure UI layer — no business logic.
 */
public final class KeyboardFactory {

    private static volatile String miniAppUrl = null;

    public static void setMiniAppUrl(String url) {
        miniAppUrl = (url != null && !url.isBlank()) ? url.trim() : null;
    }

    public static String getMiniAppUrl() {
        return miniAppUrl;
    }

    private KeyboardFactory() {
    }

    // ===== Role selection =====

    public static InlineKeyboardMarkup buildRoleKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("🎓 Я студент", "role:student"),
                button("👤 Я преподаватель", "role:teacher")
        ));
        return new InlineKeyboardMarkup(rows);
    }

    // ===== Student keyboards =====

    public static InlineKeyboardMarkup buildCourseKeyboard(List<String> courses) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < courses.size(); i += 2) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button(courses.get(i), "course:" + courses.get(i)));
            if (i + 1 < courses.size()) {
                row.add(button(courses.get(i + 1), "course:" + courses.get(i + 1)));
            }
            rows.add(row);
        }
        rows.add(List.of(button("Выйти / Сменить роль", "logout")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup buildGroupKeyboard(List<String> groups, String courseName) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < groups.size(); i += 2) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button(groups.get(i), "group:" + groups.get(i)));
            if (i + 1 < groups.size()) {
                row.add(button(groups.get(i + 1), "group:" + groups.get(i + 1)));
            }
            rows.add(row);
        }
        rows.add(List.of(button("◀ Назад к курсам", "back:courses")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup buildStudentActionsKeyboard(String groupName) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (miniAppUrl != null && !miniAppUrl.isBlank()) {
            rows.add(List.of(webAppButton("📱 Открыть приложение", miniAppUrl)));
        }
        rows.add(List.of(
                button("📅 Сегодня", "view:today"),
                button("⏭ Завтра", "view:tomorrow")
        ));
        rows.add(List.of(
                button("🗓 Неделя", "view:week"),
                button("🔄 Замены", "view:changes")
        ));
        rows.add(List.of(button("🔔 Уведомления", "notify:settings")));
        rows.add(List.of(button("❔ Помощь", "help")));
        rows.add(List.of(button("👥 Сменить группу", "role:student")));
        rows.add(List.of(button("🔄 Сменить роль", "logout")));
        return new InlineKeyboardMarkup(rows);
    }

    // ===== Teacher keyboards =====

    public static InlineKeyboardMarkup buildTeacherLettersKeyboard(List<String> letters) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (String letter : letters) {
            row.add(button(letter, "tletter:" + letter));
            if (row.size() == 4) {
                rows.add(row);
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        rows.add(List.of(button("Выйти / Сменить роль", "logout")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup buildTeacherListKeyboard(List<String> teachers, String letter) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String teacher : teachers) {
            rows.add(List.of(button(teacher, "teacherid:" + java.util.UUID.nameUUIDFromBytes(teacher.getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
        }
        rows.add(List.of(button("◀ Назад к буквам", "back:tletters")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup buildTeacherActionsKeyboard(String teacherName) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (miniAppUrl != null && !miniAppUrl.isBlank()) {
            rows.add(List.of(webAppButton("📱 Открыть приложение", miniAppUrl)));
        }
        rows.add(List.of(
                button("📅 Сегодня", "view:today"),
                button("⏭ Завтра", "view:tomorrow")
        ));
        rows.add(List.of(
                button("🗓 Неделя", "view:week"),
                button("🔄 Замены", "view:changes")
        ));
        rows.add(List.of(button("🔔 Уведомления", "notify:settings")));
        rows.add(List.of(button("❔ Помощь", "help")));
        rows.add(List.of(button("👤 Сменить преподавателя", "role:teacher")));
        rows.add(List.of(button("🔄 Сменить роль", "logout")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup buildScheduleKeyboard() {
        return new InlineKeyboardMarkup(List.of(
                List.of(button("📅 Сегодня", "view:today"), button("⏭ Завтра", "view:tomorrow")),
                List.of(button("🗓 Неделя", "view:week"), button("🔄 Замены", "view:changes")),
                List.of(button("‹ Меню", "dashboard"))));
    }

    // ===== Admin keyboards =====

    public static InlineKeyboardMarkup buildAdminKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("📊 Статистика", "admin:stats"),
                button("🔄 Обновить данные", "admin:refresh")
        ));
        rows.add(List.of(
                button("📣 Рассылка", "admin:broadcast_info")
        ));
        rows.add(List.of(button("❔ Помощь", "help")));
        rows.add(List.of(
                button("Закрыть", "admin:close")
        ));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup buildAdminBackKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button("◀ Назад в админку", "admin:menu")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup buildBroadcastConfirmKeyboard(String draftId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("📣 Отправить всем", "admin:bc_send:" + draftId),
                button("✖ Отменить", "admin:bc_cancel:" + draftId)
        ));
        return new InlineKeyboardMarkup(rows);
    }

    // ===== Notification keyboards =====

    /**
     * Notification settings screen with current status.
     */
    public static InlineKeyboardMarkup buildNotifySettingsKeyboard(boolean enabled, String backCallback) {
        return buildNotifySettingsKeyboard(enabled, false, backCallback);
    }

    public static InlineKeyboardMarkup buildNotifySettingsKeyboard(boolean enabled, boolean tomorrow, String backCallback) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button("🔄 Замены: " + (enabled ? "вкл" : "выкл"), enabled ? "notify:disable" : "notify:enable")));
        if (enabled || tomorrow) rows.add(List.of(button("🕒 Изменить время", "notify:change_time")));
        rows.add(List.of(button("🗓 Дни недели", "notify:days")));
        rows.add(List.of(button("⏭ Завтрашние пары: " + (tomorrow ? "вкл" : "выкл"), "notify:tomorrow")));
        rows.add(List.of(button("◀ Назад", backCallback)));
        return new InlineKeyboardMarkup(rows);
    }

    /**
     * Day picker for notifications with toggles and presets.
     */
    public static InlineKeyboardMarkup buildNotifyDaysKeyboard(Set<Integer> enabledDays, String backCallback) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        String[] days = {"", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

        // Row 1: Mon, Tue, Wed, Thu
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        for (int d = 1; d <= 4; d++) {
            boolean active = enabledDays != null && enabledDays.contains(d);
            String icon = active ? "✓ " : "· ";
            row1.add(button(icon + days[d], "notify:day:" + d));
        }
        rows.add(row1);

        // Row 2: Fri, Sat, Sun
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        for (int d = 5; d <= 7; d++) {
            boolean active = enabledDays != null && enabledDays.contains(d);
            String icon = active ? "✓ " : "· ";
            row2.add(button(icon + days[d], "notify:day:" + d));
        }
        rows.add(row2);

        // Row 3: Presets
        rows.add(List.of(
                button("Включить все", "notify:days_all"),
                button("Только будни", "notify:days_weekdays")
        ));

        // Row 4: Back
        rows.add(List.of(button("◀ Назад к уведомлениям", backCallback)));

        return new InlineKeyboardMarkup(rows);
    }

    /**
     * Time picker with preset times + custom input option.
     */
    public static InlineKeyboardMarkup buildTimePickerKeyboard(String backCallback) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("06:00", "notify:time:06:00"),
                button("07:00", "notify:time:07:00"),
                button("07:30", "notify:time:07:30")
        ));
        rows.add(List.of(
                button("08:00", "notify:time:08:00"),
                button("08:30", "notify:time:08:30"),
                button("12:00", "notify:time:12:00")
        ));
        rows.add(List.of(
                button("18:00", "notify:time:18:00"),
                button("20:00", "notify:time:20:00"),
                button("21:00", "notify:time:21:00")
        ));
        rows.add(List.of(button("Ввести своё время", "notify:custom_time")));
        rows.add(List.of(button("◀ Назад", backCallback)));
        return new InlineKeyboardMarkup(rows);
    }

    // ===== Common =====

    public static InlineKeyboardMarkup buildBackKeyboard(String text, String callbackData) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button(text, callbackData)));
        return new InlineKeyboardMarkup(rows);
    }

    /**
     * Create an inline keyboard button.
     */
    public static InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    /**
     * Create an inline keyboard button that opens a Telegram Mini App.
     */
    public static InlineKeyboardButton webAppButton(String text, String url) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setWebApp(new org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo(url));
        return btn;
    }
}
