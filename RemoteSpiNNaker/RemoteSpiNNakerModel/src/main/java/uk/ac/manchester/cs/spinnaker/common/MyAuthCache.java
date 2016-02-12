package uk.ac.manchester.cs.spinnaker.common;

import java.util.HashMap;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.apache.http.client.AuthCache;

public class MyAuthCache implements AuthCache {

    private final HashMap<HttpHost, AuthScheme> map =
    		new HashMap<HttpHost, AuthScheme>();

    public void put(final HttpHost host, final AuthScheme authScheme) {
        if (host == null) {
            throw new IllegalArgumentException("HTTP host may not be null");
        }
        this.map.put(host, authScheme);
    }

    public AuthScheme get(final HttpHost host) {
        if (host == null) {
            throw new IllegalArgumentException("HTTP host may not be null");
        }
        return this.map.get(host);
    }

    public void remove(final HttpHost host) {
        if (host == null) {
            throw new IllegalArgumentException("HTTP host may not be null");
        }
        this.map.remove(host);
    }

    public void clear() {
        this.map.clear();
    }

    @Override
    public String toString() {
        return this.map.toString();
    }

}
