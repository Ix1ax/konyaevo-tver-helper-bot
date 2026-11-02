package dev.ix1ax.main.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Выходной сверх календаря: каникулы колледжа или перенос праздника. Отмечает администратор. */
@Entity
@Table(name = "day_off")
public class DayOff {
    @Id @Column(name = "off_on") private LocalDate day;
    protected DayOff() {}
    public DayOff(LocalDate day) { this.day = day; }
    public LocalDate getDay() { return day; }
}
