package com.examforge.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 CSV reader: commas, double-quoted fields, "" escapes and newlines inside quotes.
 * Each returned row remembers the line number it started on (1-based) for error reports.
 */
public final class CsvParser {
    private CsvParser() {}

    public record Row(int line, List<String> cells) {}

    public static List<Row> parse(String text) {
        List<Row> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int rowStart = 1;
        String s = text.startsWith("﻿") ? text.substring(1) : text; // strip Excel BOM

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (c == '\n') line++;
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
                cells.add(cell.toString());
                cell.setLength(0);
                addRow(rows, rowStart, cells);
                cells = new ArrayList<>();
                line++;
                rowStart = line;
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !cells.isEmpty()) {
            cells.add(cell.toString());
            addRow(rows, rowStart, cells);
        }
        return rows;
    }

    private static void addRow(List<Row> rows, int line, List<String> cells) {
        boolean blank = cells.stream().allMatch(String::isBlank);
        if (!blank) rows.add(new Row(line, cells));
    }
}
