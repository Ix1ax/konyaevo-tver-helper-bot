package dev.ix1ax.main.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Schedule for a single day.
 */
public class DaySchedule {

    private String dayName;
    private List<Lesson> lessons;

    public DaySchedule() {
        this.lessons = new ArrayList<>();
    }

    public DaySchedule(String dayName) {
        this.dayName = dayName;
        this.lessons = new ArrayList<>();
    }

    public String getDayName() {
        return dayName;
    }

    public void setDayName(String dayName) {
        this.dayName = dayName;
    }

    public List<Lesson> getLessons() {
        return lessons;
    }

    public void setLessons(List<Lesson> lessons) {
        this.lessons = new ArrayList<>(lessons);
        sortLessons();
    }

    public void addLesson(Lesson lesson) {
        this.lessons.add(lesson);
        sortLessons();
    }

    public boolean hasLessons() {
        return !lessons.isEmpty();
    }

    public void sortLessons() {
        lessons.sort(Comparator
                .comparingInt(Lesson::getLessonNumber)
                .thenComparingInt(l -> weekOrder(l.getWeekType()))
                .thenComparing(l -> l.getSubject() != null ? l.getSubject() : "")
        );
    }

    private static int weekOrder(String weekType) {
        if (Lesson.WEEK_RED.equals(weekType)) return 1;
        if (Lesson.WEEK_BLUE.equals(weekType)) return 2;
        return 0;
    }

    /**
     * Format day schedule for display.
     */
    public String format() {
        if (!hasLessons()) {
            return "<b>" + dayName + "</b>\n✨ <i>Пар нет — свободный день</i>";
        }
        sortLessons();
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(dayName).append("</b>\n\n");
        for (int i = 0; i < lessons.size(); i++) {
            sb.append(lessons.get(i).format());
            if (i < lessons.size() - 1) {
                sb.append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * Format day schedule for teacher view.
     */
    public String formatForTeacher() {
        if (!hasLessons()) {
            return "<b>" + dayName + "</b>\n✨ <i>Пар нет — свободный день</i>";
        }
        sortLessons();
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(dayName).append("</b>\n\n");
        for (int i = 0; i < lessons.size(); i++) {
            // For teacher view, the group name is stored in the teacher field
            sb.append(lessons.get(i).formatForTeacher(lessons.get(i).getTeacher()));
            if (i < lessons.size() - 1) {
                sb.append("\n\n");
            }
        }
        return sb.toString();
    }
}
