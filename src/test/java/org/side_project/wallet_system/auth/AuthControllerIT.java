package org.side_project.wallet_system.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.side_project.wallet_system.config.SecurityConfig;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerIT {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean CustomOAuth2UserService oauth2UserService;
    @MockitoBean LoginSuccessHandler loginSuccessHandler;

    @Test
    void loginPage_returnsOk() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void loginPage_withErrorParam_showsErrorMessage() throws Exception {
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void registerPage_returnsOk() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    @Test
    void register_success_redirectsToLoginWithSuccess() throws Exception {
        Member member = new Member();
        member.setId(java.util.UUID.randomUUID());
        given(authService.register(any(), anyInt(), any(), any())).willReturn(member);

        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "Bob")
                        .param("age", "28")
                        .param("email", "bob@test.com")
                        .param("password", "pass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("success", "Registration successful. Please log in."));
    }

    @Test
    void register_duplicateEmail_redirectsBackWithError() throws Exception {
        given(authService.register(any(), anyInt(), any(), any()))
                .willThrow(new IllegalArgumentException("error.email.duplicate"));

        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "Bob")
                        .param("age", "28")
                        .param("email", "dup@test.com")
                        .param("password", "pass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attribute("error", "This email is already registered"));
    }
}
