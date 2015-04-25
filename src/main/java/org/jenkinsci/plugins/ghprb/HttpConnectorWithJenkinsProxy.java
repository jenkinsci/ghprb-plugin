package org.jenkinsci.plugins.ghprb;

import hudson.ProxyConfiguration;
import org.kohsuke.github.HttpConnector;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpConnectorWithJenkinsProxy implements HttpConnector {
    public HttpURLConnection connect(URL url) throws IOException {
        return (HttpURLConnection) ProxyConfiguration.open(url);
    }
}
