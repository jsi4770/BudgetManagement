package com.back.was.domain.youthpolicy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 온통청년 API 를 한 번 부르는 확인용. 환경변수 {@code YOUTH_POLICY_API_KEY} 가 있을 때만 돈다. */
@EnabledIfEnvironmentVariable(named = "YOUTH_POLICY_API_KEY", matches = ".+")
class YouthPolicyLiveTest {

    @Test
    @DisplayName("천안·아산 대상 정책이 내려오고 정책번호·이름·대상 지역이 채워진다")
    void fetchesPolicies() {
        var props = new YouthPolicyProperties(null, System.getenv("YOUTH_POLICY_API_KEY"), null, null);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(30_000);
        var client = new YouthPolicyApiClient(RestClient.builder().requestFactory(factory).build(), props);

        for (String zip : new String[]{"44130", "44200"}) {
            var page = client.fetch(zip, 1, 100);   // 동기화가 쓰는 크기·페이지 그대로
            assertThat(page.totalCount()).as(zip).isGreaterThan(10);
            assertThat(page.items()).isNotEmpty().allSatisfy(p -> {
                assertThat(p.plcyNo()).isNotBlank();
                assertThat(p.name()).isNotBlank();
                assertThat(p.zipCodes()).contains(zip);
            });
            if (page.totalCount() > 100) {
                assertThat(client.fetch(zip, 2, 100).items()).isNotEmpty();
            }
        }
    }
}
