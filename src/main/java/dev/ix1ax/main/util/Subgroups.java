package dev.ix1ax.main.util;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public final class Subgroups {
    private Subgroups() {}
    public record Entry(int number, String teacher, String room) {}
    public static List<String> rooms(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.replaceAll("(?iu)ауд\\.?", "").split("[,;\\n\\r]+|(?<=\\d)\\s*/\\s*(?=\\d)"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
    public static List<Entry> parse(String teachers, String room) {
        if (teachers == null) return List.of();
        List<String> names = Arrays.stream(teachers.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<String> rooms = rooms(room);
        if (names.size() < 2 || names.size() != rooms.size()) return List.of();
        return IntStream.range(0, names.size()).mapToObj(i -> new Entry(i + 1, names.get(i), rooms.get(i))).toList();
    }
    public static String teacherRoom(String teachers, String room, String teacher) {
        return parse(teachers, room).stream().filter(e -> e.teacher().equals(teacher))
                .map(Entry::room).findFirst().orElse(room);
    }
}
