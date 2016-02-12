package uk.ac.manchester.cs.spinnaker.unicore;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.ws.rs.WebApplicationException;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient4Engine;

import uk.ac.manchester.cs.spinnaker.common.IgnoreSSLCertificateTrustManager;
import uk.ac.manchester.cs.spinnaker.common.MyAuthCache;
import uk.ac.manchester.cs.spinnaker.unicore.rest.BearerScheme;
import uk.ac.manchester.cs.spinnaker.unicore.rest.UnicoreFileClient;

/**
 * A manager for the UNICORE storage REST API
 */
public class UnicoreFileManager {

    private UnicoreFileClient fileClient = null;

    public UnicoreFileManager(URL url, String username, String token)
            throws KeyManagementException, NoSuchAlgorithmException {

        // Set up HTTPS to ignore certificate errors
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
        HttpHost targetHost = new HttpHost(url.getHost(), url.getPort(),
                url.getProtocol());
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(targetHost.getHostName(), targetHost.getPort()),
                new UsernamePasswordCredentials(username, token));
        AuthCache authCache = new MyAuthCache();

        System.err.println("Target host = " + targetHost);
        AuthScheme tokenAuth = new BearerScheme();
        authCache.put(targetHost, tokenAuth);

        // Set up the connection
        BasicHttpContext localContext = new BasicHttpContext();
        localContext.setAttribute(ClientContext.AUTH_CACHE, authCache);
        localContext.setAttribute(ClientContext.CREDS_PROVIDER, credsProvider);
        ClientConnectionManager cm = new BasicClientConnectionManager(
                schemeRegistry);
        DefaultHttpClient httpClient = new DefaultHttpClient(cm);
        ApacheHttpClient4Engine engine = new ApacheHttpClient4Engine(httpClient,
                localContext);

        ResteasyClient client =
                new ResteasyClientBuilder().httpEngine(engine).build();
        ResteasyWebTarget target = client.target(url.toString());
        fileClient = target.proxy(UnicoreFileClient.class);
    }

    /**
     * Upload a file
     * @param storageId The id of the storage on the server
     * @param filePath The path at which to store the file (directories are
     *                 automatically created)
     * @param input The input stream containing the file to upload
     * @throws IOException
     */
    public void uploadFile(String storageId, String filePath, InputStream input)
            throws IOException {
        try {
            fileClient.upload(storageId, filePath, input);
        } catch (WebApplicationException e) {
            throw new IOException(
                "Error uploading file to " + storageId + "/" + filePath, e);
        }
    }
}
