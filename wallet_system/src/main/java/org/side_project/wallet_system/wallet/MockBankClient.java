package org.side_project.wallet_system.wallet;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockBankClient {

    private final HttpClient httpClient;

    @Value("${mock-bank.url}")
    private String mockBankUrl;

    @CircuitBreaker(name = "mockBankWithdraw", fallbackMethod = "sendWithdrawRequestFallback")
    public void sendWithdrawRequest(String transactionId, BigDecimal amount,
                                    String bankCode, String bankAccount,
                                    String callbackUrl, String traceId) throws Exception {
        String body = String.format(
            "{\"transactionId\":\"%s\",\"amount\":\"%s\",\"bankCode\":\"%s\"," +
            "\"bankAccount\":\"%s\",\"callbackUrl\":\"%s\"}",
            transactionId, amount.toPlainString(), bankCode, bankAccount, callbackUrl);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(mockBankUrl + "/api/withdraw"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (traceId != null) builder.header("X-Trace-Id", traceId);

        httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        log.info("Withdrawal request sent to mock bank: transactionId={}", transactionId);
    }

    // Called by Resilience4j when circuit is OPEN or call fails after CB trips
    private void sendWithdrawRequestFallback(String transactionId, BigDecimal amount,
                                              String bankCode, String bankAccount,
                                              String callbackUrl, String traceId,
                                              Throwable t) {
        log.warn("Circuit OPEN — mock-bank unreachable, failing withdrawal immediately: transactionId={}, cause={}",
                transactionId, t.getMessage());
        throw new MockBankUnavailableException("Mock bank circuit is open: " + t.getMessage());
    }
}
