package org.side_project.payment_service.payment;

import lombok.Builder;
import lombok.Data;

/**
 * Holds all SBPS link-type purchase request fields in definition order.
 * Fields map 1-to-1 to the hidden inputs in sb-payment.html.
 */
@Data
@Builder
public class SBPaymentRequest {
    private String gatewayUrl;

    // ── Field order follows SBPS interface spec (used for hash computation) ──
    private String payMethod;           // 1
    private String merchantId;          // 2
    private String serviceId;           // 3
    private String custCode;            // 4
    private String spsCustNo;           // 5
    private String spsPaymentNo;        // 6
    private String orderId;             // 7
    private String itemId;              // 8
    private String payItemId;           // 9  (financial institution item ID)
    private String itemName;            // 10
    private String tax;                 // 11 (amount of tax; included in amount)
    private String amount;              // 12
    private String payType;             // 13
    private String autoChargeType;      // 14
    private String serviceType;         // 15
    private String divSettele;          // 16
    private String lastChargeMonth;     // 17
    private String campType;            // 18
    private String trackingId;          // 19
    private String terminalType;        // 20
    private String successUrl;          // 21
    private String cancelUrl;           // 22
    private String errorUrl;            // 23
    private String pageconUrl;          // 24
    private String free1;               // 25
    private String free2;               // 26
    private String free3;               // 27
    private String freeCsv;             // 28
    // 29-37: detail rows (omitted per spec when not used)
    private String requestDate;         // 38
    private String limitSecond;         // 39
    private String spsHashcode;         // 40
}
