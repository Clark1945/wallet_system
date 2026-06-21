package org.side_project.wallet_system.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.notification.AuditLogPublisher;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogPublisher auditLogPublisher;
    @InjectMocks private AuditService auditService;

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void record_enrichesFromRequestContextAndPublishes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        request.addHeader("User-Agent", "JUnit-UA");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put("traceId", "trace-123");

        auditService.record(AuditLog.builder()
                .action(AuditAction.LOGIN_SUCCESS).result(AuditResult.SUCCESS).build());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPublisher).publish(captor.capture());
        AuditLog published = captor.getValue();
        assertThat(published.getIpAddress()).isEqualTo("203.0.113.7");   // first hop of X-Forwarded-For
        assertThat(published.getUserAgent()).isEqualTo("JUnit-UA");
        assertThat(published.getTraceId()).isEqualTo("trace-123");
    }

    @Test
    void record_fallsBackToRemoteAddrWhenNoForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.42");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.record(AuditLog.builder()
                .action(AuditAction.WITHDRAWAL_INITIATED).result(AuditResult.SUCCESS).build());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPublisher).publish(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("198.51.100.42");
    }

    @Test
    void record_truncatesOverlongUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "x".repeat(500));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.record(AuditLog.builder()
                .action(AuditAction.LOGIN_FAILURE).result(AuditResult.FAILURE).build());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPublisher).publish(captor.capture());
        assertThat(captor.getValue().getUserAgent()).hasSize(255);
    }

    @Test
    void record_withNoRequestContext_publishesWithoutContextFields() {
        AuditLog entry = AuditLog.builder()
                .action(AuditAction.TRANSFER).result(AuditResult.SUCCESS).build();

        auditService.record(entry);

        verify(auditLogPublisher).publish(entry);
        assertThat(entry.getIpAddress()).isNull();
        assertThat(entry.getUserAgent()).isNull();
    }

    @Test
    void record_swallowsPublisherFailure_neverThrows() {
        willThrow(new RuntimeException("broker down")).given(auditLogPublisher).publish(any());

        assertThatNoException().isThrownBy(() -> auditService.record(AuditLog.builder()
                .action(AuditAction.DEPOSIT_COMPLETED).result(AuditResult.SUCCESS).build()));
    }

    @Test
    void record_nullEntry_isNoOp() {
        auditService.record(null);
        verifyNoInteractions(auditLogPublisher);
    }

    @Test
    void record_doesNotOverwritePresetContextFields() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.1.1.1");
        request.addHeader("User-Agent", "request-ua");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put("traceId", "request-trace");

        AuditLog entry = AuditLog.builder()
                .action(AuditAction.LOGIN_SUCCESS).result(AuditResult.SUCCESS)
                .ipAddress("9.9.9.9").userAgent("preset-ua").traceId("preset-trace")
                .build();

        auditService.record(entry);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPublisher).publish(captor.capture());
        AuditLog published = captor.getValue();
        assertThat(published.getIpAddress()).isEqualTo("9.9.9.9");      // preset kept
        assertThat(published.getUserAgent()).isEqualTo("preset-ua");    // preset kept
        assertThat(published.getTraceId()).isEqualTo("preset-trace");   // preset kept
    }

    @Test
    void record_keepsShortUserAgentUntruncated() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "short-ua");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        AuditLog entry = AuditLog.builder()
                .action(AuditAction.TRANSFER).result(AuditResult.SUCCESS).build();
        auditService.record(entry);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPublisher).publish(captor.capture());
        assertThat(captor.getValue().getUserAgent()).isEqualTo("short-ua");
    }
}
