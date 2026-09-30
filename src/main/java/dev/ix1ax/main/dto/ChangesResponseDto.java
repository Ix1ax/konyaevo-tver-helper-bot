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
public class ChangesResponseDto {
    private String date;
    private String target; // Group name or Teacher name
    @Builder.Default
    private List<ChangeItemDto> items = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeItemDto {
        private int slot;
        private String groupName;
        private String text;
        private boolean canceled;
    }
}
