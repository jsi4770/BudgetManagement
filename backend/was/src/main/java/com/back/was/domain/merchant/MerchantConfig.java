package com.back.was.domain.merchant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MerchantProperties.class)
public class MerchantConfig {

    @Bean(name = "merchantRestClient")
    public RestClient merchantRestClient(MerchantProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout((int) props.timeout().toMillis());
        return RestClient.builder().requestFactory(factory).build();
    }
}
