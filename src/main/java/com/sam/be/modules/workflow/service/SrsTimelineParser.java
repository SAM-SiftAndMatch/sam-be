package com.sam.be.modules.workflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SrsTimelineParser {
    private static final Pattern SECTION_EIGHT = Pattern.compile("(?im)^#{1,6}\\s*8[.)]?\\s+.*$");
    private static final Pattern NEXT_SECTION = Pattern.compile("(?im)^#{1,6}\\s*9[.)]?\\s+.*$");
    private static final Pattern WEEK =
            Pattern.compile("(?i)^tuần\\s+(\\d+)\\s*[:.\\-–—]?\\s*(.*)$");
    private static final Pattern BULLET = Pattern.compile("^[-*+]\\s+(.+)$");

    private SrsTimelineParser() {}

    public static List<ParsedTimelineTask> parse(String markdown) {
        String section =
                extractTimelineSection(markdown == null ? "" : markdown.replace("\\n", "\n"));
        List<ParsedTimelineTask> result = new ArrayList<>();
        int currentWeek = 0;
        int sortOrder = 1;
        String weekHeading = null;
        boolean weekHasTask = false;

        for (String rawLine : section.split("\\R")) {
            String line = cleanDecoration(rawLine);
            if (line.isBlank()) continue;

            Matcher weekMatcher = WEEK.matcher(line);
            if (weekMatcher.matches()) {
                if (currentWeek > 0 && !weekHasTask) {
                    result.add(
                            new ParsedTimelineTask(
                                    currentWeek,
                                    sortOrder++,
                                    fallbackTitle(weekHeading, currentWeek),
                                    null));
                }
                currentWeek = Integer.parseInt(weekMatcher.group(1));
                weekHeading = cleanText(weekMatcher.group(2));
                weekHasTask = false;
                continue;
            }

            Matcher bulletMatcher = BULLET.matcher(rawLine.trim());
            if (currentWeek > 0 && bulletMatcher.matches()) {
                String title = cleanText(bulletMatcher.group(1));
                if (!title.isBlank()) {
                    result.add(
                            new ParsedTimelineTask(
                                    currentWeek, sortOrder++, limit(title, 255), weekHeading));
                    weekHasTask = true;
                }
            }
        }

        if (currentWeek > 0 && !weekHasTask) {
            result.add(
                    new ParsedTimelineTask(
                            currentWeek, sortOrder, fallbackTitle(weekHeading, currentWeek), null));
        }
        if (!result.isEmpty()) return result;

        int week = 1;
        for (String rawLine : section.split("\\R")) {
            Matcher bulletMatcher = BULLET.matcher(rawLine.trim());
            if (bulletMatcher.matches()) {
                String title = cleanText(bulletMatcher.group(1));
                if (!title.isBlank()) {
                    result.add(
                            new ParsedTimelineTask(
                                    week++, result.size() + 1, limit(title, 255), null));
                }
            }
        }
        if (result.isEmpty()) {
            result.add(
                    new ParsedTimelineTask(
                            1, 1, "Hoàn thành các mốc và tiêu chí nghiệm thu trong SRS", null));
        }
        return result;
    }

    private static String extractTimelineSection(String markdown) {
        Matcher start = SECTION_EIGHT.matcher(markdown);
        if (!start.find()) return markdown;
        int from = start.end();
        Matcher end = NEXT_SECTION.matcher(markdown);
        if (end.find(from)) return markdown.substring(from, end.start());
        return markdown.substring(from);
    }

    private static String cleanDecoration(String line) {
        return cleanText(
                line.replaceFirst("^\\s*#{1,6}\\s*", "").replaceFirst("^\\s*[-*+]\\s*", ""));
    }

    private static String cleanText(String value) {
        if (value == null) return "";
        return value.replace("**", "").replace("__", "").trim();
    }

    private static String fallbackTitle(String heading, int week) {
        return heading == null || heading.isBlank()
                ? "Mốc công việc tuần " + week
                : limit(heading, 255);
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record ParsedTimelineTask(
            int weekNumber, int sortOrder, String title, String description) {}
}
