package uk.ac.manchester.cs.spinnaker.remote.webutils;

import org.pac4j.core.credentials.Credentials;
import org.pac4j.oidc.profile.OidcProfile;

public class BearerCredentials extends Credentials {

    private static final long serialVersionUID = 1L;

    private String accessToken = null;

    private OidcProfile profile = null;

    public BearerCredentials(String accessToken, OidcProfile profile) {
        this.accessToken = accessToken;
        this.profile = profile;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public OidcProfile getProfile() {
        return profile;
    }

    @Override
    public void clear() {
        accessToken = null;
        profile = null;
    }

}
