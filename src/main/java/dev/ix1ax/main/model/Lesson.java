package dev.ix1ax.main.model;

import dev.ix1ax.main.util.HtmlUtils;

/**
 * Represents a single lesson/class in the schedule.
 */
public class Lesson {

    public static final String WEEK_RED = "red";
    public static final String WEEK_BLUE = "blue";

    private int lessonNumber;
    private String time;
    private String subject;
    private String teacher;
    private String room;
    private String weekType; // null / "all", "red", "blue"

    public Lesson() {
    }

    public Lesson(int lessonNumber, String time, String subject, String teacher, String room) {
        this.lessonNumber = lessonNumber;
        this.time = time;
        this.subject = subject;
        this.teacher = teacher;
        this.room = room;
    }

    public Lesson(int lessonNumber, String time, String subject, String teacher, String room, String weekType) {
        this.lessonNumber = lessonNumber;
        this.time = time;
        this.subject = subject;
        this.teacher = teacher;
        this.room = room;
        this.weekType = weekType;
    }

    public int getLessonNumber() {
        return lessonNumber;
    }

    public void setLessonNumber(int lessonNumber) {
        this.lessonNumber = lessonNumber;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTeacher() {
        return teacher;
    }

    public void setTeacher(String teacher) {
        this.teacher = teacher;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getWeekType() {
        return weekType;
    }

    public void setWeekType(String weekType) {
        this.weekType = weekType;
    }

    /**
     * Format lesson for display in Telegram message.
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        if (WEEK_RED.equals(weekType)) {
            sb.append("🔴 <b>").append(lessonNumber).append(" пара</b> · <code>").append(time).append("</code> · <i>Красная неделя</i>\n");
        } else if (WEEK_BLUE.equals(weekType)) {
            sb.append("🔵 <b>").append(lessonNumber).append(" пара</b> · <code>").append(time).append("</code> · <i>Синяя неделя</i>\n");
        } else {
            sb.append("🔹 <b>").append(lessonNumber).append(" пара</b> · <code>").append(time).append("</code>\n");
        }

        sb.append("📖 <b>").append(HtmlUtils.escapeHtml(subject)).append("</b>\n");
        if (teacher != null && !teacher.isBlank()) {
            sb.append("👨‍🏫 ").append(HtmlUtils.escapeHtml(teacher)).append("\n");
        }
        if (room != null && !room.isBlank()) {
            sb.append("📍 Ауд. <b>").append(HtmlUtils.escapeHtml(room)).append("</b>");
        }
        return sb.toString();
    }

    /**
     * Format lesson for teacher view (shows group instead of teacher name).
     */
    public String formatForTeacher(String groupName) {
        StringBuilder sb = new StringBuilder();
        if (WEEK_RED.equals(weekType)) {
            sb.append("🔴 <b>").append(lessonNumber).append(" пара</b> · <code>").append(time).append("</code> · <i>Красная неделя</i>\n");
        } else if (WEEK_BLUE.equals(weekType)) {
            sb.append("🔵 <b>").append(lessonNumber).append(" пара</b> · <code>").append(time).append("</code> · <i>Синяя неделя</i>\n");
        } else {
            sb.append("🔹 <b>").append(lessonNumber).append(" пара</b> · <code>").append(time).append("</code>\n");
        }

        sb.append("📖 <b>").append(HtmlUtils.escapeHtml(subject)).append("</b>\n");
        sb.append("👥 Группа: <b>").append(HtmlUtils.escapeHtml(groupName)).append("</b>\n");
        if (room != null && !room.isBlank()) {
            sb.append("📍 Ауд. <b>").append(HtmlUtils.escapeHtml(room)).append("</b>");
        }
        return sb.toString();
    }
}
