package dev.ix1ax.main.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** День с сокращёнными парами по 60 минут (по приказу колледжа). Отмечает администратор. */
@Entity
@Table(name = "short_day")
public class ShortDay {
    @Id @Column(name = "short_on") private LocalDate day;
    protected ShortDay() {}
    public ShortDay(LocalDate day) { this.day = day; }
    public LocalDate getDay() { return day; }
}
