package org.side_project.wallet_system.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.auth.objects.Member;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.auth.service.ProfileService;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    MemberRepository memberRepository;

    @InjectMocks
    ProfileService profileService;

    @TempDir
    Path tempDir;

    private UUID memberId;
    private Member member;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        member = new Member();
        member.setId(memberId);
        member.setName("Test User");
        member.setEmail("test@example.com");

        ReflectionTestUtils.setField(profileService, "uploadDir", tempDir.toString());
    }

    // ── getMember ─────────────────────────────────────────────

    @Test
    void getMember_existingMember_returnsMember() {
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        Member result = profileService.getMember(memberId);

        assertThat(result).isEqualTo(member);
    }

    @Test
    void getMember_memberNotFound_throws() {
        given(memberRepository.findById(memberId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getMember(memberId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.member.not.found");
    }

    // ── updateProfile ─────────────────────────────────────────

    @Test
    void updateProfile_allFields_savesCorrectly() {
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        profileService.updateProfile(memberId, "New Name", "NickN", "0912345678",
                "Hello world", LocalDate.of(1990, 5, 20));

        assertThat(member.getName()).isEqualTo("New Name");
        assertThat(member.getNickname()).isEqualTo("NickN");
        assertThat(member.getPhone()).isEqualTo("0912345678");
        assertThat(member.getBio()).isEqualTo("Hello world");
        assertThat(member.getBirthday()).isEqualTo(LocalDate.of(1990, 5, 20));
        verify(memberRepository).save(member);
    }

    @Test
    void updateProfile_nullOptionalFields_savesWithNulls() {
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        profileService.updateProfile(memberId, "Name Only", null, null, null, null);

        assertThat(member.getNickname()).isNull();
        assertThat(member.getPhone()).isNull();
        assertThat(member.getBio()).isNull();
        assertThat(member.getBirthday()).isNull();
        verify(memberRepository).save(member);
    }

    @Test
    void updateProfile_memberNotFound_throws() {
        given(memberRepository.findById(memberId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.updateProfile(
                memberId, "Name", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.member.not.found");
    }

    // ── updateAvatar ──────────────────────────────────────────

    @Test
    void updateAvatar_validJpeg_savesFileAndUpdatesPath() throws IOException {
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        byte[] content = new byte[100];
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "photo.jpg", "image/jpeg", content);

        profileService.updateAvatar(memberId, file);

        Path expected = tempDir.resolve("avatars").resolve(memberId + ".jpg");
        assertThat(Files.exists(expected)).isTrue();
        assertThat(member.getAvatarPath()).isEqualTo("avatars/" + memberId + ".jpg");
        verify(memberRepository).save(member);
    }

    @Test
    void updateAvatar_validPng_savesFileAndUpdatesPath() throws IOException {
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "photo.png", "image/png", new byte[100]);

        profileService.updateAvatar(memberId, file);

        Path expected = tempDir.resolve("avatars").resolve(memberId + ".png");
        assertThat(Files.exists(expected)).isTrue();
        assertThat(member.getAvatarPath()).isEqualTo("avatars/" + memberId + ".png");
    }

    @Test
    void updateAvatar_emptyFile_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "photo.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> profileService.updateAvatar(memberId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.avatar.empty");
    }

    @Test
    void updateAvatar_nullContentType_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "photo.jpg", null, new byte[100]);

        assertThatThrownBy(() -> profileService.updateAvatar(memberId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.avatar.type");
    }

    @Test
    void updateAvatar_nonImageType_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "doc.pdf", "application/pdf", new byte[100]);

        assertThatThrownBy(() -> profileService.updateAvatar(memberId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.avatar.type");
    }

    @Test
    void updateAvatar_disallowedExtension_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "exploit.php", "image/jpeg", new byte[100]);

        assertThatThrownBy(() -> profileService.updateAvatar(memberId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.avatar.type");
    }

    @Test
    void updateAvatar_fileTooLarge_throws() {
        byte[] bigContent = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "big.jpg", "image/jpeg", bigContent);

        assertThatThrownBy(() -> profileService.updateAvatar(memberId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.avatar.size");
    }

    @Test
    void updateAvatar_exactlyAtSizeLimit_succeeds() throws IOException {
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        byte[] exactContent = new byte[2 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "exact.jpg", "image/jpeg", exactContent);

        profileService.updateAvatar(memberId, file);

        assertThat(member.getAvatarPath()).isEqualTo("avatars/" + memberId + ".jpg");
    }

    @Test
    void updateAvatar_overwritesPreviousAvatar() throws IOException {
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        // Upload PNG first
        MockMultipartFile png = new MockMultipartFile(
                "avatar", "photo.png", "image/png", new byte[100]);
        profileService.updateAvatar(memberId, png);
        Path pngFile = tempDir.resolve("avatars").resolve(memberId + ".png");
        assertThat(Files.exists(pngFile)).isTrue();

        // Re-stub for second call
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        // Upload JPEG — old PNG should be deleted
        MockMultipartFile jpg = new MockMultipartFile(
                "avatar", "photo.jpg", "image/jpeg", new byte[100]);
        profileService.updateAvatar(memberId, jpg);

        Path jpgFile = tempDir.resolve("avatars").resolve(memberId + ".jpg");
        assertThat(Files.exists(jpgFile)).isTrue();
        assertThat(Files.exists(pngFile)).isFalse();
        assertThat(member.getAvatarPath()).isEqualTo("avatars/" + memberId + ".jpg");
    }

    @Test
    void updateAvatar_memberNotFound_throws() {
        given(memberRepository.findById(any())).willReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "photo.jpg", "image/jpeg", new byte[100]);

        assertThatThrownBy(() -> profileService.updateAvatar(memberId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.member.not.found");
    }
}
