package org.side_project.wallet_system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public HttpClient httpClient() {
        // connectTimeout caps connection establishment; the per-request
        // HttpRequest.timeout() in MockBankClient caps the overall response wait.
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
}
