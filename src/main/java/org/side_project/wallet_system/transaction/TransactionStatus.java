package org.side_project.wallet_system.transaction;

public enum TransactionStatus {
    PENDING,            // 儲值發起，等待支付確認
    REQUEST_COMPLETED,  // 提款請求已送出銀行，等待確認回調
    COMPLETED,          // 最終成功
    FAILED              // 最終失敗
}
