package com.back.was.domain.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 이 쓴 숫자가 입력에 있던 값인지 검증한다 — <b>숫자는 코드가 계산하고 LLM 은 해석만 한다</b>는 원칙의
 * 구현이다. 넛지 문구·코칭 답변·성장 리포트 피드백에 서버가 준 적 없는 금액이 끼어들면(환각) 그 결과를
 * 쓰지 않고 템플릿으로 대체한다.
 *
 * <p>느슨하게 보는 것: 31 이하 정수(날짜·횟수·"3번"), 정수로 쓴 반올림 값("68.33%" → "68%"),
 * 만 원 단위 표기("346,700원" → "34.7만"·"35만"), 날짜("2026-11-10").
 */
public final class NumberGuard {

    private static final Pattern NUM = Pattern.compile("\\d[\\d,]*\\.?\\d*");
    private static final Pattern MAN_SPLIT = Pattern.compile("(\\d+)\\s*만\\s*(\\d[\\d,]*)\\s*원");   // '105만 4,600원'
    private static final Pattern DATE = Pattern.compile("\\d{4}[-.]\\d{1,2}([-.]\\d{1,2})?|\\d{4}년");

    private NumberGuard() {
    }

    /** 허용 집합에 값들을 담는다(절댓값). {@code null} 은 건너뛴다. */
    public static Set<Double> allowed(Number... values) {
        Set<Double> acc = new HashSet<>();
        for (Number v : values) {
            if (v != null) {
                acc.add(Math.abs(v.doubleValue()));
            }
        }
        return acc;
    }

    /** 텍스트 속 숫자도 허용 집합에 더한다 (대화에 이미 나온 값은 되풀이해도 된다). */
    public static void addNumbersIn(Collection<String> texts, Set<Double> allowed) {
        for (String text : texts) {
            Matcher m = NUM.matcher(normalize(text));
            while (m.find()) {
                allowed.add(parse(m.group()));
            }
        }
    }

    /** @return 허용 집합에 없는 숫자 토큰들. 비어 있으면 통과 */
    public static List<String> unknownNumbers(String text, Set<Double> allowed) {
        List<String> bad = new ArrayList<>();
        Matcher m = NUM.matcher(normalize(text));
        while (m.find()) {
            double x = parse(m.group());
            if (x <= 31 && x == Math.rint(x)) {
                continue;   // 날짜·횟수 등 작은 정수
            }
            if (!close(x, allowed)) {
                bad.add(m.group());
            }
        }
        return bad;
    }

    static boolean close(double x, Set<Double> allowed) {
        for (double a : allowed) {
            if (a >= 1000) {
                if (Math.abs(x - a) <= 1.0) {
                    return true;
                }
            } else if (Math.abs(x - a) <= (x == Math.rint(x) ? 0.5 : 0.06)) {
                return true;
            }
            double man = a / 10_000;   // 만 원 단위 표기 (346,700 → 34.7만, 35만)
            if (a >= 10_000 && (Math.abs(x - Math.round(man * 10) / 10.0) < 0.051
                    || (x == Math.rint(x) && Math.abs(x - Math.rint(man)) < 0.51))) {
                return true;
            }
        }
        return false;
    }

    /** 날짜를 지우고 '105만 4,600원' 을 '1054600원' 으로 합친다. */
    static String normalize(String text) {
        String t = DATE.matcher(text).replaceAll(" ");
        Matcher m = MAN_SPLIT.matcher(t);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            long v = Long.parseLong(m.group(1)) * 10_000 + Long.parseLong(m.group(2).replace(",", ""));
            m.appendReplacement(sb, Matcher.quoteReplacement(v + "원"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static double parse(String token) {
        return Double.parseDouble(token.replace(",", "").replaceAll("\\.$", ""));
    }
}
