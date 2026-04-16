package org.side_project.wallet_system.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.wallet.WalletService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SBPaymentService {

    private final WalletService walletService;

    @Value("${sbpayment.merchant-id}")
    private String merchantId;

    @Value("${sbpayment.service-id}")
    private String serviceId;

    @Value("${sbpayment.hash-key}")
    private String hashKey;

    @Value("${sbpayment.gateway-url}")
    private String gatewayUrl;

    @Value("${sbpayment.base-url}")
    private String baseUrl;

    // SBPS operates in JST — request_date must be in JST regardless of server timezone
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final String PAY_METHOD    = "credit";
    private static final String ITEM_ID       = "WALLET_DEPOSIT";
    private static final String ITEM_NAME     = "ウォレット入金";
    private static final String PAY_TYPE      = "0";
    private static final String SERVICE_TYPE  = "0";
    private static final String TERMINAL_TYPE = "0";
    private static final String LIMIT_SECOND  = "600";

    /** orderId → pending payment (in-memory; acceptable for test environment) */
    private final Map<String, PendingOrder> pendingOrders = new ConcurrentHashMap<>();

    private record PendingOrder(UUID memberId, BigDecimal amount) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Build request
    // ─────────────────────────────────────────────────────────────────────────

    public SBPaymentRequest buildRequest(UUID memberId, BigDecimal amount) {
        String orderId     = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String custCode    = memberId.toString();
        // Use JST — SBPS rejects requests whose request_date exceeds limit_second in JST
        String requestDate = LocalDateTime.now(JST).format(DATE_FMT);
        String amountStr   = String.valueOf(amount.longValue());

        String successUrl = baseUrl + "/payment/sbpayment/complete";
        String cancelUrl  = baseUrl + "/payment/sbpayment/cancel";
        String errorUrl   = baseUrl + "/payment/sbpayment/error";
        String pageconUrl = baseUrl + "/payment/sbpayment/result";

        // All fields in definition order (fields 1-28 + 38-39, detail rows omitted).
        // Empty fields must still be included per spec; their empty values add nothing
        // to the hash but the form must send them.
        String hashcode = computeHashcode(
            PAY_METHOD, merchantId, serviceId, custCode,
            "", "",           // sps_cust_no, sps_payment_no
            orderId,
            ITEM_ID,
            "",               // pay_item_id
            ITEM_NAME,
            "",               // tax
            amountStr,
            PAY_TYPE,
            "",               // auto_charge_type
            SERVICE_TYPE,
            "", "", "", "",   // div_settele, last_charge_month, camp_type, tracking_id
            TERMINAL_TYPE,
            successUrl, cancelUrl, errorUrl, pageconUrl,
            "", "", "",       // free1, free2, free3
            "",               // free_csv
            requestDate, LIMIT_SECOND
        );

        pendingOrders.put(orderId, new PendingOrder(memberId, amount));
        log.info("SBPayment request built: orderId={}, memberId={}, amount={}, requestDate={}",
                 orderId, memberId, amount, requestDate);

        return SBPaymentRequest.builder()
            .gatewayUrl(gatewayUrl)
            .payMethod(PAY_METHOD)
            .merchantId(merchantId)
            .serviceId(serviceId)
            .custCode(custCode)
            .spsCustNo("")
            .spsPaymentNo("")
            .orderId(orderId)
            .itemId(ITEM_ID)
            .payItemId("")
            .itemName(ITEM_NAME)
            .tax("")
            .amount(amountStr)
            .payType(PAY_TYPE)
            .autoChargeType("")
            .serviceType(SERVICE_TYPE)
            .divSettele("")
            .lastChargeMonth("")
            .campType("")
            .trackingId("")
            .terminalType(TERMINAL_TYPE)
            .successUrl(successUrl)
            .cancelUrl(cancelUrl)
            .errorUrl(errorUrl)
            .pageconUrl(pageconUrl)
            .free1("")
            .free2("")
            .free3("")
            .freeCsv("")
            .requestDate(requestDate)
            .limitSecond(LIMIT_SECOND)
            .spsHashcode(hashcode)
            .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Process result CGI notification from SBPS
    // ─────────────────────────────────────────────────────────────────────────

    public boolean processResult(Map<String, String> params) {
        String resResult = params.getOrDefault("res_result", "");
        if (!"OK".equalsIgnoreCase(resResult)) {
            log.warn("SBPayment result not OK: res_result={}, res_err_code={}",
                     resResult, params.get("res_err_code"));
            return false;
        }

        String orderId = params.get("order_id");
        if (orderId == null || orderId.isBlank()) {
            log.error("SBPayment result missing order_id");
            return false;
        }

        PendingOrder pending = pendingOrders.remove(orderId);
        if (pending == null) {
            log.error("SBPayment result: no pending order found for orderId={}", orderId);
            return false;
        }

        try {
            walletService.deposit(pending.memberId(), pending.amount());
            log.info("SBPayment deposit completed: orderId={}, memberId={}, amount={}",
                     orderId, pending.memberId(), pending.amount());
            return true;
        } catch (Exception e) {
            log.error("SBPayment deposit failed: orderId={}, error={}", orderId, e.getMessage(), e);
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHA1 hash — request uses UTF-8 per SBPS spec
    // ─────────────────────────────────────────────────────────────────────────

    private String computeHashcode(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (String field : fields) {
            sb.append(field.strip());
        }
        sb.append(hashKey);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
