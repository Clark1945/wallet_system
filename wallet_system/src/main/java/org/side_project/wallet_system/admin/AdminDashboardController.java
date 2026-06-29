package org.side_project.wallet_system.admin;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.side_project.wallet_system.admin.dto.AdminStats;
import org.side_project.wallet_system.admin.dto.AdminTxView;
import org.side_project.wallet_system.config.SessionConstants;
import org.side_project.wallet_system.config.SessionUtils;
import org.side_project.wallet_system.transaction.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * Admin金流控管 dashboard. Access is gated by {@code AdminAuthInterceptor} (session
 * {@link SessionConstants#IS_ADMIN}); only admins are routed here at login.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private static final int PAGE_SIZE = 10;

    private final AdminService adminService;

    @GetMapping
    public String dashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpSession session, Model model) {

        TransactionStatus txStatus = (status != null && !status.isBlank())
                ? TransactionStatus.valueOf(status) : null;

        AdminStats stats = adminService.getStats();
        Page<AdminTxView> txPage =
                adminService.queryTransactions(txStatus, orderNo, startDate, endDate, page, PAGE_SIZE);

        model.addAttribute("stats", stats);
        model.addAttribute("txPage", txPage);
        model.addAttribute("statuses", TransactionStatus.values());
        model.addAttribute(SessionConstants.MEMBER_NAME, SessionUtils.getMemberName(session));
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterOrderNo", orderNo);
        model.addAttribute("filterStart", startDate);
        model.addAttribute("filterEnd", endDate);
        return "admin-dashboard";
    }
}
