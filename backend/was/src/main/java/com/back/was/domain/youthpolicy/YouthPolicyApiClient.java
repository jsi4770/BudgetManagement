package com.back.was.domain.youthpolicy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 온통청년 청년정책 API({@code /go/ythip/getPlcy}) 클라이언트.
 *
 * <pre>
 * GET {baseUrl}?apiKeyNm=…&amp;pageNum=1&amp;pageSize=100&amp;rtnType=json&amp;zipCd=44130
 * </pre>
 *
 * <p>{@code zipCd} 는 법정시군구코드 5자리이고, 그 지역이 대상에 들어 있는 정책을 모두 돌려준다(여러 시군 공통 정책 포함).
 * 인증키가 URL 에 들어가므로 URL 을 로그·예외 메시지에 남기지 않는다 — 가맹점 클라이언트와 같은 이유다.
 */
@Component
public class YouthPolicyApiClient {

    private final RestClient restClient;
    private final YouthPolicyProperties props;

    public YouthPolicyApiClient(@Qualifier("youthPolicyRestClient") RestClient restClient, YouthPolicyProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    public record PolicyPage(long totalCount, List<Policy> items) {
    }

    /** 앱이 쓰는 필드만 추렸다. 날짜는 {@code yyyyMMdd} 문자열 그대로. */
    public record Policy(
            String plcyNo, String name, String keywords, String explanation, String category, String subCategory,
            String supportContent, String applyMethod, String applyPeriod, String bizBegin, String bizEnd,
            String applyUrl, String refUrl, String institution, Integer minAge, Integer maxAge, String zipCodes) {
    }

    /** @param page 1부터 */
    public PolicyPage fetch(String zipCd, int page, int pageSize) {
        if (!props.hasKey()) {
            throw new IllegalStateException("YOUTH_POLICY_API_KEY 가 설정되지 않았다");
        }
        URI uri = URI.create(props.baseUrl() + "?apiKeyNm=" + enc(props.key()) + "&pageNum=" + page
                + "&pageSize=" + pageSize + "&rtnType=json&zipCd=" + enc(zipCd));
        JsonNode body;
        try {
            body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("청년정책 API 가 " + e.getStatusCode().value() + " 로 응답했다");
        } catch (RestClientException e) {
            throw new IllegalStateException("청년정책 API 호출 실패(" + e.getClass().getSimpleName() + ")");
        }
        return parse(body);
    }

    static PolicyPage parse(JsonNode body) {
        JsonNode list = body == null ? null : body.path("result").path("youthPolicyList");
        if (list == null || !list.isArray()) {
            String msg = body == null ? "빈 응답" : body.path("resultMessage").asString("알 수 없음");
            throw new IllegalStateException("청년정책 API 응답을 읽을 수 없다 (" + msg + ")");
        }
        List<Policy> items = new ArrayList<>();
        for (JsonNode n : list) {
            String no = n.path("plcyNo").asString("").strip();
            String name = n.path("plcyNm").asString("").strip();
            if (no.isEmpty() || name.isEmpty()) {
                continue;
            }
            items.add(new Policy(no, name, text(n, "plcyKywdNm"), text(n, "plcyExplnCn"), text(n, "lclsfNm"),
                    text(n, "mclsfNm"), text(n, "plcySprtCn"), text(n, "plcyAplyMthdCn"), text(n, "aplyYmd"),
                    text(n, "bizPrdBgngYmd"), text(n, "bizPrdEndYmd"), text(n, "aplyUrlAddr"), text(n, "refUrlAddr1"),
                    text(n, "operInstCdNm"), age(n.path("sprtTrgtMinAge")), age(n.path("sprtTrgtMaxAge")),
                    text(n, "zipCd")));
        }
        return new PolicyPage(body.path("result").path("pagging").path("totCount").asLong(0), items);
    }

    private static String text(JsonNode n, String field) {
        String v = n.path(field).asString("").replace("\r", "").strip();
        return v.isEmpty() ? null : v;
    }

    /** 0·빈 값은 "제한 없음"이라 {@code null}. */
    private static Integer age(JsonNode n) {
        try {
            int v = Integer.parseInt(n.asString("").strip());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
