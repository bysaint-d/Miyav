package az.miyav.domain.place;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The common subset of OpenStreetMap opening_hours: "24/7", "10:00-22:00", "Mo-Fr 09:00-18:00; Sa 10:00-14:00",
 * "Su off", several ranges per day and ranges past midnight. Anything else is treated as unknown rather
 * than guessed.
 */
public final class OpeningHours {
    private static final String[] DAYS = {"mo", "tu", "we", "th", "fr", "sa", "su"};
    private static final Pattern RANGE = Pattern.compile("^(\\d{1,2}):(\\d{2})-(\\d{1,2}):(\\d{2})$");

    private OpeningHours() {
    }

    public static Optional<Boolean> openAt(String spec, LocalDateTime local) {
        if (spec == null || spec.isBlank()) {
            return Optional.empty();
        }
        String text = spec.trim().toLowerCase(Locale.ROOT);
        if (text.equals("24/7")) {
            return Optional.of(true);
        }
        Map<DayOfWeek, List<int[]>> week = new EnumMap<>(DayOfWeek.class);
        for (String rule : text.split(";")) {
            if (!apply(rule.trim(), week)) {
                return Optional.empty();
            }
        }
        int minute = local.toLocalTime().getHour() * 60 + local.toLocalTime().getMinute();
        DayOfWeek today = local.getDayOfWeek();
        for (int[] range : week.getOrDefault(today, List.of())) {
            if (range[1] > range[0] ? minute >= range[0] && minute < range[1] : minute >= range[0]) {
                return Optional.of(true);
            }
        }
        for (int[] range : week.getOrDefault(today.minus(1), List.of())) {
            if (range[1] <= range[0] && minute < range[1]) {
                return Optional.of(true);
            }
        }
        return Optional.of(false);
    }

    private static boolean apply(String rule, Map<DayOfWeek, List<int[]>> week) {
        if (rule.isEmpty()) {
            return true;
        }
        String[] parts = rule.split("\\s+", 2);
        List<DayOfWeek> days;
        String times;
        if (Character.isDigit(parts[0].charAt(0))) {
            days = List.of(DayOfWeek.values());
            times = rule;
        } else {
            days = days(parts[0]);
            if (days == null || parts.length < 2) {
                return false;
            }
            times = parts[1].trim();
        }
        List<int[]> ranges = new ArrayList<>();
        if (!times.equals("off") && !times.equals("closed")) {
            for (String piece : times.split(",")) {
                Matcher matcher = RANGE.matcher(piece.trim());
                if (!matcher.matches()) {
                    return false;
                }
                int start = minutes(matcher.group(1), matcher.group(2));
                int end = minutes(matcher.group(3), matcher.group(4));
                if (start < 0 || end < 0) {
                    return false;
                }
                ranges.add(new int[]{start, end});
            }
        }
        for (DayOfWeek day : days) {
            week.put(day, ranges);
        }
        return true;
    }

    private static List<DayOfWeek> days(String spec) {
        List<DayOfWeek> days = new ArrayList<>();
        for (String item : spec.split(",")) {
            String[] bounds = item.split("-");
            int from = index(bounds[0]);
            int to = bounds.length > 1 ? index(bounds[1]) : from;
            if (from < 0 || to < 0 || bounds.length > 2) {
                return null;
            }
            for (int i = from; ; i = (i + 1) % 7) {
                days.add(DayOfWeek.of(i + 1));
                if (i == to) {
                    break;
                }
            }
        }
        return days;
    }

    private static int index(String day) {
        for (int i = 0; i < DAYS.length; i++) {
            if (DAYS[i].equals(day.trim())) {
                return i;
            }
        }
        return -1;
    }

    private static int minutes(String hours, String minutes) {
        int h = Integer.parseInt(hours);
        int m = Integer.parseInt(minutes);
        if (h > 24 || m > 59 || (h == 24 && m > 0)) {
            return -1;
        }
        return h * 60 + m;
    }
}
