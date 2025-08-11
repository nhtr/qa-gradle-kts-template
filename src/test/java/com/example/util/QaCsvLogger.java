package com.example.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Very small CSV appender for QA.
 * Writes to build/qa-report.csv
 */
public final class QaCsvLogger {
    private static final Path OUT = Paths.get("build", "qa-report.csv");
    private static final List<String> LINES = new ArrayList<>();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private QaCsvLogger() {
    }

    private static void initIfNeeded() {
        if (INITIALIZED.compareAndSet(false, true)) {
            LINES.add("ID,Input,Expected,Actual,Status,Error");
        }
    }

    public static synchronized void log(String id, Object input, Object expected, Object actual, String status, String errorMessage) {
        initIfNeeded();

        String line = String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
                esc(id),
                esc(input),
                esc(expected),
                esc(actual),
                esc(status),
                esc(errorMessage == null ? "" : errorMessage));

        LINES.add(line);
    }

    /**
     * Escape theo CSV: double-quote -> double double-quotes, loại bỏ xuống dòng
     */
    private static String esc(Object v) {
        String s = String.valueOf(v);
        s = s.replace("\"", "\"\"");         // " -> ""
        s = s.replaceAll("[\\r\\n]+", " ");  // bỏ CR/LF
        return s;
    }

    public static synchronized void save() throws IOException {
        initIfNeeded();
        Files.createDirectories(OUT.getParent());
        Files.write(OUT, LINES);
    }
}
