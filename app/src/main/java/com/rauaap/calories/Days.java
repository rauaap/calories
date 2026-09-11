package com.rauaap.calories;

import android.content.Context;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Diary days. A day runs from the configured day start to the next one, so
 * with a 05:00 start, food logged at 03:00 belongs to the previous day.
 */
final class Days {
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("EEE d MMM");
    private static final DateTimeFormatter LABEL_YEAR = DateTimeFormatter.ofPattern("EEE d MMM yyyy");

    private Days() {
    }

    /** Today's diary day as an epoch day. */
    static long today(Context c) {
        return LocalDateTime.now().minusMinutes(Prefs.dayStart(c)).toLocalDate().toEpochDay();
    }

    /** When the next diary day begins, in epoch millis. */
    static long nextBoundaryMillis(Context c) {
        return LocalDate.ofEpochDay(today(c) + 1).atStartOfDay().plusMinutes(Prefs.dayStart(c))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static String label(Context c, long day) {
        long today = today(c);
        if (day == today) return "Today";
        if (day == today - 1) return "Yesterday";
        if (day == today + 1) return "Tomorrow";
        LocalDate date = LocalDate.ofEpochDay(day);
        return date.format(date.getYear() == LocalDate.ofEpochDay(today).getYear() ? LABEL : LABEL_YEAR);
    }
}
