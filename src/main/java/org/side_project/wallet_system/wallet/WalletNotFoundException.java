package org.side_project.wallet_system.wallet;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(UUID memberId) {
        super("Wallet not found for memberId: " + memberId);
    }
}
