package org.side_project.wallet_system.wallet;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.side_project.wallet_system.config.RateLimiterService;
import org.side_project.wallet_system.config.SessionConstants;
import org.side_project.wallet_system.config.SessionUtils;
import org.side_project.wallet_system.internal.PaymentTokenService;
import org.side_project.wallet_system.transaction.Transaction;
import org.side_project.wallet_system.transaction.TransactionType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final MessageSource messageSource;
    private final RateLimiterService rateLimiterService;
    private final PaymentTokenService paymentTokenService;

    @Value("${payment.service.base-url}")
    private String paymentServiceBaseUrl;

    @GetMapping("/dashboard")
    public String dashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpSession session, Model model) {

        UUID memberId = SessionUtils.getMemberId(session);
        if (memberId == null) return "redirect:/login";

        Wallet wallet = walletService.getWallet(memberId);
        TransactionType txType = (type != null && !type.isBlank()) ? TransactionType.valueOf(type) : null;
        Page<Transaction> txPage = walletService.getTransactions(memberId, txType, startDate, endDate, page, 10);

        model.addAttribute("wallet", wallet);
        model.addAttribute("txPage", txPage);
        model.addAttribute(SessionConstants.MEMBER_NAME, SessionUtils.getMemberName(session));
        model.addAttribute("filterType", type);
        model.addAttribute("filterStart", startDate);
        model.addAttribute("filterEnd", endDate);
        return "dashboard";
    }

    @GetMapping("/deposit")
    public String depositPage(HttpSession session, Model model) {
        UUID memberId = SessionUtils.getMemberId(session);
        if (memberId == null) return "redirect:/login";

        model.addAttribute("wallet", walletService.getWallet(memberId));
        model.addAttribute(SessionConstants.MEMBER_NAME, SessionUtils.getMemberName(session));
        return "deposit";
    }

    @PostMapping("/deposit")
    public String deposit(@RequestParam BigDecimal amount,
                          @RequestParam(defaultValue = "stripe") String paymentMethod,
                          @RequestParam(required = false) String notifyEmail,
                          HttpSession session,
                          RedirectAttributes redirectAttributes,
                          Locale locale) {
        UUID memberId = SessionUtils.getMemberId(session);
        if (memberId == null) return "redirect:/login";

        if (!rateLimiterService.isAllowed("deposit:" + memberId, 10, Duration.ofMinutes(1))) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.rate.limit", null, locale));
            return "redirect:/deposit";
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.amount.positive", null, locale));
            return "redirect:/deposit";
        }
        if ("sbpayment".equals(paymentMethod)) {
            String token = paymentTokenService.createToken(memberId, amount, "sbpayment", notifyEmail);
            return "redirect:" + paymentServiceBaseUrl + "/payment/sbpayment/request?token=" + token;
        }
        if ("stripe".equals(paymentMethod)) {
            String token = paymentTokenService.createToken(memberId, amount, "stripe", notifyEmail);
            return "redirect:" + paymentServiceBaseUrl + "/payment/stripe/checkout?token=" + token;
        }
        redirectAttributes.addFlashAttribute("error",
                messageSource.getMessage("error.payment.unknown", null, "Unknown payment method", locale));
        return "redirect:/deposit";
    }

    @GetMapping("/withdraw")
    public String withdrawPage(HttpSession session, Model model) {
        UUID memberId = SessionUtils.getMemberId(session);
        if (memberId == null) return "redirect:/login";

        model.addAttribute("wallet", walletService.getWallet(memberId));
        model.addAttribute(SessionConstants.MEMBER_NAME, SessionUtils.getMemberName(session));
        return "withdraw";
    }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam BigDecimal amount,
                           @RequestParam String bankCode,
                           @RequestParam String bankAccount,
                           @RequestParam String notifyEmail,
                           HttpSession session,
                           RedirectAttributes redirectAttributes,
                           Locale locale) {
        UUID memberId = SessionUtils.getMemberId(session);
        if (memberId == null) return "redirect:/login";

        if (!rateLimiterService.isAllowed("withdraw:" + memberId, 5, Duration.ofMinutes(1))) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.rate.limit", null, locale));
            return "redirect:/withdraw";
        }

        try {
            walletService.initiateWithdrawal(memberId, amount, bankCode, bankAccount, notifyEmail);
            redirectAttributes.addFlashAttribute("success",
                    messageSource.getMessage("flash.withdraw.pending", null, locale));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/transfer")
    public String transferPage(HttpSession session, Model model) {
        UUID memberId = SessionUtils.getMemberId(session);
        if (memberId == null) return "redirect:/login";

        model.addAttribute("wallet", walletService.getWallet(memberId));
        model.addAttribute(SessionConstants.MEMBER_NAME, SessionUtils.getMemberName(session));
        return "transfer";
    }

    @PostMapping("/transfer")
    public String transfer(@RequestParam String toWalletCode,
                           @RequestParam BigDecimal amount,
                           HttpSession session,
                           RedirectAttributes redirectAttributes,
                           Locale locale) {
        UUID memberId = SessionUtils.getMemberId(session);
        if (memberId == null) return "redirect:/login";

        if (!rateLimiterService.isAllowed("transfer:" + memberId, 10, Duration.ofMinutes(1))) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("error.rate.limit", null, locale));
            return "redirect:/transfer";
        }

        try {
            walletService.transfer(memberId, toWalletCode, amount);
            redirectAttributes.addFlashAttribute("success",
                    messageSource.getMessage("flash.transfer.success", null, locale));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale));
            return "redirect:/transfer";
        }
        return "redirect:/dashboard";
    }
}
