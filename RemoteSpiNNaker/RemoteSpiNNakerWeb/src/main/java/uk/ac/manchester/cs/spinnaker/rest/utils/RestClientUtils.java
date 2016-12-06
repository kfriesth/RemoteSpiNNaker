package uk.ac.manchester.cs.spinnaker.rest.utils;

import static org.apache.http.auth.AUTH.PROXY_AUTH_RESP;
import static org.apache.http.auth.AUTH.WWW_AUTH_RESP;
import static org.apache.http.auth.params.AuthParams.getCredentialCharset;
import static org.apache.http.client.protocol.ClientContext.AUTH_CACHE;
import static org.apache.http.client.protocol.ClientContext.CREDS_PROVIDER;
import static org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
import static org.slf4j.LoggerFactory.getLogger;

import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.RFC2617Scheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient4Engine;
import org.slf4j.Logger;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

public class RestClientUtils {
	private static Logger log = getLogger(RestClientUtils.class);

	protected static ResteasyClient createRestClient(URL url,
			Credentials credentials, AuthScheme authScheme) {
		try {
			SchemeRegistry schemeRegistry = getSchemeRegistry();
			HttpContext localContext = getConnectionContext(url, credentials,
					authScheme);

			// Set up the connection
			ClientConnectionManager cm = new BasicClientConnectionManager(
					schemeRegistry);
			DefaultHttpClient httpClient = new DefaultHttpClient(cm);
			ApacheHttpClient4Engine engine = new ApacheHttpClient4Engine(
					httpClient, localContext);

			// Create and return a client
			ResteasyClient client = new ResteasyClientBuilder().httpEngine(
					engine).build();
			client.register(new ErrorCaptureResponseFilter());
			return client;
		} catch (NoSuchAlgorithmException | KeyManagementException e) {
			log.error("Cannot find basic SSL algorithms - "
					+ "this suggests a broken Java installation...");
			throw new RuntimeException("unexpectedly broken security", e);
		}
	}

	/** Set up authentication */
	private static HttpContext getConnectionContext(URL url,
			Credentials credentials, AuthScheme authScheme) {
		int port = url.getPort();
		if (port == -1)
			port = url.getDefaultPort();
		HttpHost targetHost = new HttpHost(url.getHost(), port,
				url.getProtocol());

		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(new AuthScope(targetHost.getHostName(),
				targetHost.getPort()), credentials);

		AuthCache authCache = new BasicAuthCache();
		authCache.put(targetHost, authScheme);

		HttpContext localContext = new BasicHttpContext();
		localContext.setAttribute(AUTH_CACHE, authCache);
		localContext.setAttribute(CREDS_PROVIDER, credsProvider);
		return localContext;
	}

	private static boolean checkTrusted(X509Certificate[] certs) {
		return true;
	}
	private static X509Certificate getTrustedCert() {
		return null;
	}
	public static final String SECURE_PROTOCOL = "TLS";

	/** Set up HTTPS to ignore certificate errors
	 * @deprecated This method is doing bad things. */
	private static SchemeRegistry getSchemeRegistry()
			throws NoSuchAlgorithmException, KeyManagementException {
		SSLContext sslContext = SSLContext.getInstance(SECURE_PROTOCOL);
		sslContext.init(null, new TrustManager[] { new X509TrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] certs,
					String authType) throws CertificateException {
				// Does Nothing; we aren't deploying client-side certs
			}

			@Override
			public void checkServerTrusted(X509Certificate[] certs,
					String authType) throws CertificateException {
				if (!checkTrusted(certs))
					throw new CertificateException("untrusted server");
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				X509Certificate cert = getTrustedCert();
				if (cert == null)
					return null;
				return new X509Certificate[] { cert };
			}
		} }, new SecureRandom());
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("https", 443, new SSLSocketFactory(
				sslContext, ALLOW_ALL_HOSTNAME_VERIFIER)));
		return schemeRegistry;
	}

	/**
	 * Create a REST client proxy for a class to the given URL
	 * 
	 * @param url
	 *            The URL of the REST service
	 * @param credentials
	 *            The credentials used to access the service
	 * @param authScheme
	 *            The authentication scheme in use
	 * @param clazz
	 *            The interface to proxy
	 * @return The proxy instance
	 */
	public static <T> T createClient(URL url, Credentials credentials,
			AuthScheme authScheme, Class<T> clazz, Object... providers) {
		ResteasyClient client = createRestClient(url, credentials, authScheme);
		client.register(new JacksonJsonProvider());
		for (Object provider : providers)
			client.register(provider);
		return client.target(url.toString()).proxy(clazz);
	}

	/**
	 * Create a new REST client with BASIC authentication.
	 * 
	 * @param url
	 *            The URL of the REST service
	 * @param username
	 *            The user name of the user accessing the service
	 * @param password
	 *            The password for authentication
	 * @param clazz
	 *            The interface to proxy
	 * @return The proxy instance
	 */
	public static <T> T createBasicClient(URL url, String username,
			String password, Class<T> clazz, Object... providers) {
		return createClient(url, new UsernamePasswordCredentials(username,
				password), new BasicScheme(), clazz, providers);
	}

	/**
	 * Create a new REST client with APIKey authentication.
	 * 
	 * @param url
	 *            The URL of the REST service
	 * @param username
	 *            The user name of the user accessing the service
	 * @param apiKey
	 *            The key to use to authenticate
	 * @param clazz
	 *            The interface to proxy
	 *            @param providers The objects to register with the underlying client;
	 * @return The proxy instance
	 */
	public static <T> T createApiKeyClient(URL url, final String username,
			final String apiKey, Class<T> clazz, Object... providers) {
		return createClient(url, new UsernamePasswordCredentials(username,
				apiKey), new ConnectionIndependentScheme("ApiKey") {
			@Override
			protected Header authenticate(Credentials credentials) {
				return new BasicHeader(getAuthHeaderName(), "ApiKey "
						+ username + ":" + apiKey);
			}
		}, clazz, providers);
	}

	/**
	 * Create a new REST client with Bearer authentication.
	 * 
	 * @param url
	 *            The URL of the REST service
	 * @param token
	 *            The Bearer token for authentication
	 * @param clazz
	 *            The interface to proxy
	 * @return The proxy instance
	 */
	public static <T> T createBearerClient(URL url, final String token,
			Class<T> clazz, Object... providers) {
		return createClient(url, new UsernamePasswordCredentials("", token),
				new ConnectionIndependentScheme("Bearer") {
					@Override
					protected Header authenticate(Credentials credentials) {
						return new BasicHeader(getAuthHeaderName(), "Bearer "
								+ token);
					}
				}, clazz, providers);
	}

	private static abstract class ConnectionIndependentScheme extends RFC2617Scheme {
		private boolean complete = false;
		private String name;

		ConnectionIndependentScheme(String name) {
			this.name = name;
		}

		@Override
		public String getSchemeName() {
			return name;
		}

		@Override
		public boolean isConnectionBased() {
			return false;
		}

		@Override
		public boolean isComplete() {
			return complete;
		}

		/**
		 * Produce an authorization header for the given set of
		 * {@link Credentials}. The credentials and the connection will have
		 * been sanity-checked prior to this call.
		 */
		protected abstract Header authenticate(Credentials credentials);

		/**
		 * Give the header that we're supposed to generate, depending on whether
		 * we're going by a proxy or not.
		 */
		protected String getAuthHeaderName() {
			return isProxy() ? PROXY_AUTH_RESP : WWW_AUTH_RESP;
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

			return authenticate(credentials);
		}
	}
}
