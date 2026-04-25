package org.side_project.wallet_system.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.internal.dto.*;
import org.side_project.wallet_system.wallet.WalletService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class DepositInternalController {

    private final PaymentTokenService paymentTokenService;
    private final WalletService walletService;

    @GetMapping("/token/{token}")
    public ResponseEntity<PaymentTokenData> validateToken(@PathVariable String token) {
        PaymentTokenData data = paymentTokenService.validateAndConsumeToken(token);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }

    @PostMapping("/deposit/initiate")
    public ResponseEntity<InitiateDepositResponse> initiateDeposit(@RequestBody InitiateDepositRequest req) {
        UUID transactionId = walletService.initiateDeposit(req.memberId(), req.amount(), req.notifyEmail());
        return ResponseEntity.ok(new InitiateDepositResponse(transactionId));
    }

    @PostMapping("/deposit/link-external")
    public ResponseEntity<Void> linkExternalId(@RequestBody LinkExternalRequest req) {
        walletService.linkPaymentExternalId(req.transactionId(), req.externalId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/deposit/complete")
    public ResponseEntity<Void> completeDeposit(@RequestBody CompleteDepositRequest req) {
        walletService.completeDeposit(req.transactionId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/deposit/complete-by-external")
    public ResponseEntity<Void> completeByExternalId(@RequestBody CompleteByExternalRequest req) {
        boolean found = walletService.completeDepositByExternalId(req.externalId());
        if (!found) {
            log.warn("No pending deposit for externalId={}", req.externalId());
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }
}
