package uk.ac.manchester.cs.spinnaker.remote.web;

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public class BearerAuthenticationEntryPoint implements AuthenticationEntryPoint {
	private String realmName = "";

	public void setRealmName(String realmName) {
		this.realmName = realmName;
	}

	@Override
	public void commence(HttpServletRequest request,
			HttpServletResponse response, AuthenticationException authException)
			throws IOException, ServletException {
		response.addHeader("WWW-Authenticate", "Bearer realm=\"" + realmName
				+ "\"");
		response.sendError(SC_UNAUTHORIZED, authException.getMessage());
	}
}
