package com.back.was.domain.merchant;

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
 * 한국조폐공사 통합 가맹점기본정보 API(공공데이터포털) 클라이언트.
 *
 * <pre>
 * GET {baseUrl}/franchiseV3?serviceKey=…&amp;page=1&amp;perPage=1000
 *     &amp;cond[usage_rgn_cd::EQ]=44130&amp;cond[bzmn_stts::EQ]=01&amp;returnType=JSON
 * </pre>
 *
 * <p>{@code bzmn_stts=01}(계속사업자)은 포털 문서의 요청변수 표에는 없지만 실제로 동작한다 — 천안
 * 18,874건이 10,296건으로 줄어든다. 폐업 가맹점이 40% 가량 섞여 있어서 서버에서 거르지 않으면
 * 문 닫은 가게를 추천하게 된다.
 *
 * <p><b>서비스키가 URL 에 들어가므로 URL 을 로그·예외 메시지에 남기지 않는다.</b> Spring 의
 * {@code RestClientException} 메시지에는 요청 URL 이 그대로 들어가서, 여기서 잡아 상태 코드만
 * 남긴 예외로 바꿔 던진다.
 */
@Component
public class MerchantApiClient {

    private static final String OPERATION = "/franchiseV3";
    /** 계속사업자 */
    private static final String ACTIVE_STATUS = "01";

    private final RestClient restClient;
    private final MerchantProperties props;

    public MerchantApiClient(@Qualifier("merchantRestClient") RestClient restClient, MerchantProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    /** API 한 페이지. {@code matchCount} 는 조건에 맞는 전체 건수다. */
    public record FranchisePage(long matchCount, List<Franchise> items) {
    }

    public record Franchise(
            String altText, String name, String emdName, String address, String phone,
            Double lat, Double lng, String ksicCode, String ksicName, String baseDate, String regStatusName) {

        /** 등록 구분이 "해지" 인 가맹점은 더는 쓸 수 없다. */
        public boolean isTerminated() {
            return "해지".equals(regStatusName);
        }
    }

    /**
     * @param page 1부터
     * @throws IllegalStateException 키가 없거나 호출·응답이 잘못된 경우 (메시지에 URL·키 없음)
     */
    public FranchisePage fetch(String regionCode, int page, int perPage) {
        if (!props.hasKey()) {
            throw new IllegalStateException("MERCHANT_API_KEY 가 설정되지 않았다");
        }
        URI uri = URI.create(props.baseUrl() + OPERATION + "?" + query(regionCode, page, perPage));

        JsonNode body;
        try {
            body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("가맹점 API 가 " + e.getStatusCode().value() + " 로 응답했다");
        } catch (RestClientException e) {
            throw new IllegalStateException("가맹점 API 호출 실패(" + e.getClass().getSimpleName() + ")");
        }
        return parse(body);
    }

    static FranchisePage parse(JsonNode body) {
        if (body == null || !body.path("data").isArray()) {
            // 키·경로 오류는 200/400 에 "OpenAPI_ServiceResponse" 로 온다 — 코드만 남긴다.
            String reason = body == null ? "빈 응답"
                    : body.path("OpenAPI_ServiceResponse").path("cmmMsgHeader").path("returnReasonCode").asString("알 수 없음");
            throw new IllegalStateException("가맹점 API 응답을 읽을 수 없다 (reason=" + reason + ")");
        }
        List<Franchise> items = new ArrayList<>();
        for (JsonNode n : body.path("data")) {
            String altText = n.path("alt_text").asString("");
            String name = n.path("frcs_nm").asString("").strip();
            if (altText.isBlank() || name.isBlank()) {
                continue;
            }
            items.add(new Franchise(
                    altText,
                    name,
                    n.path("emd_nm").asString(null),
                    address(n),
                    n.path("frcs_rprs_telno").asString(null),
                    number(n.path("lat")),
                    number(n.path("lot")),
                    n.path("ksic_cd").asString(null),
                    n.path("ksic_cd_nm").asString(null),
                    n.path("crtr_ymd").asString(null),
                    n.path("frcs_reg_se_nm").asString(null)));
        }
        return new FranchisePage(body.path("matchCount").asLong(0), items);
    }

    private static String address(JsonNode n) {
        String addr = n.path("frcs_addr").asString("").strip();
        String detail = n.path("frcs_dtl_addr").asString("").strip();
        String joined = detail.isEmpty() ? addr : addr + " " + detail;
        return joined.isEmpty() ? null : joined;
    }

    private static Double number(JsonNode n) {
        return n.isNumber() ? n.asDouble() : null;
    }

    private String query(String regionCode, int page, int perPage) {
        return String.join("&",
                "serviceKey=" + enc(props.key()),
                "page=" + page,
                "perPage=" + perPage,
                enc("cond[usage_rgn_cd::EQ]") + "=" + enc(regionCode),
                enc("cond[bzmn_stts::EQ]") + "=" + ACTIVE_STATUS,
                "returnType=JSON");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
