package org.example.risklendpro.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public final class AdminDateHelper {

    public static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private AdminDateHelper() {
    }

    public static String formatDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).format(DATETIME);
    }

    public static String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDate.ofInstant(date.toInstant(), ZoneId.systemDefault()).format(DATE);
    }

    public static Date parseDateStart(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        LocalDate d = LocalDate.parse(dateStr.length() > 10 ? dateStr.substring(0, 10) : dateStr);
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public static Date parseDateEnd(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        LocalDate d = LocalDate.parse(dateStr.length() > 10 ? dateStr.substring(0, 10) : dateStr);
        return Date.from(d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
