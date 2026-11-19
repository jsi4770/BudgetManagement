package com.back.was.domain.ocr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Django OCR 서버 호출용 HTTP 클라이언트. {@code ocr.base-url} 이 있을 때만 만든다. */
@Configuration
@EnableConfigurationProperties(OcrProperties.class)
public class OcrConfig {

    /** {@link DjangoReceiptOcrClient} 와 {@link LlmReceiptOcrClient} 를 가르는 조건과 같은 식. */
    static final String DJANGO_CONFIGURED = "!'${ocr.base-url:}'.isBlank()";
    static final String DJANGO_NOT_CONFIGURED = "'${ocr.base-url:}'.isBlank()";

    @Bean(name = "ocrRestClient")
    @ConditionalOnExpression(DJANGO_CONFIGURED)
    public RestClient ocrRestClient(OcrProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout((int) props.timeout().toMillis());

        // 본문을 모아서 Content-Length 로 보낸다. Spring 6.1+ 의 SimpleClientHttpRequestFactory 는
        // 버퍼링하지 않아 multipart 가 chunked 로 나가는데, Django runserver 는 chunked 본문을 읽지 못하고
        // 400 "Bad request syntax" 로 거절한다. 이미지는 최대 10MB 라 메모리에 모아도 된다.
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(new BufferingClientHttpRequestFactory(factory))
                .build();
    }
}
