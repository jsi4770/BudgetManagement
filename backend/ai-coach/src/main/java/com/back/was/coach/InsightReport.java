package com.back.was.coach;

import java.util.List;

public record InsightReport(String headline, List<Insight> insights) {
    public record Evidence(String label, double value, String unit) {}   // unit: 원 % 건 개 개월

    public record Insight(String title, String category, String severity, String message,
                          List<Evidence> evidence, String action) {}
}
