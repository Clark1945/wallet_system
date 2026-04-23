package org.side_project.wallet_system.wallet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WithdrawWebhookController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WalletService walletService;

    @Value("${withdraw.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/withdraw/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {

        if (!isValidSignature(rawBody, signature)) {
            log.warn("Withdraw webhook rejected: invalid or missing signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, String> payload;
        try {
            payload = MAPPER.readValue(rawBody, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Withdraw webhook rejected: invalid JSON body");
            return ResponseEntity.badRequest().build();
        }

        log.info("Withdraw webhook received: payload={}", payload);

        String transactionId = payload.get("transactionId");
        String result = payload.get("result");

        if (transactionId == null || transactionId.isBlank()) {
            log.warn("Withdraw webhook rejected: missing transactionId, payload={}", payload);
            return ResponseEntity.badRequest().build();
        }

        UUID txId;
        try {
            txId = UUID.fromString(transactionId);
        } catch (IllegalArgumentException e) {
            log.warn("Withdraw webhook rejected: invalid transactionId={}", transactionId);
            return ResponseEntity.badRequest().build();
        }

        if ("SUCCESS".equalsIgnoreCase(result)) {
            log.info("Withdraw webhook: processing SUCCESS for transactionId={}", transactionId);
            try {
                walletService.completeWithdrawal(txId);
                log.info("Withdraw webhook: transactionId={} marked COMPLETED", transactionId);
                return ResponseEntity.ok().build();
            } catch (Exception e) {
                log.error("Withdraw webhook: failed to complete transactionId={}", transactionId, e);
                return ResponseEntity.internalServerError().build();
            }
        } else {
            log.warn("Withdraw webhook: result={} for transactionId={} — marking FAILED and refunding",
                    result, transactionId);
            try {
                walletService.failWithdrawal(txId);
                log.info("Withdraw webhook: transactionId={} marked FAILED, balance refunded", transactionId);
                return ResponseEntity.ok().build();
            } catch (Exception e) {
                log.error("Withdraw webhook: failed to mark transactionId={} as FAILED", transactionId, e);
                return ResponseEntity.internalServerError().build();
            }
        }
    }

    private boolean isValidSignature(byte[] body, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = computeHmacSha256(body);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String computeHmacSha256(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
