package org.side_project.wallet_system.auth.repository;

import org.side_project.wallet_system.auth.objects.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {
    Optional<Member> findByEmail(String email);
    Optional<Member> findByGoogleId(String googleId);
    boolean existsByEmail(String email);
}
