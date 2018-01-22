package org.jenkinsci.plugins.ghprb;

import hudson.ProxyConfiguration;
import org.kohsuke.github.HttpConnector;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpConnectorWithJenkinsProxy implements HttpConnector {

    private static final int DEFAULT_CONNECT_TIMEOUT = 10000;

    private static final int DEFAULT_READ_TIMEOUT = 10000;

    public HttpURLConnection connect(URL url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) ProxyConfiguration.open(url);

        // Set default timeouts in case there are none
        if (con.getConnectTimeout() == 0) {
            con.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
        }
        if (con.getReadTimeout() == 0) {
            con.setReadTimeout(DEFAULT_READ_TIMEOUT);
        }
        return con;
    }
}
