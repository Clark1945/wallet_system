package org.side_project.wallet_system.auth.oauth;

import org.side_project.wallet_system.auth.objects.Member;
import org.side_project.wallet_system.auth.objects.MemberStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class CustomUserDetails implements UserDetails {

    private final Member member;

    public CustomUserDetails(Member member) {
        this.member = member;
    }

    public UUID getMemberId()    { return member.getId(); }
    public String getMemberName() { return member.getName(); }

    @Override public String getUsername()  { return member.getEmail(); }
    @Override public String getPassword()  { return member.getPassword(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return member.getStatus() == MemberStatus.ACTIVE; }
}
