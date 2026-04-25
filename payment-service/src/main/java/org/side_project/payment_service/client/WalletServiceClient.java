package org.side_project.payment_service.client;

import lombok.extern.slf4j.Slf4j;
import org.side_project.payment_service.client.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class WalletServiceClient {

    private final RestClient restClient;

    public WalletServiceClient(RestClient.Builder builder,
                               @Value("${wallet.service.base-url}") String baseUrl,
                               @Value("${internal.service.secret}") String internalSecret) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Secret", internalSecret)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public PaymentTokenData validateToken(String token) {
        try {
            return restClient.get()
                    .uri("/internal/token/{token}", token)
                    .retrieve()
                    .body(PaymentTokenData.class);
        } catch (RestClientException e) {
            log.warn("Payment token validation failed: token={}, error={}", token, e.getMessage());
            return null;
        }
    }

    public UUID initiateDeposit(UUID memberId, BigDecimal amount, String notifyEmail) {
        InitiateDepositResponse response = restClient.post()
                .uri("/internal/deposit/initiate")
                .body(new InitiateDepositRequest(memberId, amount, notifyEmail))
                .retrieve()
                .body(InitiateDepositResponse.class);
        if (response == null) {
            throw new IllegalStateException("No response from wallet-service for initiateDeposit");
        }
        return response.transactionId();
    }

    public void linkExternalId(UUID transactionId, String externalId) {
        restClient.post()
                .uri("/internal/deposit/link-external")
                .body(new LinkExternalRequest(transactionId, externalId))
                .retrieve()
                .toBodilessEntity();
    }

    public void completeDeposit(UUID transactionId) {
        restClient.post()
                .uri("/internal/deposit/complete")
                .body(new CompleteDepositRequest(transactionId))
                .retrieve()
                .toBodilessEntity();
    }

    public boolean completeDepositByExternalId(String externalId) {
        try {
            restClient.post()
                    .uri("/internal/deposit/complete-by-external")
                    .body(new CompleteByExternalRequest(externalId))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("completeDepositByExternalId failed: externalId={}, error={}", externalId, e.getMessage());
            return false;
        }
    }
}
