package mockbank;

import lombok.extern.slf4j.Slf4j;
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

    @PostMapping("/api/withdraw")
    public ResponseEntity<Void> receiveWithdrawal(@RequestBody WithdrawRequest req) {
        log.info("Mock bank received withdrawal: transactionId={}, amount={}, bankCode={}, bankAccount={}",
                req.transactionId(), req.amount(), req.bankCode(), req.bankAccount());

        int delaySeconds = ThreadLocalRandom.current().nextInt(3, 9); // 3 to 8 inclusive

        CompletableFuture.runAsync(() -> {
            try {
                log.info("Mock bank processing: transactionId={}, delay={}s",
                        req.transactionId(), delaySeconds);
                Thread.sleep(delaySeconds * 1000L);

                String body = String.format(
                        "{\"transactionId\":\"%s\",\"result\":\"SUCCESS\"}",
                        req.transactionId());

                HttpRequest callback = HttpRequest.newBuilder()
                        .uri(URI.create(req.callbackUrl()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response =
                        httpClient.send(callback, HttpResponse.BodyHandlers.ofString());
                log.info("Mock bank callback sent: transactionId={}, status={}",
                        req.transactionId(), response.statusCode());

            } catch (Exception e) {
                log.error("Mock bank callback failed: transactionId={}", req.transactionId(), e);
            }
        });

        return ResponseEntity.ok().build();
    }
}
