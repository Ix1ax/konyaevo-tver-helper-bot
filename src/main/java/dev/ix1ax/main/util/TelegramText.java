package dev.ix1ax.main.util;

import java.util.*;
import java.util.regex.Pattern;

/** Делит длинный HTML, сохраняя символы, сущности и открытые теги на каждой странице. */
public final class TelegramText {
    private static final Pattern TOKENS = Pattern.compile("<[^>]+>|&(?:#[0-9]+|#x[0-9a-fA-F]+|[a-zA-Z]+);|[\\s\\S]");
    private TelegramText() {}
    public static List<String> pages(String html) {
        List<String> result = new ArrayList<>();
        List<String> open = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        var tokens = TOKENS.matcher(html);
        while (tokens.find()) {
            String token = tokens.group();
            if (page.length() + token.length() + closings(open).length() > 3500) {
                result.add(page + closings(open));
                page = new StringBuilder(String.join("", open));
            }
            if (token.length() > 3000) throw new IllegalArgumentException("Слишком длинный HTML-тег");
            page.append(token);
            if (token.startsWith("</")) {
                if (!open.isEmpty()) open.remove(open.size() - 1);
            } else if (token.startsWith("<") && !token.endsWith("/>")) {
                open.add(token);
            }
        }
        if (!page.isEmpty()) result.add(page + closings(open));
        if (result.isEmpty()) result.add("Сообщение пусто.");
        return result;
    }
    private static String closings(List<String> open) {
        StringBuilder result = new StringBuilder();
        for (int i = open.size() - 1; i >= 0; i--) {
            String name = open.get(i).substring(1).split("[ >]", 2)[0];
            result.append("</").append(name).append('>');
        }
        return result.toString();
    }
}
