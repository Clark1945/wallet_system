package org.side_project.wallet_system.wallet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WithdrawWebhookController {

    private final WalletService walletService;

    @PostMapping("/withdraw/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, String> payload) {
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
}
