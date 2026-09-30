package dev.ix1ax.main.dto;

import dev.ix1ax.main.util.Subgroups;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonDto {
    public java.util.List<Subgroups.Entry> getSubgroups() {
        return Subgroups.parse(teacher, room);
    }

    private int lessonNumber;
    private String time;
    private String subject;
    private String teacher;
    private String room;
    private String weekType; // null/all, "red", "blue"
    private String groupName;

    // Type: "Пара" or "Замена"
    @Builder.Default
    private String type = "Пара";

    private boolean changed;
    private String changeText;
    private boolean canceled;

    // Original scheduled lesson details (for strikethrough preview when changed/replaced)
    private String originalSubject;
    private String originalTeacher;
    private String originalRoom;
}
