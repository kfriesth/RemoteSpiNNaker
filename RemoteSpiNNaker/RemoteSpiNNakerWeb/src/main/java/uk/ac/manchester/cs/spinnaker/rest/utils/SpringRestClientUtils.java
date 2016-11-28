package uk.ac.manchester.cs.spinnaker.rest.utils;

import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static uk.ac.manchester.cs.spinnaker.rest.utils.RestClientUtils.createBearerClient;

import java.net.URL;

import org.pac4j.oidc.profile.OidcProfile;
import org.pac4j.springframework.security.authentication.ClientAuthenticationToken;

public class SpringRestClientUtils {
	public static <T> T createOIDCClient(URL url, Class<T> clazz) {
		try {
			ClientAuthenticationToken clientAuth = (ClientAuthenticationToken) getContext()
					.getAuthentication();
			OidcProfile oidcProfile = (OidcProfile) clientAuth.getUserProfile();
			return createBearerClient(url, oidcProfile.getIdTokenString(),
					clazz);
		} catch (ClassCastException e) {
			throw new RuntimeException("Current Authentication is not OIDC");
		}
	}
}
