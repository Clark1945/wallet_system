package org.side_project.wallet_system.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.wallet.WalletService;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SBPaymentServiceTest {

    @Mock WalletService walletService;
    @InjectMocks SBPaymentService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "merchantId", "19788");
        ReflectionTestUtils.setField(service, "serviceId", "001");
        ReflectionTestUtils.setField(service, "hashKey", "testhashkey123");
        ReflectionTestUtils.setField(service, "gatewayUrl", "https://test-gateway.example.com");
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");
    }

    // ─── buildRequest ─────────────────────────────────────────────────────────

    @Test
    void buildRequest_returnsRequestWithCorrectMerchantFields() {
        UUID memberId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1500");
        given(walletService.initiateDeposit(memberId, amount)).willReturn(UUID.randomUUID());

        SBPaymentRequest req = service.buildRequest(memberId, amount);

        assertThat(req.getMerchantId()).isEqualTo("19788");
        assertThat(req.getServiceId()).isEqualTo("001");
        assertThat(req.getAmount()).isEqualTo("1500");
        assertThat(req.getCustCode()).isEqualTo(memberId.toString());
        assertThat(req.getGatewayUrl()).isEqualTo("https://test-gateway.example.com");
    }

    @Test
    void buildRequest_computesNonEmptyHashcode() {
        UUID memberId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1000");
        given(walletService.initiateDeposit(memberId, amount)).willReturn(UUID.randomUUID());

        SBPaymentRequest req = service.buildRequest(memberId, amount);

        assertThat(req.getSpsHashcode()).isNotBlank().hasSize(40); // SHA-1 hex = 40 chars
    }

    @Test
    void buildRequest_setsCallbackUrlsWithBaseUrl() {
        UUID memberId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("500");
        given(walletService.initiateDeposit(memberId, amount)).willReturn(UUID.randomUUID());

        SBPaymentRequest req = service.buildRequest(memberId, amount);

        assertThat(req.getSuccessUrl()).isEqualTo("http://localhost:8080/payment/sbpayment/complete");
        assertThat(req.getCancelUrl()).isEqualTo("http://localhost:8080/payment/sbpayment/cancel");
        assertThat(req.getErrorUrl()).isEqualTo("http://localhost:8080/payment/sbpayment/error");
        assertThat(req.getPageconUrl()).isEqualTo("http://localhost:8080/payment/sbpayment/result");
    }

    @Test
    void buildRequest_callsInitiateDepositAndLinksExternalId() {
        UUID memberId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("2000");
        given(walletService.initiateDeposit(memberId, amount)).willReturn(transactionId);

        service.buildRequest(memberId, amount);

        then(walletService).should().initiateDeposit(memberId, amount);
        then(walletService).should().linkPaymentExternalId(eq(transactionId), anyString());
    }

    // ─── processResult ────────────────────────────────────────────────────────

    @Test
    void processResult_nonOkResult_returnsFalse() {
        Map<String, String> params = Map.of("res_result", "NG", "order_id", "order123");

        assertThat(service.processResult(params)).isFalse();
        then(walletService).shouldHaveNoInteractions();
    }

    @Test
    void processResult_missingOrderId_returnsFalse() {
        Map<String, String> params = Map.of("res_result", "OK");

        assertThat(service.processResult(params)).isFalse();
    }

    @Test
    void processResult_unknownOrderId_returnsFalse() {
        given(walletService.completeDepositByExternalId("nonexistent-order")).willReturn(false);

        Map<String, String> params = Map.of("res_result", "OK", "order_id", "nonexistent-order");

        assertThat(service.processResult(params)).isFalse();
        then(walletService).should().completeDepositByExternalId("nonexistent-order");
    }

    @Test
    void processResult_validOrder_completesDepositAndReturnsTrue() {
        String orderId = "valid-order-001";
        given(walletService.completeDepositByExternalId(orderId)).willReturn(true);

        Map<String, String> params = Map.of("res_result", "OK", "order_id", orderId);

        assertThat(service.processResult(params)).isTrue();
        then(walletService).should().completeDepositByExternalId(orderId);
    }

    @Test
    void processResult_walletServiceThrows_returnsFalse() {
        String orderId = "error-order-001";
        willThrow(new RuntimeException("DB error")).given(walletService).completeDepositByExternalId(orderId);

        Map<String, String> params = Map.of("res_result", "OK", "order_id", orderId);

        assertThat(service.processResult(params)).isFalse();
    }
}
