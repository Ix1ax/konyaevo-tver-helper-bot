package dev.ix1ax.main.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayScheduleDto {
    private String dayName;
    @Builder.Default
    private List<LessonDto> lessons = new ArrayList<>();
    private boolean hasLessons;
}
