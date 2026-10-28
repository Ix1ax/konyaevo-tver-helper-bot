package dev.ix1ax.main.util;

/** Значения из .env: spring-dotenv оставляет кавычки внутри строки, а start.sh их снимает. */
public final class EnvValue {
    private EnvValue() {}

    public static String clean(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && (trimmed.startsWith("'") && trimmed.endsWith("'") || trimmed.startsWith("\"") && trimmed.endsWith("\"")))
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        return trimmed;
    }
}
