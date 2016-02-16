package uk.ac.manchester.cs.spinnaker.rest;

import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient4Engine;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

public class RestClientUtils {

    public static ResteasyClient createRestClient(
            URL url, Credentials credentials, AuthScheme authScheme) {
        // Set up HTTPS to ignore certificate errors
        try {

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[]{
                new IgnoreSSLCertificateTrustManager()},
                new SecureRandom());
            SSLSocketFactory sf = new SSLSocketFactory(sslContext,
                SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            Scheme httpsScheme = new Scheme("https", 443, sf);
            SchemeRegistry schemeRegistry = new SchemeRegistry();
            schemeRegistry.register(httpsScheme);

            // Set up authentication
            HttpHost targetHost = new HttpHost(
                url.getHost(), url.getPort(), url.getProtocol());
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                new AuthScope(targetHost.getHostName(), targetHost.getPort()),
                credentials);
            AuthCache authCache = new MyAuthCache();
            authCache.put(targetHost, authScheme);

            // Set up the connection
            BasicHttpContext localContext = new BasicHttpContext();
            localContext.setAttribute(ClientContext.AUTH_CACHE, authCache);
            localContext.setAttribute(
                ClientContext.CREDS_PROVIDER, credsProvider);
            ClientConnectionManager cm = new BasicClientConnectionManager(
                schemeRegistry);
            DefaultHttpClient httpClient = new DefaultHttpClient(cm);
            ApacheHttpClient4Engine engine = new ApacheHttpClient4Engine(
                httpClient, localContext);

            // Create and return a client
            ResteasyClient client =
                new ResteasyClientBuilder().httpEngine(engine).build();
            client.register(new ErrorCaptureResponseFilter());
            return client;
        } catch (NoSuchAlgorithmException|KeyManagementException e) {
            System.err.println(
                "Cannot find basic SSL algorithms - " +
                "this suggests a broken Java installation...");
            throw new RuntimeException(e);
        }
    }

    /**
    * Create a REST client proxy for a class to the given URL
    * @param url The URL of the REST service
    * @param credentials The credentials used to access the service
    * @param authScheme The authentication scheme in use
    * @param clazz The interface to proxy
    * @return The proxy instance
    */
    public static <T> T createClient(
            URL url, Credentials credentials, AuthScheme authScheme,
            Class<T> clazz) {
        ResteasyClient client = createRestClient(url, credentials, authScheme);
        client.register(new JacksonJsonProvider());
        ResteasyWebTarget target = client.target(url.toString());
        return target.proxy(clazz);
    }

    /**
     * Create a new REST client with BASIC authentication
     * @param url The URL of the REST service
     * @param username The user name of the user accessing the service
     * @param password The password for authentication
     * @param clazz The interface to proxy
     * @return The proxy instance
     */
    public static <T> T createBasicClient(
            URL url, String username, String password, Class<T> clazz) {
        return createClient(
            url, new UsernamePasswordCredentials(username, password),
            new BasicScheme(), clazz);
    }

    /**
     * Create a new REST client with APIKey authentication
     * @param url The URL of the REST service
     * @param username The user name of the user accessing the service
     * @param apiKey The key to use to authenticate
     * @param clazz The interface to proxy
     * @return The proxy instance
     */
    public static <T> T createApiKeyClient(
            URL url, String username, String apiKey, Class<T> clazz) {
        return createClient(
            url, new UsernamePasswordCredentials(username, apiKey),
            new APIKeyScheme(), clazz);
    }

    /**
     * Create a new REST client with Bearer authentication
     * @param url The URL of the REST service
     * @param token The Bearer token for authentication
     * @param clazz The interface to proxy
     * @return The proxy instance
     */
    public static <T> T createBearerClient(
            URL url, String token, Class<T> clazz) {
        return createClient(
            url, new UsernamePasswordCredentials("", token),
            new BearerScheme(), clazz);
    }

}
