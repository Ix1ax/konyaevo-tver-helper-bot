package dev.ix1ax.main.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for building Telegram inline keyboards.
 * Pure UI layer — no business logic.
 */
public final class KeyboardFactory {

    private KeyboardFactory() {
    }

    // ===== Role selection =====

    public static InlineKeyboardMarkup buildRoleKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("🎓 Я студент", "role:student"),
                button("💼 Я преподаватель", "role:teacher")
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
        rows.add(List.of(button("🚪 Выйти / Сменить роль", "logout")));
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
        rows.add(List.of(button("‹ Назад к курсам", "back:courses")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup buildStudentActionsKeyboard(String groupName) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("📅 Сегодня", "s_today:" + groupName),
                button("📆 Завтра", "s_tomorrow:" + groupName)
        ));
        rows.add(List.of(
                button("🗓 Вся неделя", "s_week:" + groupName),
                button("⚡️ Изменения", "s_changes:" + groupName)
        ));
        rows.add(List.of(button("🚪 Сменить группу", "logout")));
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
        rows.add(List.of(button("🚪 Выйти / Сменить роль", "logout")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup buildTeacherListKeyboard(List<String> teachers, String letter) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String teacher : teachers) {
            rows.add(List.of(button(teacher, "teacher:" + teacher)));
        }
        rows.add(List.of(button("‹ Назад к буквам", "back:tletters")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup buildTeacherActionsKeyboard(String teacherName) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("📅 Сегодня", "t_today:" + teacherName),
                button("📆 Завтра", "t_tomorrow:" + teacherName)
        ));
        rows.add(List.of(
                button("🗓 Вся неделя", "t_week:" + teacherName),
                button("⚡️ Изменения", "t_changes:" + teacherName)
        ));
        rows.add(List.of(button("🚪 Сменить преподавателя", "logout")));
        return new InlineKeyboardMarkup(rows);
    }

    // ===== Admin keyboards =====

    public static InlineKeyboardMarkup buildAdminKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("📊 Статистика", "admin:stats"),
                button("🔄 Обновить кэш", "admin:refresh")
        ));
        rows.add(List.of(
                button("📢 Сделать рассылку", "admin:broadcast_info")
        ));
        rows.add(List.of(
                button("🚪 Закрыть", "admin:close")
        ));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup buildAdminBackKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button("‹ Назад в админку", "admin:menu")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup buildBroadcastConfirmKeyboard(String draftId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("✅ Отправить всем", "admin:bc_send:" + draftId),
                button("❌ Отменить", "admin:bc_cancel:" + draftId)
        ));
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
}
