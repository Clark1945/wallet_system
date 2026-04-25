package org.side_project.wallet_system.wallet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class MockBankClientTest {

    @Mock private HttpClient httpClient;
    @InjectMocks private MockBankClient mockBankClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mockBankClient, "mockBankUrl", "http://mock-bank:8081");
    }

    // ── happy path ─────────────────────────────────────────────

    @Test
    void sendWithdrawRequest_postsToCorrectUri() throws Exception {
        HttpResponse response = mock(HttpResponse.class);
        given(httpClient.send(any(HttpRequest.class), any())).willReturn(response);

        mockBankClient.sendWithdrawRequest("tx-001", new BigDecimal("300.00"), "012", "9876543210",
                "http://app:8080/withdraw/webhook", null);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        then(httpClient).should().send(captor.capture(), any());
        HttpRequest sent = captor.getValue();

        assertThat(sent.uri().toString()).isEqualTo("http://mock-bank:8081/api/withdraw");
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(sent.headers().firstValue("X-Trace-Id")).isEmpty();
    }

    @Test
    void sendWithdrawRequest_withTraceId_addsHeader() throws Exception {
        HttpResponse response = mock(HttpResponse.class);
        given(httpClient.send(any(HttpRequest.class), any())).willReturn(response);

        mockBankClient.sendWithdrawRequest("tx-002", new BigDecimal("50.00"), "013", "1111111111",
                "http://app:8080/withdraw/webhook", "trace-abc-123");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        then(httpClient).should().send(captor.capture(), any());
        assertThat(captor.getValue().headers().firstValue("X-Trace-Id")).contains("trace-abc-123");
    }

    // ── error paths ────────────────────────────────────────────

    @Test
    void sendWithdrawRequest_ioException_propagates() throws Exception {
        given(httpClient.send(any(), any())).willThrow(new IOException("connection refused"));

        assertThatThrownBy(() ->
            mockBankClient.sendWithdrawRequest("tx-err", new BigDecimal("100.00"), "012", "123",
                    "http://callback", null))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("connection refused");
    }

    @Test
    void sendWithdrawRequest_interruptedException_propagates() throws Exception {
        given(httpClient.send(any(), any())).willThrow(new InterruptedException("interrupted"));

        assertThatThrownBy(() ->
            mockBankClient.sendWithdrawRequest("tx-int", new BigDecimal("200.00"), "012", "456",
                    "http://callback", null))
            .isInstanceOf(InterruptedException.class);
    }
}
