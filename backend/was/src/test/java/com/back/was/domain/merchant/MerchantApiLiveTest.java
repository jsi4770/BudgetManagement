package com.back.was.domain.merchant;

import com.back.was.domain.merchant.MerchantApiClient.FranchisePage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 공공데이터포털 API 를 한 번 부르는 확인용 테스트. <b>환경변수 {@code MERCHANT_API_KEY} 가 있을 때만</b>
 * 돌고, 평소 {@code gradle test} 에서는 건너뛴다(키·네트워크 없이도 CI 가 통과해야 해서).
 *
 * <pre>MERCHANT_API_KEY=… ./gradlew test --tests '*MerchantApiLiveTest'</pre>
 *
 * 일일 호출 한도(개발계정 10,000건)를 아끼려고 지역마다 5건씩만 받는다.
 */
@EnabledIfEnvironmentVariable(named = "MERCHANT_API_KEY", matches = ".+")
class MerchantApiLiveTest {

    private MerchantApiClient client() {
        MerchantProperties props = new MerchantProperties(null, System.getenv("MERCHANT_API_KEY"), null, null);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        return new MerchantApiClient(RestClient.builder().requestFactory(factory).build(), props);
    }

    @Test
    @DisplayName("천안·아산 모두 영업 중 가맹점이 내려오고 지역코드·좌표·업종이 채워진다")
    void fetchesActiveMerchantsForBothRegions() {
        for (SupportedRegion region : SupportedRegion.values()) {
            FranchisePage page = client().fetch(region.code(), 1, 5);

            assertThat(page.matchCount()).as(region.displayName() + " 매칭 건수").isGreaterThan(1000);
            assertThat(page.items()).as(region.displayName() + " 항목").isNotEmpty().hasSizeLessThanOrEqualTo(5);
            assertThat(page.items()).allSatisfy(f -> {
                assertThat(f.altText()).isNotBlank();
                assertThat(f.name()).isNotBlank();
                assertThat(f.address()).contains("충");   // 충남/충청남도
                assertThat(f.ksicCode()).isNotBlank();
            });
        }
    }
}
