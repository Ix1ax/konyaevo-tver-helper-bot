package dev.ix1ax.main.service;

import com.opencsv.CSVReader;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import dev.ix1ax.main.util.HtmlUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses schedule changes from Google Sheets.
 * The changes sheet has a single page that gets overwritten each day.
 *
 * Structure:
 * Row 0: Date + day of week (e.g. "7 сентября\n(понедельник)")
 * Row 1: "Группа", "1", "2", "3", "4", "5", "6"
 * Row 2+: Group name, lesson data for slots 1-6
 */
@Service
public class ChangesParserService {

    private static final Logger log = LoggerFactory.getLogger(ChangesParserService.class);

    private static final String CSV_URL_TEMPLATE =
            "https://docs.google.com/spreadsheets/d/%s/gviz/tq?tqx=out:csv&gid=%s";

    @Value("${changes.spreadsheet.id}")
    private String spreadsheetId;

    @Value("${changes.sheet.gid}")
    private String sheetGid;

    /**
     * The date/title of the changes.
     */
    private volatile String changesDate = "";

    /**
     * Map: groupName -> Map: lessonNumber -> change description
     */
    private volatile Map<String, Map<Integer, String>> changesByGroup = new ConcurrentHashMap<>();

    /**
     * Reusable HTTP client with timeouts.
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @PostConstruct
    public void init() {
        refreshChanges();
    }

    @Scheduled(fixedRateString = "${changes.refresh.interval}", initialDelayString = "${changes.refresh.interval}")
    public void refreshChanges() {
        log.info("[CHANGES] Refreshing changes data from Google Sheets...");
        try {
            String url = String.format(CSV_URL_TEMPLATE, spreadsheetId, sheetGid);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("[CHANGES WARNING] HTTP {} fetching changes. Keeping current changes.", response.statusCode());
                return;
            }

            List<String[]> csv;
            try (CSVReader reader = new CSVReader(new InputStreamReader(
                    new ByteArrayInputStream(response.body()), StandardCharsets.UTF_8))) {
                csv = reader.readAll();
            }

            if (csv == null || csv.size() < 2) {
                log.warn("[CHANGES WARNING] Changes CSV is empty or too small. Keeping current changes in memory.");
                return;
            }

            Map<String, Map<Integer, String>> newChanges = new LinkedHashMap<>();

            // Row 0: Date
            String date = csv.get(0)[0].trim().replace("\n", " ");
            changesDate = date;

            // Row 1: Header ("Группа", "1", "2", ...)
            // Row 2+: Data
            for (int row = 2; row < csv.size(); row++) {
                String[] line = csv.get(row);
                if (line.length < 1) continue;

                String groupName = line[0].trim();
                if (groupName.isEmpty()) continue;

                Map<Integer, String> groupChanges = new LinkedHashMap<>();

                for (int slot = 1; slot <= 6; slot++) {
                    if (slot < line.length) {
                        String change = line[slot].trim();
                        if (!change.isEmpty()) {
                            groupChanges.put(slot, change);
                        }
                    }
                }

                if (!groupChanges.isEmpty()) {
                    newChanges.put(groupName, groupChanges);
                }
            }

            // Atomic snapshot publication
            this.changesByGroup = newChanges;

            log.info("[CHANGES SUCCESS] Updated for date '{}': {} groups with changes",
                    changesDate, changesByGroup.size());
        } catch (Exception e) {
            log.warn("[CHANGES ERROR] Failed to fetch changes: {}. Retaining previous changes in memory.", e.getMessage());
        }
    }

    // ===== Public API =====

    public String getChangesDate() {
        return changesDate;
    }

    public Map<Integer, String> getChangesForGroup(String groupName) {
        return changesByGroup.getOrDefault(groupName, Collections.emptyMap());
    }

    /**
     * Get formatted changes text for a specific group.
     */
    public String getFormattedChanges(String groupName) {
        Map<Integer, String> changes = getChangesForGroup(groupName);
        if (changes.isEmpty()) {
            return "⚡️ <b>Изменения на " + HtmlUtils.escapeHtml(changesDate) + "</b>\n\n" +
                    "👥 <b>Группа: " + HtmlUtils.escapeHtml(groupName) + "</b>\n\n" +
                    "✨ <i>Изменений нет</i>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("⚡️ <b>Изменения на ").append(HtmlUtils.escapeHtml(changesDate)).append("</b>\n\n");
        sb.append("👥 <b>Группа: ").append(HtmlUtils.escapeHtml(groupName)).append("</b>\n\n");

        List<Integer> slots = new ArrayList<>(changes.keySet());
        Collections.sort(slots);

        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            String changeText = changes.get(slot);
            sb.append("🔹 <b>").append(slot).append(" пара</b>:\n");
            String[] lines = changeText.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    sb.append("   ▫️ ").append(HtmlUtils.escapeHtml(trimmed)).append("\n");
                }
            }
            if (i < slots.size() - 1) {
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * Get formatted changes for a teacher (search all groups for mentions).
     */
    public String getFormattedChangesForTeacher(String teacherName) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚡️ <b>Изменения на ").append(HtmlUtils.escapeHtml(changesDate)).append("</b>\n\n");
        sb.append("👨‍🏫 <b>").append(HtmlUtils.escapeHtml(teacherName)).append("</b>\n\n");

        List<TeacherChangeEntry> changeEntries = new ArrayList<>();

        for (Map.Entry<String, Map<Integer, String>> groupEntry : changesByGroup.entrySet()) {
            String groupName = groupEntry.getKey();
            Map<Integer, String> groupSlots = groupEntry.getValue();

            for (Map.Entry<Integer, String> slotEntry : groupSlots.entrySet()) {
                int slot = slotEntry.getKey();
                String val = slotEntry.getValue();
                if (val.contains(teacherName) || containsTeacherLastName(val, teacherName)) {
                    StringBuilder entrySb = new StringBuilder();
                    entrySb.append("👥 <b>").append(HtmlUtils.escapeHtml(groupName)).append("</b> — <b>")
                            .append(slot).append(" пара</b>:\n");
                    for (String line : val.split("\n")) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            entrySb.append("   ▫️ ").append(HtmlUtils.escapeHtml(trimmed)).append("\n");
                        }
                    }
                    changeEntries.add(new TeacherChangeEntry(slot, groupName, entrySb.toString().trim()));
                }
            }
        }

        // Strict sorting: first by lesson slot ascending (1..6), then by group name alphabetically
        changeEntries.sort(Comparator
                .comparingInt((TeacherChangeEntry e) -> e.slot)
                .thenComparing(e -> e.groupName)
        );

        if (changeEntries.isEmpty()) {
            sb.append("✨ <i>Изменений нет</i>");
        } else {
            for (int i = 0; i < changeEntries.size(); i++) {
                sb.append(changeEntries.get(i).formattedText);
                if (i < changeEntries.size() - 1) {
                    sb.append("\n\n");
                }
            }
        }

        return sb.toString().trim();
    }

    private static class TeacherChangeEntry {
        final int slot;
        final String groupName;
        final String formattedText;

        TeacherChangeEntry(int slot, String groupName, String formattedText) {
            this.slot = slot;
            this.groupName = groupName;
            this.formattedText = formattedText;
        }
    }

    private boolean containsTeacherLastName(String text, String teacherName) {
        // Extract last name from "Фамилия И.О."
        String[] parts = teacherName.split("\\s+");
        if (parts.length > 0) {
            return text.contains(parts[0]);
        }
        return false;
    }
}
