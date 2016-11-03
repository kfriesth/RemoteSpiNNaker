package uk.ac.manchester.cs.spinnaker.rest;

import static org.apache.http.auth.AUTH.PROXY_AUTH_RESP;
import static org.apache.http.auth.AUTH.WWW_AUTH_RESP;
import static org.apache.http.auth.params.AuthParams.getCredentialCharset;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.impl.auth.RFC2617Scheme;
import org.apache.http.message.BufferedHeader;
import org.apache.http.util.CharArrayBuffer;

public class APIKeyScheme extends RFC2617Scheme {
	private boolean complete = false;

	@Override
	public String getSchemeName() {
		return "ApiKey";
	}

	@Override
	public boolean isConnectionBased() {
		return false;
	}

	@Override
	public boolean isComplete() {
		return complete;
	}

	@Override
	public Header authenticate(Credentials credentials, HttpRequest request)
			throws AuthenticationException {
		if (credentials == null)
            throw new IllegalArgumentException("Credentials may not be null");
        if (request == null)
            throw new IllegalArgumentException("HTTP request may not be null");
        String charset = getCredentialCharset(request.getParams());
		if (charset == null)
			throw new IllegalArgumentException("charset may not be null");

		CharArrayBuffer buffer = new CharArrayBuffer(32);
		if (isProxy())
			buffer.append(PROXY_AUTH_RESP);
		else
			buffer.append(WWW_AUTH_RESP);

		buffer.append(": ApiKey ");
		buffer.append(credentials.getUserPrincipal().getName());
		buffer.append(":");
		buffer.append(credentials.getPassword());

		return new BufferedHeader(buffer);
	}

}
