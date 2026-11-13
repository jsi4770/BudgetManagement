package com.back.was.receipt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Django OCR 서버 호출용 HTTP 클라이언트 */
@Configuration
@EnableConfigurationProperties(OcrProperties.class)
public class OcrConfig {

    @Bean(name = "ocrRestClient")
    public RestClient ocrRestClient(OcrProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout((int) props.timeout().toMillis());
        return RestClient.builder().requestFactory(factory).build();
    }
}
