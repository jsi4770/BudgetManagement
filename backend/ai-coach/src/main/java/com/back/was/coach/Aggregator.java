package com.back.was.coach;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** 거래 → 월간 요약. 원본 Python aggregate.summarize 의 Java 이식 (결과가 동일해야 함: AggregatorGoldenTest) */
@Component
public class Aggregator {

    public static final Set<String> FIXED_CATS = Set.of("월세/관리비", "통신비", "보험", "가족지원");
    public static final Set<String> SAVING_CATS = Set.of("적금");   // 지출이 아니라 저축으로 취급

    private final DemoData data;

    public Aggregator(DemoData data) { this.data = data; }

    public Summary summarize(String persona) { return summarize(persona, null); }

    public Summary summarize(String persona, String monthArg) {
        DemoData.Persona p = data.get(persona);
        List<Tx> txs = p.txs();
        List<String> months = txs.stream().map(Tx::month).distinct().sorted().toList();
        String month = monthArg != null && !monthArg.isBlank() ? monthArg : months.get(months.size() - 1);
        List<String> covered = months.stream().filter(m -> m.compareTo(month) <= 0).toList();
        List<String> prior = covered.subList(0, Math.max(0, covered.size() - 1));

        Map<String, Map<String, Long>> catM = new LinkedHashMap<>();
        Map<String, Integer> catCnt = new HashMap<>();
        Map<String, Long> income = new HashMap<>(), saving = new HashMap<>();
        for (Tx t : txs) {
            String m = t.month();
            if (t.isIncome()) {
                income.merge(m, t.amount(), Long::sum);
            } else if (SAVING_CATS.contains(t.category())) {
                saving.merge(m, t.amount(), Long::sum);
            } else {
                catM.computeIfAbsent(t.category(), k -> new HashMap<>()).merge(m, t.amount(), Long::sum);
                if (m.equals(month)) catCnt.merge(t.category(), 1, Integer::sum);
            }
        }

        Map<String, Long> spendByMonth = new LinkedHashMap<>();
        for (String m : covered) {
            spendByMonth.put(m, catM.values().stream().mapToLong(c -> c.getOrDefault(m, 0L)).sum());
        }
        long fixed = FIXED_CATS.stream().filter(catM::containsKey).mapToLong(c -> at(catM.get(c), month)).sum();
        long spending = spendByMonth.get(month);
        long variable = spending - fixed;

        List<Summary.CategoryStat> cats = new ArrayList<>();
        for (var e : catM.entrySet()) {
            String cat = e.getKey();
            Map<String, Long> byM = e.getValue();
            if (FIXED_CATS.contains(cat) || at(byM, month) == 0) continue;
            long base = prior.isEmpty() ? 0 : (long) prior.stream().mapToLong(m -> at(byM, m)).average().orElse(0);
            long prev = prior.isEmpty() ? 0 : at(byM, prior.get(prior.size() - 1));
            long amount = at(byM, month);
            cats.add(new Summary.CategoryStat(cat, amount,
                    variable != 0 ? round(amount * 1.0 / variable * 100, 1) : 0, prev, pct(amount, prev),
                    base, pct(amount, base), slope(covered.stream().map(m -> at(byM, m)).toList()),
                    catCnt.getOrDefault(cat, 0)));
        }
        cats.sort(Comparator.comparingLong(Summary.CategoryStat::amount).reversed());

        // 월말(21일~) 집중도. 21~말일은 한 달의 약 35% 라 50% 미만은 '집중'이 아니고, 5건 미만은 노이즈
        Map<String, long[]> late = new LinkedHashMap<>();
        Map<String, long[]> merch = new LinkedHashMap<>();
        for (Tx t : txs) {
            if (!t.isExpense() || !t.month().equals(month)
                    || FIXED_CATS.contains(t.category()) || SAVING_CATS.contains(t.category())) continue;
            long[] l = late.computeIfAbsent(t.category(), k -> new long[2]);
            l[1] += t.amount();
            if (t.day() >= 21) l[0] += t.amount();
            long[] mm = merch.computeIfAbsent(t.merchant(), k -> new long[2]);
            mm[0] += t.amount();
            mm[1] += 1;
        }
        Map<String, Double> lateShare = new LinkedHashMap<>();
        late.forEach((c, v) -> {
            if (v[1] != 0 && catCnt.getOrDefault(c, 0) >= 5 && (double) v[0] / v[1] >= 0.5) {
                lateShare.put(c, round((double) v[0] / v[1] * 100, 1));
            }
        });
        List<Summary.Merchant> top = merch.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0])).limit(5)
                .map(e -> new Summary.Merchant(e.getKey(), e.getValue()[0], (int) e.getValue()[1])).toList();

        List<Summary.Anomaly> anomalies = new ArrayList<>();
        for (Summary.CategoryStat c : cats) {
            if (c.baselineAvg() != 0 && c.amount() >= c.baselineAvg() * 1.3 && c.amount() - c.baselineAvg() >= 30_000) {
                anomalies.add(new Summary.Anomaly("category_spike", c.category(), String.format(Locale.US, "%s %,d원, 이전 평균 %,d원 대비 +%s%%", c.category(), c.amount(),
                                c.baselineAvg(), pyFloat(c.vsBaselinePct())), c.amount() - c.baselineAvg()));
            }
        }
        Map<String, List<Long>> hist = new HashMap<>();
        for (Tx t : txs) {
            if (t.isExpense() && prior.contains(t.month())) hist.computeIfAbsent(t.category(), k -> new ArrayList<>()).add(t.amount());
        }
        for (Tx t : txs) {
            if (!t.isExpense() || !t.month().equals(month)
                    || FIXED_CATS.contains(t.category()) || SAVING_CATS.contains(t.category())) continue;
            List<Long> h = hist.getOrDefault(t.category(), List.of());
            if (h.size() >= 5) {
                double mu = h.stream().mapToLong(Long::longValue).average().orElse(0);
                double sd = Math.sqrt(h.stream().mapToDouble(v -> (v - mu) * (v - mu)).sum() / h.size());
                if (sd != 0 && t.amount() > mu + 2.5 * sd) {
                    anomalies.add(new Summary.Anomaly("large_tx", t.category(), String.format(Locale.US,
                            "%s %s %,d원 (평소 평균 %,d원)", t.date(), t.merchant(), t.amount(), (long) mu), t.amount()));
                }
            }
        }

        Map<String, Set<String>> subs = new HashMap<>();
        Map<String, Long> subTotal = new HashMap<>();
        for (Tx t : txs) {
            if (t.isExpense() && "구독".equals(t.category())) {
                subs.computeIfAbsent(t.month(), k -> new HashSet<>()).add(t.merchant());
                subTotal.merge(t.month(), t.amount(), Long::sum);
            }
        }
        Summary.SubscriptionInfo subscription = null;
        if (subs.containsKey(month) && !subs.get(month).isEmpty()) {
            Set<String> before = new HashSet<>();
            prior.forEach(m -> before.addAll(subs.getOrDefault(m, Set.of())));
            List<String> news = new ArrayList<>(subs.get(month));
            news.removeAll(before);
            Collections.sort(news);
            int prevCount = prior.isEmpty() ? 0 : subs.getOrDefault(prior.get(prior.size() - 1), Set.of()).size();
            subscription = new Summary.SubscriptionInfo(subs.get(month).size(), prevCount, subTotal.get(month), news);
        }

        long inc = income.getOrDefault(month, 0L);
        return new Summary(persona, p.name(), month, covered, inc, spending, saving.getOrDefault(month, 0L),
                fixed, variable, spending != 0 ? round(fixed * 1.0 / spending * 100, 1) : 0,
                inc != 0 ? round((inc - spending) * 1.0 / inc * 100, 1) : 0,
                subscription, cats, lateShare, top, anomalies, spendByMonth);
    }

    // ---- 수치 도우미 (Python round() 와 같은 half-even 반올림) ----
    static long at(Map<String, Long> m, String k) { return m == null ? 0 : m.getOrDefault(k, 0L); }

    static double round(double v, int digits) {
        return new BigDecimal(v).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
    }

    static Double pct(long a, long b) { return b != 0 ? round((a - b) * 1.0 / b * 100, 1) : null; }

    static long slope(List<Long> ys) {
        int n = ys.size();
        if (n < 2) return 0;
        double mx = (n - 1) / 2.0, my = ys.stream().mapToLong(Long::longValue).sum() / (double) n, den = 0, num = 0;
        for (int x = 0; x < n; x++) {
            den += (x - mx) * (x - mx);
            num += (x - mx) * (ys.get(x) - my);
        }
        return (long) (Math.rint(num / den / 100) * 100);
    }

    /** Python f"{x}" 처럼 정수값 실수는 "63.0" 이 아니라 소수점 그대로 (Java 기본 표기와 동일) */
    static String pyFloat(Double d) { return d == null ? "None" : String.valueOf(d); }
}
