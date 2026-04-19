package org.side_project.wallet_system.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.side_project.wallet_system.auth.controller.ProfileController;
import org.side_project.wallet_system.auth.oauth.CustomOAuth2UserService;
import org.side_project.wallet_system.auth.oauth.LoginSuccessHandler;
import org.side_project.wallet_system.auth.objects.Member;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.auth.service.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.side_project.wallet_system.config.SecurityConfig;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
class ProfileControllerIT {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProfileService profileService;
    @MockitoBean
    MemberRepository memberRepository;
    @MockitoBean
    CustomOAuth2UserService oauth2UserService;
    @MockitoBean
    LoginSuccessHandler loginSuccessHandler;

    private MockHttpSession session;
    private UUID memberId;
    private Member member;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();

        member = new Member();
        member.setId(memberId);
        member.setName("Test User");
        member.setEmail("test@example.com");

        session = new MockHttpSession();
        session.setAttribute("memberId", memberId.toString());
        session.setAttribute("memberName", "Test User");

        given(profileService.getMember(memberId)).willReturn(member);
    }

    // ── GET /profile ──────────────────────────────────────────

    @Test
    void profilePage_withoutSession_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void profilePage_withSession_returnsOkAndProfileView() throws Exception {
        mockMvc.perform(get("/profile")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attributeExists("member"));
    }

    @Test
    void profilePage_withSession_memberHasEmail() throws Exception {
        mockMvc.perform(get("/profile")
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(model().attribute("member", member));
    }

    // ── POST /profile ─────────────────────────────────────────

    @Test
    void updateProfile_validData_redirectsWithSuccess() throws Exception {
        mockMvc.perform(post("/profile").with(csrf()).with(user("test@example.com"))
                        .param("name", "New Name")
                        .param("nickname", "NickN")
                        .param("phone", "0912345678")
                        .param("bio", "Hello")
                        .param("birthday", "1990-05-20")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("success", "Profile updated successfully"));
    }

    @Test
    void updateProfile_onlyName_nullOptionalFields() throws Exception {
        mockMvc.perform(post("/profile").with(csrf()).with(user("test@example.com"))
                        .param("name", "Name Only")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("success", "Profile updated successfully"));
    }

    @Test
    void updateProfile_updatesSessionMemberName() throws Exception {
        mockMvc.perform(post("/profile").with(csrf()).with(user("test@example.com"))
                        .param("name", "Changed Name")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andDo(result ->
                        assertThat(session.getAttribute("memberName")).isEqualTo("Changed Name"));
    }

    @Test
    void updateProfile_serviceThrows_redirectsWithError() throws Exception {
        willThrow(new IllegalArgumentException("error.member.not.found"))
                .given(profileService).updateProfile(any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/profile").with(csrf()).with(user("test@example.com"))
                        .param("name", "Name")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("error", "Member not found"));
    }

    @Test
    void updateProfile_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/profile").with(user("test@example.com"))
                        .param("name", "Name")
                        .session(session))
                .andExpect(status().isForbidden());
    }

    // ── POST /profile/avatar ──────────────────────────────────

    @Test
    void updateAvatar_validFile_redirectsWithSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "photo.jpg", "image/jpeg", new byte[100]);

        mockMvc.perform(multipart("/profile/avatar").file(file)
                        .with(csrf()).with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("success", "Profile picture updated successfully"));
    }

    @Test
    void updateAvatar_emptyFile_redirectsWithError() throws Exception {
        willThrow(new IllegalArgumentException("error.avatar.empty"))
                .given(profileService).updateAvatar(any(), any());

        MockMultipartFile file = new MockMultipartFile(
                "avatar", "photo.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/profile/avatar").file(file)
                        .with(csrf()).with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("error", "Please select a file to upload"));
    }

    @Test
    void updateAvatar_serviceThrowsType_redirectsWithError() throws Exception {
        willThrow(new IllegalArgumentException("error.avatar.type"))
                .given(profileService).updateAvatar(any(), any());

        MockMultipartFile file = new MockMultipartFile(
                "avatar", "doc.pdf", "application/pdf", new byte[100]);

        mockMvc.perform(multipart("/profile/avatar").file(file)
                        .with(csrf()).with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("error",
                        "Only image files are allowed (JPG, PNG, GIF, WEBP)"));
    }

    @Test
    void updateAvatar_serviceThrowsSize_redirectsWithError() throws Exception {
        willThrow(new IllegalArgumentException("error.avatar.size"))
                .given(profileService).updateAvatar(any(), any());

        MockMultipartFile file = new MockMultipartFile(
                "avatar", "big.jpg", "image/jpeg", new byte[100]);

        mockMvc.perform(multipart("/profile/avatar").file(file)
                        .with(csrf()).with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("error", "File size must not exceed 2 MB"));
    }

    @Test
    void updateAvatar_ioException_redirectsWithGenericError() throws Exception {
        willThrow(new java.io.IOException("disk full"))
                .given(profileService).updateAvatar(any(), any());

        MockMultipartFile file = new MockMultipartFile(
                "avatar", "photo.jpg", "image/jpeg", new byte[100]);

        mockMvc.perform(multipart("/profile/avatar").file(file)
                        .with(csrf()).with(user("test@example.com"))
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("error",
                        "Failed to upload avatar. Please try again."));
    }

    @Test
    void updateAvatar_withoutCsrf_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "photo.jpg", "image/jpeg", new byte[100]);

        mockMvc.perform(multipart("/profile/avatar").file(file)
                        .with(user("test@example.com"))
                        .session(session))
                .andExpect(status().isForbidden());
    }
}
