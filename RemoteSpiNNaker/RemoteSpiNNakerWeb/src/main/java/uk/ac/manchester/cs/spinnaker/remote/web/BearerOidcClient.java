package uk.ac.manchester.cs.spinnaker.remote.web;

import static com.nimbusds.openid.connect.sdk.UserInfoResponse.parse;
import static com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata.parse;
import static org.pac4j.core.client.ClientType.HEADER_BASED;
import static org.pac4j.core.context.HttpConstants.AUTHORIZATION_HEADER;
import static org.pac4j.core.context.HttpConstants.DEFAULT_CONNECT_TIMEOUT;
import static org.pac4j.core.context.HttpConstants.DEFAULT_READ_TIMEOUT;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.pac4j.core.client.BaseClient;
import org.pac4j.core.client.ClientType;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.RequiresHttpAction;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.oidc.profile.OidcProfile;

import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.http.DefaultResourceRetriever;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.UserInfoErrorResponse;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.UserInfoSuccessResponse;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;

public class BearerOidcClient extends
		DirectClient<BearerCredentials, OidcProfile> {
	private static final String BEARER_PREFIX = "Bearer ";

	private final String discoveryURI;
	private OIDCProviderMetadata oidcProvider;
	private final String realmName;

	public BearerOidcClient(String discoveryURI, String realmName)
			throws ParseException, MalformedURLException, IOException {
		this.discoveryURI = discoveryURI;

		try {
			oidcProvider = parse(new DefaultResourceRetriever(
					DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT)
					.retrieveResource(new URL(discoveryURI)).getContent());
		} catch (Exception e) {
			logger.error(
					"Could not contact OIDC provider - Bearer authentication"
							+ " will not work", e);
		}

		this.realmName = realmName;
	}

	@Override
	protected void internalInit(WebContext webContext) {
		// Does Nothing
	}

	@Override
	public BearerCredentials getCredentials(WebContext context)
			throws RequiresHttpAction {
		String authorization = context.getRequestHeader(AUTHORIZATION_HEADER);
		if (authorization == null || !authorization.startsWith(BEARER_PREFIX))
			return null;

		// Verify the access token
		String accessToken = authorization.substring(BEARER_PREFIX.length());
		if (accessToken.trim().isEmpty())
			return null;
		try {
			BearerAccessToken token = new BearerAccessToken(accessToken);
			if (oidcProvider.getUserInfoEndpointURI() == null) {
				logger.error("No User Info Endpoint!");
				return null;
			}

			return convertTokenToCredentials(context, accessToken, token);
		} catch (Exception e) {
			throw new TechnicalException(e);
		}
	}

	private BearerCredentials convertTokenToCredentials(WebContext context,
			String accessToken, BearerAccessToken token) throws IOException,
			ParseException, RequiresHttpAction {
		UserInfoRequest userInfoRequest = new UserInfoRequest(
				oidcProvider.getUserInfoEndpointURI(), token);
		HTTPRequest userInfoHttpRequest = userInfoRequest.toHTTPRequest();
		userInfoHttpRequest.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
		userInfoHttpRequest.setReadTimeout(DEFAULT_READ_TIMEOUT);
		HTTPResponse httpResponse = userInfoHttpRequest.send();
		logger.debug("Token response: status={}, content={}",
				httpResponse.getStatusCode(), httpResponse.getContent());

		UserInfoResponse userInfoResponse = parse(httpResponse);

		if (userInfoResponse instanceof UserInfoErrorResponse) {
			logger.error("Bad User Info response, error={}",
					((UserInfoErrorResponse) userInfoResponse).getErrorObject());
			throw RequiresHttpAction.unauthorized("", context, realmName);
		}

		OidcProfile profile = new OidcProfile(token);
		UserInfoSuccessResponse userInfoSuccessResponse = (UserInfoSuccessResponse) userInfoResponse;
		UserInfo userInfo = userInfoSuccessResponse.getUserInfo();
		if (userInfo != null)
			profile.addAttributes(userInfo.toJWTClaimsSet().getClaims());

		return new BearerCredentials(accessToken, profile);
	}

	@Override
	protected OidcProfile retrieveUserProfile(BearerCredentials credentials,
			WebContext context) {
		return credentials.getProfile();
	}

	@Override
	protected BaseClient<BearerCredentials, OidcProfile> newClient() {
		try {
			return new BearerOidcClient(discoveryURI, realmName);
		} catch (ParseException | IOException e) {
			throw new TechnicalException(e);
		}
	}

	@Override
	public ClientType getClientType() {
		return HEADER_BASED;
	}
}
