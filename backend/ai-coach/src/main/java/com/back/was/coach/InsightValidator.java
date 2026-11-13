package com.back.was.coach;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/** 인사이트 구조·숫자 검증 (Python insights.validate 이식). 비어 있는 목록이면 통과 */
@Component
public class InsightValidator {

    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z]+_[A-Za-z_]+|^(amount|count)\\b");
    private final NumberCheck numbers;

    public InsightValidator(NumberCheck numbers) { this.numbers = numbers; }

    private static String g(double d) { return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.valueOf(d); }

    public List<String> validate(InsightReport report, Summary s) {
        Set<Double> allowed = numbers.allowed(s);
        List<String> problems = new ArrayList<>();
        List<InsightReport.Insight> ins = report.insights() == null ? List.of() : report.insights();

        if (ins.size() < 3 || ins.size() > 4) problems.add("인사이트는 3~4개여야 하는데 " + ins.size() + "개");
        Set<String> covered = new HashSet<>();
        ins.forEach(i -> covered.add(i.category()));
        TreeSet<String> must = new TreeSet<>();
        s.anomalies().forEach(a -> must.add(a.category()));
        s.lateMonthShare().forEach((c, v) -> { if (v >= 50) must.add(c); });
        for (String cat : must) {
            if (!covered.contains(cat)) problems.add("'" + cat + "' 카테고리의 이상 징후/월말 집중 인사이트가 빠졌음 (category 값을 '" + cat + "'로 쓸 것)");
        }

        List<Summary.Anomaly> sorted = s.anomalies().stream().sorted(Comparator.comparingLong(Summary.Anomaly::value).reversed()).toList();
        if (!sorted.isEmpty()) {
            Summary.Anomaly top = sorted.get(0);
            List<String> firstTwo = ins.stream().limit(2).map(InsightReport.Insight::category).toList();
            if (!firstTwo.contains(top.category())) {
                problems.add("가장 큰 이상 징후('" + top.category() + "')가 앞쪽(첫 두 개) 인사이트여야 함 (category를 '" + top.category() + "'로)");
            }
            Summary.CategoryStat stat = s.categories().stream().filter(c -> c.category().equals(top.category())).findFirst().orElse(null);
            if ("category_spike".equals(top.kind()) && stat != null && stat.vsBaselinePct() != null) {
                double pct = stat.vsBaselinePct();
                boolean hit = ins.stream().anyMatch(i -> i.category().equals(top.category())
                        && (i.evidence().stream().anyMatch(e -> Math.abs(e.value() - pct) < 0.06) || i.message().contains(g(pct) + "%")));
                if (!hit) problems.add("'" + top.category() + "' 인사이트에 이전 평균 대비 증가율(" + g(pct) + "%)을 evidence로 넣을 것");
            }
        }
        if (s.savingsRate() < 10) {   // 저축 여력 부족은 반드시 짚어야 하는 핵심 지표
            double rate = s.savingsRate();
            boolean hit = ins.stream().anyMatch(i -> i.evidence().stream().anyMatch(e -> Math.abs(e.value() - rate) < 0.06)
                    || i.message().contains(g(rate) + "%"));
            if (!hit) problems.add("저축률(" + g(rate) + "%)이 10% 미만인데 이를 다루는 인사이트가 없음 (evidence에 저축률 값을 넣을 것)");
        }

        Set<String> spendCats = new HashSet<>();
        s.categories().forEach(c -> spendCats.add(c.category()));
        Set<String> flagged = new HashSet<>(s.lateMonthShare().keySet());
        s.anomalies().forEach(a -> flagged.add(a.category()));
        for (int i = 0; i < ins.size(); i++) {
            InsightReport.Insight in = ins.get(i);
            if ("info".equals(in.severity()) && flagged.contains(in.category())) {
                problems.add("[" + i + "] '" + in.category() + "'은 이상 징후/월말 집중 항목이라 severity는 warn 이상이어야 함 "
                        + "(저축·목표 같은 다른 주제의 인사이트라면 category를 그 주제로 바꿀 것)");
            }
            if (("warn".equals(in.severity()) || "alert".equals(in.severity()))
                    && spendCats.contains(in.category()) && !flagged.contains(in.category())) {
                problems.add("[" + i + "] '" + in.category() + "'은 이상 징후가 아니므로 severity는 info여야 함");
            }
            for (InsightReport.Evidence ev : in.evidence()) {
                if (FIELD_NAME.matcher(ev.label()).find()) problems.add("[" + i + "] evidence 라벨 '" + ev.label() + "'에 JSON 필드명이 노출됨 — 한국어로 쓸 것");
                if (!NumberCheck.close(Math.abs(ev.value()), allowed)) {
                    problems.add("[" + i + "] evidence '" + ev.label() + "'=" + g(ev.value()) + ev.unit() + " 는 입력에 없는 값");
                }
            }
            for (String text : List.of(in.message(), in.action(), in.title())) {
                problems.addAll(numbers.unknownIn(text, allowed, "[" + i + "] 본문 "));
            }
        }
        return problems;
    }
}
