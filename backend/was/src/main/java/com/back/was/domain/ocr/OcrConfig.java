package com.back.was.domain.ocr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Django OCR 서버 호출용 HTTP 클라이언트. {@code ocr.base-url} 이 있을 때만 만든다. */
@Configuration
@EnableConfigurationProperties(OcrProperties.class)
public class OcrConfig {

    /** {@link DjangoReceiptOcrClient} 와 {@link GeminiReceiptOcrClient} 를 가르는 조건과 같은 식. */
    static final String DJANGO_CONFIGURED = "!'${ocr.base-url:}'.isBlank()";
    static final String DJANGO_NOT_CONFIGURED = "'${ocr.base-url:}'.isBlank()";

    @Bean(name = "ocrRestClient")
    @ConditionalOnExpression(DJANGO_CONFIGURED)
    public RestClient ocrRestClient(OcrProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout((int) props.timeout().toMillis());

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
