package uk.ac.manchester.cs.spinnaker.remote.webutils;

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
import org.pac4j.springframework.security.web.ClientAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class DirectClientAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger =
        LoggerFactory.getLogger(ClientAuthenticationFilter.class);

    private Client<?, ?> client = null;

    private AuthenticationDetailsSource<HttpServletRequest, ?>
        authenticationDetailsSource = new WebAuthenticationDetailsSource();

    private AuthenticationEntryPoint authenticationEntryPoint =
        new BearerAuthenticationEntryPoint();

    private AuthenticationManager authenticationManager = null;

    public DirectClientAuthenticationFilter(
            AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // context
        final WebContext context = new J2EContext(request, response);

        // get credentials
        Credentials credentials = null;
        try {
            credentials = client.getCredentials(context);
        } catch (final RequiresHttpAction e) {
            logger.info("Requires additionnal HTTP action", e);
        } catch (CredentialsException ce) {
            throw new AuthenticationCredentialsException(
                "Error retrieving credentials", ce);
        }

        logger.debug("credentials : {}", credentials);

        // if credentials/profile is not null, do more
        if (credentials != null) {

            try {

                // create token from credential
                final ClientAuthenticationToken token =
                    new ClientAuthenticationToken(
                        credentials, client.getName());
                token.setDetails(
                    authenticationDetailsSource.buildDetails(request));

                // authenticate
                final Authentication authentication =
                    authenticationManager.authenticate(token);
                logger.debug("authentication: {}", authentication);

                SecurityContextHolder.getContext().setAuthentication(
                    authentication);
            } catch (AuthenticationException e) {
                authenticationEntryPoint.commence(request, response, e);
            }
        }

        filterChain.doFilter(request, response);
    }

    public Client<?, ?> getClient() {
        return this.client;
    }

    public void setClient(final Client<?, ?> client) {
        this.client = client;
    }

    public void setAuthenticationEntryPoint(
            AuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
    }
}
