package org.side_project.wallet_system.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentMemberArgumentResolverTest {

    private final CurrentMemberArgumentResolver resolver = new CurrentMemberArgumentResolver();

    // ── fixtures: methods whose parameters drive supportsParameter() ──
    @SuppressWarnings("unused")
    static class Fixture {
        void annotatedUuid(@CurrentMember UUID memberId) {}
        void annotatedString(@CurrentMember String notUuid) {}
        void plainUuid(UUID notAnnotated) {}
    }

    private MethodParameter param(String method, Class<?>... types) throws NoSuchMethodException {
        Method m = Fixture.class.getDeclaredMethod(method, types);
        return new MethodParameter(m, 0);
    }

    // ── supportsParameter ─────────────────────────────────────────────

    @Test
    void supports_annotatedUuid_isTrue() throws Exception {
        assertThat(resolver.supportsParameter(param("annotatedUuid", UUID.class))).isTrue();
    }

    @Test
    void supports_annotatedButWrongType_isFalse() throws Exception {
        assertThat(resolver.supportsParameter(param("annotatedString", String.class))).isFalse();
    }

    @Test
    void supports_uuidWithoutAnnotation_isFalse() throws Exception {
        assertThat(resolver.supportsParameter(param("plainUuid", UUID.class))).isFalse();
    }

    // ── resolveArgument ───────────────────────────────────────────────

    @Test
    void resolve_withSessionMemberId_returnsUuid() throws Exception {
        UUID memberId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute(SessionConstants.MEMBER_ID, memberId.toString());
        request.setSession(httpSession);

        Object result = resolver.resolveArgument(
                param("annotatedUuid", UUID.class), null, new ServletWebRequest(request), null);

        assertThat(result).isEqualTo(memberId);
    }

    @Test
    void resolve_noSession_throwsNotAuthenticated() throws Exception {
        // no session created on the request → password-only / expired-session case
        NativeWebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest());

        assertThatThrownBy(() -> resolver.resolveArgument(
                param("annotatedUuid", UUID.class), null, webRequest, null))
                .isInstanceOf(NotAuthenticatedException.class);
    }

    @Test
    void resolve_sessionWithoutMemberId_throwsNotAuthenticated() throws Exception {
        // authenticated to Spring Security but OTP step not completed → no member id in session
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        assertThatThrownBy(() -> resolver.resolveArgument(
                param("annotatedUuid", UUID.class), null, new ServletWebRequest(request), null))
                .isInstanceOf(NotAuthenticatedException.class);
    }
}