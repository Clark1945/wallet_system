package mockbank;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@RestController
public class WithdrawController {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${mock-bank.fail-rate:0.10}")
    private double failRate;

    @Value("${mock-bank.no-callback-rate:0.10}")
    private double noCallbackRate;

    @PostMapping("/api/withdraw")
    public ResponseEntity<Void> receiveWithdrawal(@RequestBody WithdrawRequest req) {
        log.info("Mock bank received withdrawal: transactionId={}, amount={}, bankCode={}, bankAccount={}",
                req.transactionId(), req.amount(), req.bankCode(), req.bankAccount());

        int delaySeconds = ThreadLocalRandom.current().nextInt(3, 9); // 3 to 8 inclusive
        // Capture trace ID before entering async thread (MDC is thread-local)
        String traceId = MDC.get("traceId");

        CompletableFuture.runAsync(() -> {
            if (traceId != null) MDC.put("traceId", traceId);
            try {
                double roll = ThreadLocalRandom.current().nextDouble();

                if (roll < noCallbackRate) {
                    log.warn("Mock bank simulating no-callback: transactionId={}, roll={}",
                            req.transactionId(), roll);
                    return;
                }

                log.info("Mock bank processing: transactionId={}, delay={}s",
                        req.transactionId(), delaySeconds);
                Thread.sleep(delaySeconds * 1000L);

                String result = (roll < noCallbackRate + failRate) ? "FAIL" : "SUCCESS";
                String body = String.format(
                        "{\"transactionId\":\"%s\",\"result\":\"%s\"}",
                        req.transactionId(), result);

                HttpRequest.Builder callbackBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(req.callbackUrl()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                if (traceId != null) callbackBuilder.header("X-Trace-Id", traceId);

                HttpResponse<String> response =
                        httpClient.send(callbackBuilder.build(), HttpResponse.BodyHandlers.ofString());
                log.info("Mock bank callback sent: transactionId={}, result={}, status={}",
                        req.transactionId(), result, response.statusCode());

            } catch (Exception e) {
                log.error("Mock bank callback failed: transactionId={}", req.transactionId(), e);
            } finally {
                MDC.remove("traceId");
            }
        });

        return ResponseEntity.ok().build();
    }
}
