package dev.ix1ax.main.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfoResponseDto {
    private String weekBadge;
    @com.fasterxml.jackson.annotation.JsonProperty("isRedWeek")
    private boolean isRedWeek;
    private boolean tomorrowRedWeek;
    private String todayName;
    private String tomorrowName;
    private String changesDate;
    private String serverTime;
}
