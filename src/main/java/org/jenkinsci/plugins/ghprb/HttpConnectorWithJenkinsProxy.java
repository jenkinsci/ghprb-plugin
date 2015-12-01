package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.kohsuke.github.HttpConnector;

import hudson.ProxyConfiguration;

public class HttpConnectorWithJenkinsProxy implements HttpConnector {
    public HttpURLConnection connect(URL url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) ProxyConfiguration.open(url);

        // Set default timeouts in case there are none
        if (con.getConnectTimeout() == 0) {
            con.setConnectTimeout(10000);
        }
        if (con.getReadTimeout() == 0) {
            con.setReadTimeout(10000);
        }
        return con;
    }
}
