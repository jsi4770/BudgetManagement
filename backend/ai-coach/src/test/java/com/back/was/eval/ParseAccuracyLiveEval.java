package com.back.was.eval;

import com.back.was.common.AiParseFailedException;
import com.back.was.common.AiUnavailableException;
import com.back.was.transaction.parse.ParseResult;
import com.back.was.transaction.parse.TransactionParseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 한 줄 입력 파싱 정확도(금액·카테고리·날짜) + 지연. 작성일은 2026-11-10(화)로 고정 */
@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class ParseAccuracyLiveEval extends LiveEvalBase {

    record Case(String text, Long amount, String category, String date, boolean notTx) {
        static Case of(String t, long a, String c) { return new Case(t, a, c, null, false); }
        static Case dated(String t, long a, String c, String d) { return new Case(t, a, c, d, false); }
    }

    static final List<Case> CASES = List.of(
            Case.of("배민 3.4", 34_000, "배달"), Case.of("스벅 4500", 4_500, "카페"),
            Case.of("점심 김치찌개 9000", 9_000, "식비"), Case.of("택시 1만2천", 12_000, "교통"),
            Case.of("뱌민 만삼처넌", 13_000, "배달"), Case.of("월세 55만", 550_000, "월세"),
            Case.of("지하철 1500원", 1_500, "교통"), Case.of("메가커피 아아 2천원", 2_000, "카페"),
            Case.of("쿠팡이츠 치킨 2만 3천", 23_000, "배달"), Case.of("편의점 삼각김밥 1,800", 1_800, "식비"),
            Case.of("월급 250만원", 2_500_000, "급여"), Case.of("넷플릭스 17000", 17_000, "기타"),
            Case.of("택시비 만오천", 15_000, "교통"), Case.of("올리브영 3만5천", 35_000, "기타"),
            Case.of("치킨 시켰다 19000", 19_000, "배달"), Case.of("아아 4.1", 4_100, "카페"),
            Case.dated("저번주 수요일 요기요 2.1", 21_000, "배달", "2026-11-04"),
            Case.dated("어제 점심 8천원", 8_000, "식비", "2026-11-09"),
            Case.dated("그제 버스 1400", 1_400, "교통", "2026-11-08"),
            new Case("그냥 오늘 좀 피곤하다", null, null, null, true));

    @Autowired TransactionParseService service;

    @Test
    void measure() throws Exception {
        int reps = n();
        int total = 0, amountOk = 0, catOk = 0, dateOk = 0, dateTotal = 0, notTxOk = 0, notTxTotal = 0, unavailable = 0;
        List<Long> lat = new ArrayList<>();
        List<String> misses = new ArrayList<>();
        for (int r = 0; r < reps; r++) {
            for (Case c : CASES) {
                long t0 = System.nanoTime();
                try {
                    ParseResult res = service.parse(c.text(), LocalDate.of(2026, 11, 10));
                    lat.add(System.nanoTime() - t0);
                    if (c.notTx()) { notTxTotal++; misses.add("`" + c.text() + "` → 거래가 아닌데 파싱됨"); continue; }
                    total++;
                    boolean a = c.amount().equals(res.amount()), k = c.category().equals(res.category().name());
                    if (a) amountOk++;
                    if (k) catOk++;
                    if (c.date() != null) { dateTotal++; if (c.date().equals(res.transactionDate())) dateOk++; else misses.add("`" + c.text() + "` 날짜 " + res.transactionDate()); }
                    if (!a || !k) misses.add("`" + c.text() + "` → " + res.amount() + "원 / " + res.category().name() + " (기대 " + c.amount() + " / " + c.category() + ")");
                } catch (AiParseFailedException e) {
                    lat.add(System.nanoTime() - t0);
                    if (c.notTx()) { notTxTotal++; notTxOk++; } else { total++; misses.add("`" + c.text() + "` → 파싱 거부(422)"); }
                } catch (AiUnavailableException e) {
                    unavailable++;
                }
            }
        }
        long[] nanos = lat.stream().mapToLong(Long::longValue).toArray();
        String md = "# 한 줄 입력 파싱 정확도 (실제 Gemini)\n\n"
                + String.format("- 금액 정확도: **%d/%d (%.0f%%)**\n- 카테고리 정확도: **%d/%d (%.0f%%)**\n- 상대 날짜 정확도: %d/%d\n- 거래 아님 거부: %d/%d\n- AI 호출 실패(한도·장애, 정확도에서 제외): %d\n- 지연: p50 %.0fms, p95 %.0fms\n\n",
                amountOk, total, pctOf(amountOk, total), catOk, total, pctOf(catOk, total), dateOk, dateTotal, notTxOk, notTxTotal,
                unavailable, EvalReport.pct(nanos, 0.5), EvalReport.pct(nanos, 0.95))
                + (misses.isEmpty() ? "틀린 케이스 없음\n" : "## 틀린 케이스\n" + misses.stream().map(s -> "- " + s).reduce("", (x, y) -> x + y + "\n"));
        EvalReport.write("parse-accuracy.md", md);
    }

    static double pctOf(int a, int b) { return b == 0 ? 0 : 100.0 * a / b; }
}
