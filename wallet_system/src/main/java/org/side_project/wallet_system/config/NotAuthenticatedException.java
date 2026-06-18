package org.side_project.wallet_system.config;

/**
 * Raised when a request reaches a controller that requires a fully-authenticated member
 * (session {@link SessionConstants#MEMBER_ID} present) but none is available.
 * Handled by {@code GlobalExceptionHandler} with a redirect to {@code /login}.
 */
public class NotAuthenticatedException extends RuntimeException {
}