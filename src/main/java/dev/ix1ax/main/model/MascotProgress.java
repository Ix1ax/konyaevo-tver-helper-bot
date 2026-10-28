package dev.ix1ax.main.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Прогресс маскота Парыча: огонёк учебных дней, опыт, облик и напоминание. */
@Entity
@Table(name = "mascot_progress")
public class MascotProgress {
    @Id
    @Column(name = "chat_id")
    private Long chatId;

    private int streak;
    @Column(name = "best_streak")
    private int bestStreak;
    private int freezes;
    /** Последний засчитанный учебный день. */
    @Column(name = "last_day")
    private LocalDate lastDay;

    /** Понедельник недели, к которой относятся маски дней. */
    @Column(name = "week_start")
    private LocalDate weekStart;
    /** Биты дней недели (0 — понедельник): засчитанные дни и дни, закрытые заморозкой. */
    @Column(name = "week_done")
    private int weekDone;
    @Column(name = "week_frozen")
    private int weekFrozen;

    private int xp;
    private String look = "classic";

    @Column(name = "task_day")
    private LocalDate taskDay;
    /** Биты выполненных за taskDay заданий. */
    @Column(name = "tasks_done")
    private int tasksDone;

    @Column(name = "reminder_enabled")
    private boolean reminderEnabled = true;
    @Column(name = "last_reminder")
    private LocalDate lastReminder;

    /** Награды, которые ещё не показали в приложении: "streak:7", "level:3" или "donation:50:star". */
    @Column(name = "pending_reward")
    private String pendingReward;

    /** Сколько звёзд всего пожертвовал пользователь: открывает облики поддержки. Колонка добавлена в 2.2.4, у старых строк null. */
    @Column(name = "donated_stars")
    private Integer donatedStars;

    public MascotProgress() {}
    public MascotProgress(Long chatId) { this.chatId = chatId; }

    public Long getChatId() { return chatId; }
    public int getStreak() { return streak; }
    public void setStreak(int streak) { this.streak = streak; }
    public int getBestStreak() { return bestStreak; }
    public void setBestStreak(int bestStreak) { this.bestStreak = bestStreak; }
    public int getFreezes() { return freezes; }
    public void setFreezes(int freezes) { this.freezes = freezes; }
    public LocalDate getLastDay() { return lastDay; }
    public void setLastDay(LocalDate lastDay) { this.lastDay = lastDay; }
    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }
    public int getWeekDone() { return weekDone; }
    public void setWeekDone(int weekDone) { this.weekDone = weekDone; }
    public int getWeekFrozen() { return weekFrozen; }
    public void setWeekFrozen(int weekFrozen) { this.weekFrozen = weekFrozen; }
    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }
    public String getLook() { return look; }
    public void setLook(String look) { this.look = look; }
    public LocalDate getTaskDay() { return taskDay; }
    public void setTaskDay(LocalDate taskDay) { this.taskDay = taskDay; }
    public int getTasksDone() { return tasksDone; }
    public void setTasksDone(int tasksDone) { this.tasksDone = tasksDone; }
    public boolean isReminderEnabled() { return reminderEnabled; }
    public void setReminderEnabled(boolean reminderEnabled) { this.reminderEnabled = reminderEnabled; }
    public LocalDate getLastReminder() { return lastReminder; }
    public void setLastReminder(LocalDate lastReminder) { this.lastReminder = lastReminder; }
    public String getPendingReward() { return pendingReward; }
    public void setPendingReward(String pendingReward) { this.pendingReward = pendingReward; }
    public int getDonatedStars() { return donatedStars == null ? 0 : donatedStars; }
    public void setDonatedStars(int donatedStars) { this.donatedStars = donatedStars; }
}
