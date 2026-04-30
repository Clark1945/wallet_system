package org.side_project.payment_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @Value("${wallet.service.public-url}")
    private String walletServicePublicUrl;

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseBody
    public ResponseEntity<Void> handleNoResource() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e) {
        log.error("Unhandled exception in payment-service", e);
        return "redirect:" + walletServicePublicUrl + "/deposit";
    }
}
