package org.side_project.email_service.email;

import java.util.Map;

public record EmailMessage(String type, String to, Map<String, String> params) {

    public static final String REGISTRATION_OTP   = "REGISTRATION_OTP";
    public static final String LOGIN_OTP          = "LOGIN_OTP";
    public static final String PASSWORD_RESET     = "PASSWORD_RESET";
    public static final String DEPOSIT_SUCCESS    = "DEPOSIT_SUCCESS";
    public static final String WITHDRAWAL_SUCCESS = "WITHDRAWAL_SUCCESS";
}
