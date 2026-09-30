package dev.ix1ax.main.util;

import dev.ix1ax.main.model.Lesson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubgroupsTest {
    private final String names = "Анохина С.О., Зуева Н.Н.";
    @Test void pairsTeachersWithSlashAndMultilineRooms() {
        for (String rooms : new String[]{"9/25 ауд.", "9\n25", "9,25"}) {
            var entries = Subgroups.parse(names, rooms);
            assertEquals(2, entries.size());
            assertEquals("Анохина С.О.", entries.get(0).teacher());
            assertEquals("9", entries.get(0).room());
            assertEquals("25", entries.get(1).room());
            assertEquals("25", Subgroups.teacherRoom(names, rooms, "Зуева Н.Н."));
        }
    }
    @Test void preservesSpecialRooms() {
        assertEquals(java.util.List.of("с/з"), Subgroups.rooms("с/з"));
        assertEquals(java.util.List.of("с/л"), Subgroups.rooms("с/л"));
        assertEquals(java.util.List.of("ч/з"), Subgroups.rooms("ч/з"));
        assertEquals(java.util.List.of("202", "36-о"), Subgroups.rooms("202\n36-о"));
    }
    @Test void doesNotGuessForUnequalCounts() {
        assertTrue(Subgroups.parse(names, "9").isEmpty());
        assertEquals("9", Subgroups.teacherRoom(names, "9", "Зуева Н.Н."));
    }
    @Test void formatsBothSubgroupsAndEscapesText() {
        String text = new Lesson(2, "10:15 - 11:50", "Язык", names, "9/25").format();
        assertTrue(text.contains("1-я подгруппа"));
        assertTrue(text.contains("2-я подгруппа"));
        assertTrue(text.contains("<b>25</b>"));
    }
}
