package org.jenkinsci.plugins.ghprb;

import jenkins.model.Jenkins;
import jenkins.util.JenkinsJVM;
import okhttp3.OkHttpClient;
import org.kohsuke.github.extras.okhttp3.OkHttpConnector;

import javax.annotation.Nonnull;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

public class HttpConnectorWithJenkinsProxy extends OkHttpConnector {


    private static final int DEFAULT_CONNECT_TIMEOUT = 10000;

    private static final int DEFAULT_READ_TIMEOUT = 10000;

    // Create a default base client with our default connect timeouts
    private static final OkHttpClient BASECLIENT = new OkHttpClient().newBuilder()
        .connectTimeout(DEFAULT_CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(DEFAULT_READ_TIMEOUT, TimeUnit.MILLISECONDS)
        .build();

    public HttpConnectorWithJenkinsProxy(String host) {
        super(getClient(host));
    }

    private static OkHttpClient getClient(String host) {
        OkHttpClient.Builder builder = BASECLIENT.newBuilder();

        if (JenkinsJVM.isJenkinsJVM()) {
            builder.proxy(getProxy(host));
        }

        return builder.build();
    }

    /**
     * Uses proxy if configured on pluginManager/advanced page
     *
     * @param host GitHub's hostname to build proxy to
     *
     * @return proxy to use it in connector. Should not be null as it can lead to unexpected behaviour
     */
    @Nonnull
    private static Proxy getProxy(@Nonnull String host) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null || jenkins.proxy == null) {
            return Proxy.NO_PROXY;
        } else {
            return jenkins.proxy.createProxy(host);
        }
    }
}
