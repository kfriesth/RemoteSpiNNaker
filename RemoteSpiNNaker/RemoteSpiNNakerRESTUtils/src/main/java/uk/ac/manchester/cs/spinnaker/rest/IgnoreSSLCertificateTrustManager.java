package uk.ac.manchester.cs.spinnaker.rest;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

@Deprecated
public class IgnoreSSLCertificateTrustManager implements X509TrustManager {

	@Override
	public void checkClientTrusted(X509Certificate[] certs, String authType)
			throws CertificateException {
		// Does Nothing
	}

	@Override
	public void checkServerTrusted(X509Certificate[] certs, String authType)
			throws CertificateException {
		// Does Nothing
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return null;
	}

}
