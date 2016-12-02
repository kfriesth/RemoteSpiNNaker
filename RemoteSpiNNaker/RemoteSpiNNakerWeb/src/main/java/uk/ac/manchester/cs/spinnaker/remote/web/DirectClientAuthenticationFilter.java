package uk.ac.manchester.cs.spinnaker.remote.web;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.pac4j.core.client.Client;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.exception.RequiresHttpAction;
import org.pac4j.springframework.security.authentication.ClientAuthenticationToken;
import org.pac4j.springframework.security.exception.AuthenticationCredentialsException;
import org.slf4j.Logger;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class DirectClientAuthenticationFilter extends OncePerRequestFilter {
	private static final String MUST_AUTH_HEADER = "WWW-Authenticate";
	private static final String MUST_AUTH_PAYLOAD = "Bearer realm=\"%s\"";
	public static final String DEFAULT_REALM = "SpiNNaker";
	private final Logger logger = getLogger(getClass());

	private Client<?, ?> client;
	private String realmName = DEFAULT_REALM;
	private AuthenticationEntryPoint authenticationEntryPoint;
	private WebAuthenticationDetailsSource detailsSource;
	private AuthenticationManager authenticationManager;

	public DirectClientAuthenticationFilter(
			AuthenticationManager authenticationManager) {
		this.authenticationManager = requireNonNull(authenticationManager);
		detailsSource = new WebAuthenticationDetailsSource();
	}

	@PostConstruct
	void checkForSanity() {
		requireNonNull(client);
		if (authenticationEntryPoint == null)
			authenticationEntryPoint = new AuthenticationEntryPoint() {
				@Override
				public void commence(HttpServletRequest request,
						HttpServletResponse response,
						AuthenticationException authException)
						throws IOException {
					commenceBearerAuth(response, authException);
				}
			};
	}

	private void commenceBearerAuth(HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.addHeader(MUST_AUTH_HEADER,
				format(MUST_AUTH_PAYLOAD, realmName));
		response.sendError(SC_UNAUTHORIZED, authException.getMessage());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		// context
		WebContext context = new J2EContext(request, response);

		// get credentials
		Credentials credentials = null;
		try {
			credentials = client.getCredentials(context);
		} catch (RequiresHttpAction e) {
			logger.info("Requires additionnal HTTP action", e);
		} catch (CredentialsException ce) {
			throw new AuthenticationCredentialsException(
					"Error retrieving credentials", ce);
		}

		logger.debug("credentials : {}", credentials);

		// if credentials/profile is not null, do more
		if (credentials != null)
			authenticateCredentials(request, response, credentials);

		filterChain.doFilter(request, response);
	}

	private void authenticateCredentials(HttpServletRequest request,
			HttpServletResponse response, Credentials credentials)
			throws IOException, ServletException {
		// create token from credential
		ClientAuthenticationToken token = new ClientAuthenticationToken(
				credentials, client.getName());
		token.setDetails(detailsSource.buildDetails(request));

		try {
			// authenticate
			Authentication auth = authenticationManager.authenticate(token);
			logger.debug("authentication: {}", auth);
			SecurityContextHolder.getContext().setAuthentication(auth);
		} catch (AuthenticationException e) {
			authenticationEntryPoint.commence(request, response, e);
		}
	}

	public Client<?, ?> getClient() {
		return client;
	}

	public void setClient(Client<?, ?> client) {
		this.client = client;
	}

	public void setAuthenticationEntryPoint(
			AuthenticationEntryPoint authenticationEntryPoint) {
		this.authenticationEntryPoint = authenticationEntryPoint;
	}
}
