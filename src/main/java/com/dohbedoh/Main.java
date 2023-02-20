package com.dohbedoh;

import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Route;
import okhttp3.internal.platform.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Allan Burdajewicz
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private static final long INTERVAL_MS = Long.getLong("com.dohbedoh.requestInterval", 90000L);
    private static final String PROXY_HOST = System.getProperty("com.dohbedoh.proxyHost");
    private static final Integer PROXY_PORT = Integer.getInteger("com.dohbedoh.proxyPort");
    private static final boolean RECREATE_CLIENTS = Boolean.getBoolean("com.dohbedoh.recreateClients");
    private static final String USER = System.getenv("TEST_USER");
    private static final String PASS = System.getenv("TEST_PASS");

    public static void main(String[] args) throws MalformedURLException, IllegalArgumentException {
        OkHttpClient client = createNewClient();

        if(args.length == 0) {
            throw new IllegalArgumentException("Expecting exactly one argument. Please specify the request URL to test");
        }

        URL testUrl = new URL(args[0]);

        while(true) {
            testUrl(client, testUrl);

            testDns(client, testUrl);

            try {
                Thread.sleep(INTERVAL_MS);
            } catch (InterruptedException ignored) {}

            if(RECREATE_CLIENTS) {
                client = createNewClient();
            } else {
                // Thought maybe client still keep connections active..
                client.connectionPool().evictAll();
            }
        }
    }

    public static OkHttpClient createNewClient() {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

        // Authentication
        if(USER != null && USER.trim().length() > 0 && PASS !=null && PASS.trim().length() > 0) {
            clientBuilder.authenticator(new BasicAuthenticatorImpl(USER, PASS));
        }

        // Proxy
        if(PROXY_HOST != null && PROXY_HOST.trim().length() > 0 && PROXY_PORT != null) {
            clientBuilder.proxySelector(new ProxySelectorImpl(PROXY_HOST, PROXY_PORT));
        }

        // Logging
        clientBuilder.addInterceptor(new LoggingInterceptor("APPLICATION"));
        clientBuilder.addNetworkInterceptor(new LoggingInterceptor("NETWORK"));

        clientBuilder.sslSocketFactory(new LoggingSocketFactory(), Platform.get().platformTrustManager());
        clientBuilder.eventListener(new ConnectionListener());

        return clientBuilder.build();
    }

    public static void testUrl(OkHttpClient client, URL url) {
        Request okhttp3Request = new Request.Builder().url(url).build();
        try(Response okhttp3Response = client.newCall(okhttp3Request).execute()){
            try(ResponseBody responseBody = okhttp3Response.body()) {
                try {
                    if(responseBody != null) {
                        LOGGER.log(Level.INFO, responseBody.string());
                    } else {
                        LOGGER.log(Level.INFO, "Empty body");
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Could not retrieve body", e);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failure executing request", e);
        }
    }

    public static void testDns(OkHttpClient client, URL url) {
        try {
            LOGGER.log(Level.INFO, "Lookup DNS for " + url.getHost());
            List<InetAddress> lookups = client.dns().lookup(url.getHost());
            lookups.forEach(inetAddress ->
                LOGGER.log(Level.INFO, "  " + inetAddress.toString()));

            if(PROXY_HOST != null && PROXY_HOST.trim().length() > 0 && PROXY_PORT != null) {
                LOGGER.log(Level.INFO, "Lookup DNS for " + PROXY_HOST);
                lookups = client.dns().lookup(PROXY_HOST);
                lookups.forEach(inetAddress ->
                    LOGGER.log(Level.INFO, "  " + inetAddress.toString()));
            }
        } catch (UnknownHostException e) {
            LOGGER.log(Level.SEVERE, "Failure looking up URL " + url, e);
        }
    }

    public static class BasicAuthenticatorImpl implements Authenticator {

        private final String authStr;

        public BasicAuthenticatorImpl(String user, String pass) {
            this.authStr = "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
        }

        @Override
        public Request authenticate(Route route, Response response) throws IOException {
            return response.request().newBuilder().header("Authorization", authStr).build();
        }
    }

    public static class ProxySelectorImpl extends ProxySelector {

        private final Proxy proxy;

        public ProxySelectorImpl(final String host, final int port) {
            this.proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        }

        @Override
        public List<Proxy> select(URI uri) {
            return List.of(proxy);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            LOGGER.log(Level.WARNING, "Proxy connection failed", ioe);
        }
    }

    public static class LoggingInterceptor implements Interceptor {

        private final String prefix;

        public LoggingInterceptor(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response;
            long t1 = System.nanoTime();
            LOGGER.log(
                Level.INFO,
                String.format(
                    "[%s] Sending request %s on %s%n%s",
                    prefix, request.url(), chain.connection(), request.headers()));
            response = chain.proceed(request);

            long t2 = System.nanoTime();
            LOGGER.log(
                Level.INFO,
                String.format(
                    "[%s] Received response for %s in %.1fms%n%s",
                    prefix, response.request().url(), (t2 - t1) / 1e6d, response.headers()));
            return response;
        }
    }

    public static class LoggingSocketFactory extends SSLSocketFactory {

        private final SSLSocketFactory delegate =
            Platform.get().newSslSocketFactory(Platform.get().platformTrustManager());

        public LoggingSocketFactory() {
            super();
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return this.delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return this.delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            LOGGER.log(
                Level.INFO,
                String.format(
                    "Socket connection to: %s:%s",
                    host, port));
            return delegate.createSocket(s, host, port, autoClose);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            LOGGER.log(
                Level.INFO,
                String.format(
                    "Socket connection to: %s:%s",
                    host, port));
            return this.delegate.createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            LOGGER.log(
                Level.INFO,
                String.format(
                    "Socket connection to: %s:%s on local %s:%s",
                    host, port, localHost, localPort));
            return this.delegate.createSocket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            LOGGER.log(
                Level.INFO,
                String.format(
                    "Socket connection by InetAddress to: %s:%s",
                    host, port));
            return this.delegate.createSocket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            LOGGER.log(
                Level.INFO,
                String.format(
                    "Socket connection by InetAddress to: %s:%s on local %s:%s",
                    address, port, localAddress, localPort));
            return this.delegate.createSocket(address, port, localAddress, localPort);
        }
    }

    public static class ConnectionListener extends EventListener {

        @Override
        public void connectStart(@NotNull Call call, @NotNull InetSocketAddress inetSocketAddress, @NotNull Proxy proxy) {
            LOGGER.log(
                Level.INFO,
                String.format(
                    "Connect Start to %s through %s",
                    inetSocketAddress, proxy));
            super.connectStart(call, inetSocketAddress, proxy);
        }

        @Override
        public void connectEnd(@NotNull Call call, @NotNull InetSocketAddress inetSocketAddress, @NotNull Proxy proxy, @Nullable Protocol protocol) {
            LOGGER.log(
                Level.INFO,
                String.format(
                    "Connect End to %s through %s",
                    inetSocketAddress, proxy));
            super.connectEnd(call, inetSocketAddress, proxy, protocol);
        }

        @Override
        public void connectFailed(@NotNull Call call, @NotNull InetSocketAddress inetSocketAddress, @NotNull Proxy proxy, @Nullable Protocol protocol, @NotNull IOException ioe) {
            LOGGER.log(
                Level.INFO,
                String.format(
                    "Connect Failed to %s through %s with %s",
                    inetSocketAddress, proxy, ioe));
            super.connectFailed(call, inetSocketAddress, proxy, protocol, ioe);
        }

        @Override
        public void dnsEnd(@NotNull Call call, @NotNull String domainName, @NotNull List<InetAddress> inetAddressList) {
            LOGGER.log(
                Level.INFO,
                String.format(
                    "DNS End for domain %s with %s",
                    domainName, inetAddressList));
            super.dnsEnd(call, domainName, inetAddressList);
        }

        @Override
        public void dnsStart(@NotNull Call call, @NotNull String domainName) {
            LOGGER.log(
                Level.INFO,
                String.format(
                    "DNS End for domain %s", domainName));
            super.dnsStart(call, domainName);
        }
    }
}