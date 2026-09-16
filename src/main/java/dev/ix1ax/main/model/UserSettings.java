package dev.ix1ax.main.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Stores user preferences (selected role, course, group, teacher).
 */
@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    /**
     * Last message ID sent by bot (for editing).
     */
    @Column(name = "message_id")
    private Integer messageId;

    /**
     * "student" or "teacher"
     */
    @Column(name = "role")
    private String role;

    /**
     * Selected course number (1-4) for students.
     */
    @Column(name = "course")
    private Integer course;

    /**
     * Selected group name (e.g. "1-ТМС").
     */
    @Column(name = "group_name")
    private String groupName;

    /**
     * Selected teacher name for teachers.
     */
    @Column(name = "teacher_name")
    private String teacherName;

    public UserSettings() {
    }

    public UserSettings(Long chatId) {
        this.chatId = chatId;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getCourse() {
        return course;
    }

    public void setCourse(Integer course) {
        this.course = course;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    /**
     * Whether daily change notifications are enabled for this user.
     */
    @Column(name = "notify_enabled")
    private Boolean notifyEnabled = false;

    /**
     * Time of day to send notifications, format "HH:mm" in Moscow timezone.
     */
    @Column(name = "notify_time")
    private String notifyTime;

    public Boolean getNotifyEnabled() {
        return notifyEnabled;
    }

    public void setNotifyEnabled(Boolean notifyEnabled) {
        this.notifyEnabled = notifyEnabled;
    }

    public String getNotifyTime() {
        return notifyTime;
    }

    public void setNotifyTime(String notifyTime) {
        this.notifyTime = notifyTime;
    }

    /**
     * Active days of the week for notifications: comma-separated numbers 1-7 (1=Monday, 7=Sunday).
     * Defaults to all 7 days: "1,2,3,4,5,6,7".
     */
    @Column(name = "notify_days")
    private String notifyDays = "1,2,3,4,5,6,7";

    public String getNotifyDays() {
        if (notifyDays == null) {
            return "1,2,3,4,5,6,7";
        }
        return notifyDays;
    }

    public void setNotifyDays(String notifyDays) {
        this.notifyDays = notifyDays;
    }

    public java.util.Set<Integer> getNotifyDaysSet() {
        java.util.Set<Integer> set = new java.util.TreeSet<>();
        String days = getNotifyDays();
        if (days.isBlank() || "none".equalsIgnoreCase(days.trim())) {
            return set;
        }
        for (String part : days.split(",")) {
            try {
                int d = Integer.parseInt(part.trim());
                if (d >= 1 && d <= 7) {
                    set.add(d);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return set;
    }

    public void setNotifyDaysSet(java.util.Set<Integer> set) {
        if (set == null || set.isEmpty()) {
            this.notifyDays = "none";
            return;
        }
        java.util.List<String> list = new java.util.ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            if (set.contains(d)) {
                list.add(String.valueOf(d));
            }
        }
        this.notifyDays = String.join(",", list);
    }

    public boolean isNotifyDayEnabled(int dayOfWeek) {
        return getNotifyDaysSet().contains(dayOfWeek);
    }

    public void toggleNotifyDay(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) return;
        java.util.Set<Integer> set = getNotifyDaysSet();
        if (set.contains(dayOfWeek)) {
            set.remove(dayOfWeek);
        } else {
            set.add(dayOfWeek);
        }
        setNotifyDaysSet(set);
    }

    public String getNotifyDaysSummary() {
        java.util.Set<Integer> set = getNotifyDaysSet();
        if (set.size() == 7) {
            return "Каждый день";
        }
        if (set.isEmpty()) {
            return "Не выбраны";
        }
        if (set.size() == 5 && set.contains(1) && set.contains(2) && set.contains(3) && set.contains(4) && set.contains(5)) {
            return "Будни (Пн-Пт)";
        }
        if (set.size() == 2 && set.contains(6) && set.contains(7)) {
            return "Выходные (Сб-Вс)";
        }

        String[] shortNames = {"", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        java.util.List<String> activeNames = new java.util.ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            if (set.contains(d)) {
                activeNames.add(shortNames[d]);
            }
        }
        return String.join(", ", activeNames);
    }
}
