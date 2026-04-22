package org.side_project.wallet_system.auth.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.auth.objects.Member;
import org.side_project.wallet_system.auth.service.AuthService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends OidcUserService {

    private final AuthService authService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String googleId = oidcUser.getAttribute("sub");
        String email    = oidcUser.getAttribute("email");
        String name     = oidcUser.getAttribute("name");

        log.debug("OIDC user loaded from Google: email={}", email);
        try {
            Member member = authService.findOrCreateGoogleMember(googleId, email, name);
            return new CustomOAuth2User(oidcUser, member.getId(), member.getName());
        } catch (IllegalArgumentException e) {
            throw new OAuth2AuthenticationException(e.getMessage());
        }
    }
}
