package com.example.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AppConfig {

    private RestClient buildClient(RestClient.Builder builder, String baseUrl, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(readTimeout);

        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public RestClient inventoryRestClient(RestClient.Builder builder,
                                          @Value("${inventory.service.url:http://localhost:8081}") String url) {
        return buildClient(builder, url, Duration.ofSeconds(3));
    }

    @Bean
    public RestClient paymentRestClient(RestClient.Builder builder,
                                        @Value("${payment.service.url:http://localhost:8082}") String url) {
        // 15s allows Stripe to complete before the order service times out,
        // preventing concurrent retry races that can cause a null paymentIntentId on the order.
        return buildClient(builder, url, Duration.ofSeconds(15));
    }

    @Bean
    public RestClient eventRestClient(RestClient.Builder builder,
                                      @Value("${event.service.url:http://localhost:8083}") String url) {
        return buildClient(builder, url, Duration.ofSeconds(3));
    }
}
