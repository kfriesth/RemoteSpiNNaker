package uk.ac.manchester.cs.spinnaker.rest.spring;

import java.net.URL;

import org.pac4j.core.profile.UserProfile;
import org.pac4j.oidc.profile.OidcProfile;
import org.pac4j.springframework.security.authentication.ClientAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import uk.ac.manchester.cs.spinnaker.rest.RestClientUtils;

public class SpringRestClientUtils {

    public static <T> T createOIDCClient(URL url, Class<T> clazz) {
        Authentication auth =
            SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ClientAuthenticationToken) {
            ClientAuthenticationToken clientAuth =
                (ClientAuthenticationToken) auth;
            UserProfile profile = clientAuth.getUserProfile();
            if (profile instanceof OidcProfile) {
                OidcProfile oidcProfile = (OidcProfile) profile;
                return RestClientUtils.createBearerClient(
                    url, oidcProfile.getAccessToken().getValue(), clazz);
            }
        }
        throw new RuntimeException("Current Authentication is not OIDC");
    }

}
