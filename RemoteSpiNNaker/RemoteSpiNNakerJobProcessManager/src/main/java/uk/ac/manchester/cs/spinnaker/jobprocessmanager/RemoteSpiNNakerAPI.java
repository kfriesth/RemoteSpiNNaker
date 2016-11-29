package uk.ac.manchester.cs.spinnaker.jobprocessmanager;

import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static org.jboss.resteasy.util.Base64.encodeBytes;

import java.io.IOException;
import java.nio.charset.Charset;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

import uk.ac.manchester.cs.spinnaker.job.JobManagerInterface;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

public abstract class RemoteSpiNNakerAPI {
	private RemoteSpiNNakerAPI() {
	}

	private static final Charset UTF8 = Charset.forName("UTF-8");

	/**
	 * How to talk to the main website.
	 * 
	 * @param url
	 *            Where the main website is located.
	 * @param authToken
	 *            How to authenticate to the main website, or <tt>null</tt> to
	 *            not provide auth. If given, Should be the concatenation of the
	 *            username, a colon (<tt>:</tt>), and the password.
	 */
	public static JobManagerInterface createJobManager(String url,
			String authToken) {
		ResteasyClientBuilder builder = new ResteasyClientBuilder();
		// TODO Add https trust store, etc.
		ResteasyClient client = builder.build();
		client.register(new JacksonJsonProvider());
		if (authToken != null)
			client.register(getBasicAuthFilter(authToken));
		return client.target(url).proxy(JobManagerInterface.class);
	}

	private static ClientRequestFilter getBasicAuthFilter(String authToken) {
		final String payload = "Basic " + encodeBytes(authToken.getBytes(UTF8));
		return new ClientRequestFilter() {
			@Override
			public void filter(ClientRequestContext requestContext)
					throws IOException {
				requestContext.getHeaders().add(AUTHORIZATION, payload);
			}
		};
	}
}
