package uk.ac.manchester.cs.spinnaker.rest;

import java.util.HashMap;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.apache.http.client.AuthCache;

public class MyAuthCache implements AuthCache {
	private final HashMap<HttpHost, AuthScheme> map = new HashMap<>();

	@Override
	public void put(HttpHost host, AuthScheme authScheme) {
		if (host == null)
			throw new IllegalArgumentException("HTTP host may not be null");
		map.put(host, authScheme);
	}

	@Override
	public AuthScheme get(HttpHost host) {
		if (host == null)
			throw new IllegalArgumentException("HTTP host may not be null");
		return map.get(host);
	}

	@Override
	public void remove(HttpHost host) {
		if (host == null)
			throw new IllegalArgumentException("HTTP host may not be null");
		map.remove(host);
	}

	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public String toString() {
		return map.toString();
	}
}
