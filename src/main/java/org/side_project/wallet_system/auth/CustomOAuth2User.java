package org.side_project.wallet_system.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class CustomOAuth2User implements OidcUser {

    private final OidcUser delegate;
    private final UUID memberId;
    private final String memberName;

    public CustomOAuth2User(OidcUser delegate, UUID memberId, String memberName) {
        this.delegate = delegate;
        this.memberId = memberId;
        this.memberName = memberName;
    }

    public UUID getMemberId()     { return memberId; }
    public String getMemberName() { return memberName; }

    @Override public Map<String, Object> getClaims()                          { return delegate.getClaims(); }
    @Override public OidcUserInfo getUserInfo()                               { return delegate.getUserInfo(); }
    @Override public OidcIdToken getIdToken()                                 { return delegate.getIdToken(); }
    @Override public Map<String, Object> getAttributes()                      { return delegate.getAttributes(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities()  { return delegate.getAuthorities(); }
    @Override public String getName()                                          { return delegate.getName(); }
}
