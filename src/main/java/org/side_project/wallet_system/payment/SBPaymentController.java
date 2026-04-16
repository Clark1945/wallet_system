package org.side_project.wallet_system.payment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/payment/sbpayment")
@RequiredArgsConstructor
public class SBPaymentController {

    private final SBPaymentService sbPaymentService;
    private final MessageSource messageSource;

    /**
     * Builds the SBPS purchase request form and renders an auto-submit page.
     * Requires the user to be authenticated (session must have memberId).
     */
    @GetMapping("/request")
    public String paymentRequest(HttpSession session, Model model) {
        BigDecimal amount = (BigDecimal) session.getAttribute("sbpaymentPendingAmount");
        if (amount == null) {
            log.warn("No pending SBPS amount in session — redirecting to deposit");
            return "redirect:/deposit";
        }
        UUID memberId = UUID.fromString((String) session.getAttribute("memberId"));
        SBPaymentRequest req = sbPaymentService.buildRequest(memberId, amount);
        model.addAttribute("req", req);
        return "sb-payment";
    }

    /**
     * Result CGI endpoint — called server-to-server by SBPS after payment.
     * Must be publicly accessible (no session, no CSRF).
     * SBPS sends the POST body in Shift-JIS (Windows-31J); we must NOT use
     * @RequestParam here because Tomcat would decode with UTF-8 and throw
     * MalformedInputException on Japanese characters like item_name.
     * Response body: "OK," on success, "NG,<message>" on failure.
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

    /**
     * Parses a URL-encoded form body whose values are Shift-JIS encoded.
     * Reads the raw bytes as ISO-8859-1 (byte-transparent) so that
     * URL-encoded percent sequences (%XX) are preserved intact, then
     * delegates to URLDecoder with the Windows-31J charset.
     */
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
            } catch (Exception ignore) {}
        }
        return params;
    }

    /**
     * Browser redirect from SBPS after successful payment.
     */
    @GetMapping("/complete")
    public String paymentComplete(HttpSession session,
                                  RedirectAttributes redirectAttributes,
                                  Locale locale) {
        session.removeAttribute("sbpaymentPendingAmount");
        redirectAttributes.addFlashAttribute("success",
                messageSource.getMessage("flash.deposit.success", null, locale));
        return "redirect:/dashboard";
    }

    /**
     * Browser redirect from SBPS when user cancels payment.
     */
    @GetMapping("/cancel")
    public String paymentCancel(HttpSession session,
                                RedirectAttributes redirectAttributes,
                                Locale locale) {
        session.removeAttribute("sbpaymentPendingAmount");
        redirectAttributes.addFlashAttribute("error",
                messageSource.getMessage("flash.payment.cancelled", null, locale));
        return "redirect:/deposit";
    }

    /**
     * Browser redirect from SBPS on payment error.
     */
    @GetMapping("/error")
    public String paymentError(HttpSession session,
                               RedirectAttributes redirectAttributes,
                               Locale locale) {
        session.removeAttribute("sbpaymentPendingAmount");
        redirectAttributes.addFlashAttribute("error",
                messageSource.getMessage("flash.payment.error", null, locale));
        return "redirect:/deposit";
    }
}
