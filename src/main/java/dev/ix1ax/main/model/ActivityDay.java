package dev.ix1ax.main.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Пользователь открывал расписание в этот день (по Москве): одна строка на человека в день, для графика активности. */
@Entity
@Table(name = "activity_day", indexes = @Index(name = "idx_activity_day", columnList = "active_on"))
@IdClass(ActivityDay.Key.class)
public class ActivityDay {
    @Id @Column(name = "active_on") private LocalDate day;
    @Id @Column(name = "chat_id") private long chatId;
    protected ActivityDay() {}
    public ActivityDay(LocalDate day, long chatId) { this.day = day; this.chatId = chatId; }
    public LocalDate getDay() { return day; }
    public long getChatId() { return chatId; }

    public static class Key implements Serializable {
        private LocalDate day;
        private long chatId;
        public Key() {}
        public Key(LocalDate day, long chatId) { this.day = day; this.chatId = chatId; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && chatId == key.chatId && Objects.equals(day, key.day);
        }
        @Override public int hashCode() { return Objects.hash(day, chatId); }
    }
}
