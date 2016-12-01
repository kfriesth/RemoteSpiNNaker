package uk.ac.manchester.cs.spinnaker.remote.web;

import static com.nimbusds.jose.JWSAlgorithm.RS256;

import org.pac4j.oidc.client.OidcClient;
import org.springframework.beans.factory.annotation.Value;

class BasicOidcClient extends OidcClient {
	@Override
	@Value("${oidc.clientId}")
	public void setClientID(String clientId) {
		super.setClientID(clientId);
	}

	@Override
	@Value("${oidc.secret}")
	public void setSecret(String secret) {
		super.setSecret(secret);
	}

	@Override
	@Value("${oidc.discovery.uri}")
	public void setDiscoveryURI(String oidcDiscoveryUri) {
		super.setDiscoveryURI(oidcDiscoveryUri);
	}

	BasicOidcClient() {
		setScope("openid profile hbp.collab");
		setPreferredJwsAlgorithm(RS256);
	}
}
