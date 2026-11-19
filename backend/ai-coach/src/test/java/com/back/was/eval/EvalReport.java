package com.back.was.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

final class EvalReport {
    private EvalReport() {}

    static void write(String file, String markdown) throws IOException {
        Path dir = Path.of(System.getProperty("report.dir", "build/perf-report"));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(file), markdown);
        System.out.println("\n" + markdown + "\n(저장: " + dir.resolve(file).toAbsolutePath() + ")");
    }

    static double pct(long[] nanos, double q) {
        if (nanos.length == 0) return Double.NaN;
        long[] s = nanos.clone();
        Arrays.sort(s);
        return s[Math.min(s.length - 1, (int) Math.ceil(q * s.length) - 1)] / 1e6;
    }
}
