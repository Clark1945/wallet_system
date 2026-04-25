package org.side_project.payment_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @Value("${wallet.service.public-url}")
    private String walletServicePublicUrl;

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e) {
        log.error("Unhandled exception in payment-service", e);
        return "redirect:" + walletServicePublicUrl + "/deposit";
    }
}
