package uk.ac.manchester.cs.spinnaker.remote.web;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;

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
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class DirectClientAuthenticationFilter extends OncePerRequestFilter {
	private final Logger logger = getLogger(getClass());
	private Client<?, ?> client;
	private AuthenticationDetailsSource<HttpServletRequest, ?> authenticationDetailsSource = new WebAuthenticationDetailsSource();
	private AuthenticationEntryPoint authenticationEntryPoint = new BearerAuthenticationEntryPoint();
	private AuthenticationManager authenticationManager;

	public DirectClientAuthenticationFilter(
			AuthenticationManager authenticationManager) {
		this.authenticationManager = authenticationManager;
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
		token.setDetails(authenticationDetailsSource.buildDetails(request));

		try {
			// authenticate
			Authentication authentication = authenticationManager
					.authenticate(token);
			logger.debug("authentication: {}", authentication);
			SecurityContextHolder.getContext()
					.setAuthentication(authentication);
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
