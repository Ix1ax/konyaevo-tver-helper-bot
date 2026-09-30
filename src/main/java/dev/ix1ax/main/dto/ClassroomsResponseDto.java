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
public class ClassroomsResponseDto {
    private String day;
    private int slot;
    private String weekType;
    @Builder.Default
    private List<String> freeRooms = new ArrayList<>();
    @Builder.Default
    private List<OccupiedRoomDto> occupiedRooms = new ArrayList<>();
    @Builder.Default
    private List<String> allRooms = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OccupiedRoomDto {
        private String room;
        private String subject;
        private String teacher;
        private String groupName;
        private String time;
    }
}
