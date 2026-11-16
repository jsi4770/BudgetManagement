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

    /** {@link DjangoReceiptOcrClient} 와 {@link GeminiReceiptOcrClient} 를 가르는 조건과 같은 식. */
    static final String DJANGO_CONFIGURED = "!'${ocr.base-url:}'.isBlank()";
    static final String DJANGO_NOT_CONFIGURED = "'${ocr.base-url:}'.isBlank()";

    /**
     * 본문을 메모리에 다 모은 뒤 {@code Content-Length} 를 붙여 보낸다 ({@link BufferingClientHttpRequestFactory}).
     *
     * <p>Spring 6.1 부터 {@link SimpleClientHttpRequestFactory} 는 본문을 {@code Transfer-Encoding: chunked} 로
     * 흘려보내는데, Django 개발 서버({@code runserver}, WSGI)는 chunked 요청 본문을 읽지 못한다. 본문이 빈 것으로
     * 보여 "image REQUIRED" 400 이 오고, 그게 여기서 502 OCR_FAILED 가 됐다. 이미지는 10MB 상한이라 버퍼링해도 된다.
     */
    @Bean(name = "ocrRestClient")
    @ConditionalOnExpression(DJANGO_CONFIGURED)
    public RestClient ocrRestClient(OcrProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout((int) props.timeout().toMillis());

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(new BufferingClientHttpRequestFactory(factory))
                .build();
    }
}
