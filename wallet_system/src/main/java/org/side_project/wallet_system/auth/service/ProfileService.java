package org.side_project.wallet_system.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.side_project.wallet_system.auth.objects.Member;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final long MAX_AVATAR_SIZE_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final MemberRepository memberRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    public Member getMember(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("error.member.not.found"));
    }

    @Transactional
    public void updateProfile(UUID memberId,
                              String name,
                              String nickname,
                              String phone,
                              String bio,
                              LocalDate birthday) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("error.member.not.found"));
        member.setName(name);
        member.setNickname(nickname);
        member.setPhone(phone);
        member.setBio(bio);
        member.setBirthday(birthday);
        memberRepository.save(member);
        log.info("Profile updated: memberId={}", memberId);
    }

    @Transactional
    public void updateAvatar(UUID memberId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("error.avatar.empty");
        }
        String contentType = file.getContentType();
        if (!isAllowedImageType(contentType)) {
            throw new IllegalArgumentException("error.avatar.type");
        }
        if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new IllegalArgumentException("error.avatar.size");
        }
        String originalFilename = StringUtils.cleanPath(
                Objects.requireNonNull(file.getOriginalFilename(), "Filename must not be null"));
        String ext = StringUtils.getFilenameExtension(originalFilename);
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new IllegalArgumentException("error.avatar.type");
        }

        String safeExt = ext.toLowerCase();
        String filename = memberId.toString() + "." + safeExt;
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("avatars");
        Files.createDirectories(uploadPath);

        // Delete any previous avatar file for this member (handles extension changes)
        try (Stream<Path> existing = Files.list(uploadPath)) {
            existing.filter(p -> p.getFileName().toString().startsWith(memberId.toString() + "."))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException e) {
                            log.warn("Failed to delete old avatar file: {}", p, e);
                        }
                    });
        }

        Path dest = uploadPath.resolve(filename);
        if (!dest.normalize().startsWith(uploadPath)) {
            throw new IllegalArgumentException("error.avatar.type");
        }
        file.transferTo(dest);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("error.member.not.found"));
        member.setAvatarPath("avatars/" + filename);
        memberRepository.save(member);
        log.info("Avatar updated: memberId={}, filename={}", memberId, filename);
    }

    private boolean isAllowedImageType(String contentType) {
        if (contentType == null) return false;
        return switch (contentType) {
            case "image/jpeg", "image/png", "image/gif", "image/webp" -> true;
            default -> false;
        };
    }
}
