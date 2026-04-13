package org.side_project.wallet_system.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerIT {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;

    @Test
    void loginPage_returnsOk() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void registerPage_returnsOk() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    @Test
    void login_validCredentials_redirectsToDashboardAndSetsSession() throws Exception {
        Member member = new Member();
        member.setId(UUID.randomUUID());
        member.setName("Alice");
        given(authService.login("alice@test.com", "pass")).willReturn(Optional.of(member));

        MvcResult result = mockMvc.perform(post("/login")
                        .param("email", "alice@test.com")
                        .param("password", "pass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();

        assertThat(result.getRequest().getSession().getAttribute("memberId"))
                .isEqualTo(member.getId().toString());
        assertThat(result.getRequest().getSession().getAttribute("memberName"))
                .isEqualTo("Alice");
    }

    @Test
    void login_invalidCredentials_redirectsBackWithError() throws Exception {
        given(authService.login(any(), any())).willReturn(Optional.empty());

        mockMvc.perform(post("/login")
                        .param("email", "nobody@test.com")
                        .param("password", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("error", "Email 或密碼錯誤"));
    }

    @Test
    void register_success_redirectsToLoginWithSuccess() throws Exception {
        Member member = new Member();
        member.setId(UUID.randomUUID());
        given(authService.register(any(), anyInt(), any(), any())).willReturn(member);

        mockMvc.perform(post("/register")
                        .param("name", "Bob")
                        .param("age", "28")
                        .param("email", "bob@test.com")
                        .param("password", "pass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("success", "註冊成功，請登入"));
    }

    @Test
    void register_duplicateEmail_redirectsBackWithError() throws Exception {
        given(authService.register(any(), anyInt(), any(), any()))
                .willThrow(new IllegalArgumentException("此 Email 已被註冊"));

        mockMvc.perform(post("/register")
                        .param("name", "Bob")
                        .param("age", "28")
                        .param("email", "dup@test.com")
                        .param("password", "pass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attribute("error", "此 Email 已被註冊"));
    }

    @Test
    void logout_invalidatesSessionAndRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
