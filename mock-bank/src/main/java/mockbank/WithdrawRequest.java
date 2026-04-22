package mockbank;

public record WithdrawRequest(
        String transactionId,
        String amount,
        String bankCode,
        String bankAccount,
        String callbackUrl
) {}
