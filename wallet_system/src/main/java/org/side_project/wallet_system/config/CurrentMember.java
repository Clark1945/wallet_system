package org.side_project.wallet_system.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the fully-authenticated member's UUID from the session.
 *
 * <p>"Fully authenticated" means the session carries {@link SessionConstants#MEMBER_ID},
 * which is only set after the login OTP step completes (see {@code AuthFlowService.verifyLoginOtp}
 * and {@code LoginSuccessHandler} for the Google flow). A password-only request that has not
 * cleared OTP is authenticated to Spring Security but has no session member id; resolving this
 * parameter then throws {@link NotAuthenticatedException}, redirecting the user to {@code /login}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentMember {
}