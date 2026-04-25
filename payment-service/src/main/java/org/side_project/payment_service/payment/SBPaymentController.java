package org.side_project.payment_service.payment;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.payment_service.client.WalletServiceClient;
import org.side_project.payment_service.client.dto.PaymentTokenData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/payment/sbpayment")
@RequiredArgsConstructor
public class SBPaymentController {

    private final SBPaymentService sbPaymentService;
    private final WalletServiceClient walletServiceClient;

    @Value("${wallet.service.public-url}")
    private String walletServicePublicUrl;

    @GetMapping("/request")
    public String paymentRequest(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            log.warn("SBPS request called without token — redirecting to wallet deposit");
            return "redirect:" + walletServicePublicUrl + "/deposit";
        }

        PaymentTokenData data = walletServiceClient.validateToken(token);
        if (data == null) {
            log.warn("SBPS request: invalid or expired token={}", token);
            return "redirect:" + walletServicePublicUrl + "/deposit";
        }

        SBPaymentRequest req = sbPaymentService.buildRequest(data.memberId(), data.amount(), data.notifyEmail());
        model.addAttribute("req", req);
        return "sb-payment";
    }

    /**
     * Result CGI endpoint — called server-to-server by SBPS after payment.
     * SBPS sends the POST body in Shift-JIS (Windows-31J); we must NOT use
     * @RequestParam here because Tomcat would decode with UTF-8 and throw
     * MalformedInputException on Japanese characters like item_name.
     */
    @PostMapping("/result")
    @ResponseBody
    public String paymentResult(HttpServletRequest request) {
        try {
            byte[] rawBody = request.getInputStream().readAllBytes();
            Map<String, String> params = parseShiftJisFormBody(rawBody);
            log.info("SBPayment result CGI received: res_result={}, order_id={}",
                     params.get("res_result"), params.get("order_id"));
            boolean success = sbPaymentService.processResult(params);
            return success ? "OK," : "NG,Payment processing failed";
        } catch (Exception e) {
            log.error("Failed to process SBPS result request", e);
            return "NG,Internal error";
        }
    }

    private static Map<String, String> parseShiftJisFormBody(byte[] body) {
        Charset sjis = Charset.forName("Windows-31J");
        String bodyStr = new String(body, StandardCharsets.ISO_8859_1);
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : bodyStr.split("&")) {
            if (pair.isEmpty()) continue;
            int idx = pair.indexOf('=');
            try {
                if (idx >= 0) {
                    params.put(
                        URLDecoder.decode(pair.substring(0, idx), sjis),
                        URLDecoder.decode(pair.substring(idx + 1), sjis)
                    );
                }
            } catch (Exception e) {
                log.warn("Failed to decode SBPS parameter pair: {}", pair, e);
            }
        }
        return params;
    }

    @GetMapping("/complete")
    public String paymentComplete() {
        return "redirect:" + walletServicePublicUrl + "/dashboard";
    }

    @GetMapping("/cancel")
    public String paymentCancel() {
        return "redirect:" + walletServicePublicUrl + "/deposit";
    }

    @GetMapping("/error")
    public String paymentError() {
        return "redirect:" + walletServicePublicUrl + "/deposit";
    }
}
