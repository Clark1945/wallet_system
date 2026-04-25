package org.side_project.wallet_system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.net.http.HttpClient;

@Configuration
@EnableAsync
public class AppConfig {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }
}
