package org.side_project.wallet_system.wallet;

public class MockBankUnavailableException extends RuntimeException {
    public MockBankUnavailableException(String message) {
        super(message);
    }
}
