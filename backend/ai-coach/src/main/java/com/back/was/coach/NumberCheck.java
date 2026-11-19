package com.back.was.coach;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LLM 이 쓴 숫자가 입력에 있던 값인지 검증한다 (Python insights._close / goals.check_numbers 이식) */
@Component
public class NumberCheck {

    private static final Pattern NUM = Pattern.compile("\\d[\\d,]*\\.?\\d*");
    private static final Pattern MAN_SPLIT = Pattern.compile("(\\d+)\\s*만\\s*(\\d[\\d,]*)\\s*원");   // '105만 4,600원'
    private static final Pattern DATE = Pattern.compile("\\d{4}[-.]\\d{1,2}([-.]\\d{1,2})?|\\d{4}년");

    private final ObjectMapper om;

    public NumberCheck(ObjectMapper om) { this.om = om; }

    /** 객체 안의 모든 숫자(절댓값) 수집 */
    public Set<Double> allowed(Object... sources) {
        Set<Double> acc = new HashSet<>();
        for (Object s : sources) collect(om.valueToTree(s), acc);
        return acc;
    }

    private void collect(JsonNode n, Set<Double> acc) {
        if (n == null) return;
        if (n.isNumber()) acc.add(Math.abs(n.asDouble()));
        else if (n.isObject() || n.isArray()) n.forEach(c -> collect(c, acc));
    }

    public static boolean close(double x, Set<Double> allowed) {
        for (double a : allowed) {
            if (Math.abs(x - a) <= (Math.abs(a) >= 1000 ? 1.0 : 0.06)) return true;   // 큰 금액은 1원 오차까지만
            double m = a / 10_000;   // 만 원 단위 표기 (346,700 -> 34.7만, 35만)
            if (a >= 10_000 && (Math.abs(x - Aggregator.round(m, 1)) < 0.051
                    || (Math.abs(x - Math.rint(m)) < 0.51 && x == Math.rint(x)))) return true;
        }
        return false;
    }

    /** 날짜 제거, '105만 4,600원' → '1054600원' */
    public static String normalize(String text) {
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

    /** 문장 속 숫자 토큰 중 허용 집합에 없는 것의 설명 목록 (비어 있으면 통과) */
    public List<String> unknownNumbers(List<String> texts, Set<Double> allowed) {
        List<String> bad = new ArrayList<>();
        for (String text : texts) bad.addAll(unknownIn(text, allowed, ""));
        return bad;
    }

    List<String> unknownIn(String text, Set<Double> allowed, String prefix) {
        List<String> bad = new ArrayList<>();
        Matcher m = NUM.matcher(normalize(text));
        while (m.find()) {
            String tok = m.group();
            double x = Double.parseDouble(tok.replace(",", "").replaceAll("\\.$", ""));
            if (x <= 31 && x == Math.rint(x)) continue;   // 날짜·횟수 등 작은 정수는 허용
            if (!close(x, allowed)) {
                bad.add(prefix + "숫자 '" + tok + "' 는 입력에 없는 값: " + text.substring(0, Math.min(40, text.length())) + "…");
            }
        }
        return bad;
    }
}
