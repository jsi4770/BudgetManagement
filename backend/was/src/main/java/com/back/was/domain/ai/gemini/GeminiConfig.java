package com.back.was.domain.ai.gemini;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Gemini 호출용 HTTP 클라이언트를 빈으로 등록한다. (Spring Boot 3.2+ 의 RestClient, 추가 라이브러리 없음)
 * 빈 이름을 geminiRestClient 로 고정하고 주입받는 쪽은 @Qualifier 로 지정한다.
 */
@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfig {

    @Bean(name = "geminiRestClient")
    public RestClient geminiRestClient(GeminiProperties props) {
        return build(props, props.timeout());
    }

    /** 이미지 호출 전용(읽기 타임아웃만 다르다). */
    @Bean(name = "geminiImageRestClient")
    public RestClient geminiImageRestClient(GeminiProperties props) {
        return build(props, props.imageTimeout());
    }

    private static RestClient build(GeminiProperties props, java.time.Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);                          // 연결 3초
        factory.setReadTimeout((int) readTimeout.toMillis());      // 응답 대기

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                // 키는 URL(?key=)이 아니라 헤더로 → 서버 접근 로그에 키가 남지 않음
                .defaultHeader("x-goog-api-key", props.hasApiKey() ? props.apiKey() : "")
                .build();
    }
}
