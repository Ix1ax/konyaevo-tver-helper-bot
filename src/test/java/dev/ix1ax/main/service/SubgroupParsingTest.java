package dev.ix1ax.main.service;

import dev.ix1ax.main.model.DaySchedule;
import dev.ix1ax.main.util.Subgroups;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SubgroupParsingTest {
    @Test void preservesRoomsInSeparateCellAndInsideSubjectCell() throws Exception {
        for (boolean embedded : new boolean[]{false, true}) {
            try (var workbook = new XSSFWorkbook()) {
                var sheet = workbook.createSheet();
                sheet.createRow(0).createCell(3).setCellValue("1-РПО2");
                var row = sheet.createRow(1);
                row.createCell(0).setCellValue("Понедельник");
                row.createCell(1).setCellValue("10:15 - 11:50");
                row.createCell(2).setCellValue(2);
                row.createCell(3).setCellValue("Иностранный язык\nАнохина С.О., Зуева Н.Н." + (embedded ? "\n9/25 ауд." : ""));
                row.createCell(4).setCellValue(embedded ? "" : "9\n25");
                Map<String, Map<String, DaySchedule>> schedules = new LinkedHashMap<>();
                var parser = new ScheduleParserService();
                parser.parseSheetPoi(sheet, "1 курс", schedules, new HashMap<>(), new HashSet<>());
                var lessons = schedules.get("1-РПО2").get("Понедельник").getLessons();
                assertEquals(1, lessons.size());
                assertEquals(2, Subgroups.parse(lessons.get(0).getTeacher(), lessons.get(0).getRoom()).size());
                var method = ScheduleParserService.class.getDeclaredMethod("buildTeacherSchedule", Map.class);
                method.setAccessible(true);
                @SuppressWarnings("unchecked")
                var teachers = (Map<String, Map<String, DaySchedule>>) method.invoke(parser, schedules);
                assertEquals("9", teachers.get("Анохина С.О.").get("Понедельник").getLessons().get(0).getRoom());
                assertEquals("25", teachers.get("Зуева Н.Н.").get("Понедельник").getLessons().get(0).getRoom());
            }
        }
    }
}
