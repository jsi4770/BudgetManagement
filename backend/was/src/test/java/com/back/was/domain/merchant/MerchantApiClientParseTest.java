package com.back.was.domain.merchant;

import com.back.was.domain.merchant.MerchantApiClient.FranchisePage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 API 응답(2026-11-17 천안 호출)의 모양 그대로 파싱을 확인한다. 외부 호출은 하지 않는다. */
class MerchantApiClientParseTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String SAMPLE = """
            {"currentCount":2,"data":[
              {"alt_text":"286fc7bf5b7670091aa51792957d57da","brno":"8050602569","bzmn_stts_nm":"계속사업자",
               "crtr_ymd":"20231117","emd_nm":"성정동","frcs_addr":"충남 천안시 서북구 쌍용대로 250",
               "frcs_dtl_addr":"1층 (성정동)","frcs_nm":"네네치킨성정점","frcs_reg_se_nm":"신규",
               "frcs_rprs_telno":"0415744479","ksic_cd":"561","ksic_cd_nm":"음식점 및 주점업",
               "lat":36.8195382,"lot":127.136109,"usage_rgn_cd":"44130"},
              {"alt_text":"a24015b91e0d8a0e65d226352a70355a","frcs_addr":"충남 천안시 서북구 서부1길 66",
               "frcs_dtl_addr":null,"frcs_nm":"솔찬밥상","frcs_reg_se_nm":"해지","ksic_cd":"561",
               "lat":null,"lot":null}
            ],"matchCount":10296,"page":1,"perPage":2,"totalCount":8853741}
            """;

    @Test
    @DisplayName("응답의 필드를 가맹점 모델로 옮기고 주소는 상세주소와 합친다")
    void parsesFields() throws Exception {
        FranchisePage page = MerchantApiClient.parse(MAPPER.readTree(SAMPLE));

        assertThat(page.matchCount()).isEqualTo(10296);
        assertThat(page.items()).hasSize(2);

        var first = page.items().get(0);
        assertThat(first.altText()).isEqualTo("286fc7bf5b7670091aa51792957d57da");
        assertThat(first.name()).isEqualTo("네네치킨성정점");
        assertThat(first.address()).isEqualTo("충남 천안시 서북구 쌍용대로 250 1층 (성정동)");
        assertThat(first.lat()).isEqualTo(36.8195382);
        assertThat(first.lng()).isEqualTo(127.136109);
        assertThat(first.ksicCode()).isEqualTo("561");
        assertThat(first.baseDate()).isEqualTo("20231117");
    }

    @Test
    @DisplayName("null 위경도·상세주소는 null 로 두고, 등록구분이 해지면 표시한다")
    void handlesNullsAndTermination() throws Exception {
        var second = MerchantApiClient.parse(MAPPER.readTree(SAMPLE)).items().get(1);

        assertThat(second.lat()).isNull();
        assertThat(second.lng()).isNull();
        assertThat(second.address()).isEqualTo("충남 천안시 서북구 서부1길 66");
        assertThat(second.isTerminated()).isTrue();
    }

    @Test
    @DisplayName("data 가 없는 오류 응답은 사유 코드만 담은 예외가 된다")
    void errorBodyBecomesException() throws Exception {
        var error = MAPPER.readTree("""
                {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":"NO_OPENAPI_SERVICE_ERROR","returnReasonCode":"12"}}}
                """);

        assertThatThrownBy(() -> MerchantApiClient.parse(error))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reason=12");
    }
}
