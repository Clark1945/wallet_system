package org.side_project.payment_service.config;

import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public StripeClient stripeClient(@Value("${stripe.secret-key}") String secretKey) {
        return new StripeClient(secretKey);
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        // Without timeouts a slow/hung wallet-service would block the calling
        // thread indefinitely. Cap connect at 5s and read at 10s.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(factory);
    }
}
