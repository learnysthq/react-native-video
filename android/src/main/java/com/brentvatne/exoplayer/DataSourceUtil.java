package com.brentvatne.exoplayer;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.modules.network.CookieJarContainer;
import com.facebook.react.modules.network.ForwardingCookieHandler;
import com.facebook.react.modules.network.OkHttpClientProvider;
import com.facebook.react.modules.network.ReactCookieJarContainer;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;

import okhttp3.Call;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;

//Sridhar
import java.security.cert.CertificateException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import android.util.Log;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;


import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.util.List;

public class DataSourceUtil {

    private DataSourceUtil() {
    }

    private static DataSource.Factory rawDataSourceFactory = null;
    private static DataSource.Factory defaultDataSourceFactory = null;
    private static HttpDataSource.Factory defaultHttpDataSourceFactory = null;
    private static String userAgent = null;

    public static void setUserAgent(String userAgent) {
        DataSourceUtil.userAgent = userAgent;
    }

    public static String getUserAgent(ReactContext context) {
        if (userAgent == null) {
            userAgent = Util.getUserAgent(context, "ReactNativeVideo");
        }
        return userAgent;
    }

    public static DataSource.Factory getRawDataSourceFactory(ReactContext context) {
        if (rawDataSourceFactory == null) {
            rawDataSourceFactory = buildRawDataSourceFactory(context);
        }
        return rawDataSourceFactory;
    }

    public static void setRawDataSourceFactory(DataSource.Factory factory) {
        DataSourceUtil.rawDataSourceFactory = factory;
    }


    public static DataSource.Factory getDefaultDataSourceFactory(ReactContext context, DefaultBandwidthMeter bandwidthMeter, Map<String, String> requestHeaders) {
        if (defaultDataSourceFactory == null || (requestHeaders != null && !requestHeaders.isEmpty())) {
            defaultDataSourceFactory = buildDataSourceFactory(context, bandwidthMeter, requestHeaders);
        }
        return defaultDataSourceFactory;
    }

    public static void setDefaultDataSourceFactory(DataSource.Factory factory) {
        DataSourceUtil.defaultDataSourceFactory = factory;
    }

    public static HttpDataSource.Factory getDefaultHttpDataSourceFactory(ReactContext context, DefaultBandwidthMeter bandwidthMeter, Map<String, String> requestHeaders) {
        if (defaultHttpDataSourceFactory == null || (requestHeaders != null && !requestHeaders.isEmpty())) {
            defaultHttpDataSourceFactory = buildHttpDataSourceFactory(context, bandwidthMeter, requestHeaders);
        }
        return defaultHttpDataSourceFactory;
    }

    public static void setDefaultHttpDataSourceFactory(HttpDataSource.Factory factory) {
        DataSourceUtil.defaultHttpDataSourceFactory = factory;
    }

    private static DataSource.Factory buildRawDataSourceFactory(ReactContext context) {
        return new RawResourceDataSourceFactory(context.getApplicationContext());
    }

    private static DataSource.Factory buildDataSourceFactory(ReactContext context, DefaultBandwidthMeter bandwidthMeter, Map<String, String> requestHeaders) {
        return new DefaultDataSource.Factory(context,
                buildHttpDataSourceFactory(context, bandwidthMeter, requestHeaders));
    }

    private static HttpDataSource.Factory buildHttpDataSourceFactory(ReactContext context, DefaultBandwidthMeter bandwidthMeter, Map<String, String> requestHeaders) {
        /*Sridhar - To bypass proxy for localhost - start*/
        //OkHttpClient client = OkHttpClientProvider.getOkHttpClient();
        ProxySelector proxySelector = new ProxySelector() {
            @Override
            public void connectFailed(URI uri, SocketAddress addr, IOException error) {
                System.err.println("React-Native-Video: Failed to connect to proxy!!!!");
            }

            @Override
            public List<Proxy> select(URI uri) {
                if (uri.getHost() != null && (uri.getHost().equals("localhost") || uri.getHost().equals("127.0.0.1"))) {
                    // If the request is for localhost, don't use a proxy
                    List<Proxy> proxyList = new ArrayList<Proxy>(1);
                    proxyList.add(Proxy.NO_PROXY);
                    return proxyList;
                } else {
                    // For other requests, use the default proxy (if any)
                    return ProxySelector.getDefault().select(uri);
                }
            }
        };

        //OkHttpClient client = new OkHttpClient.Builder().proxySelector(proxySelector).cookieJar(new ReactCookieJarContainer()).build();
        //Sridhar bypass SSL cert validation        
        OkHttpClient client = null;

        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                    @Override public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                    @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
                }
            };

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            client = new OkHttpClient.Builder()
                .proxySelector(proxySelector)
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .cookieJar(new ReactCookieJarContainer())
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        Log.d("LstDrm", "Exoplayer Requesting: " + request.url());
                        //System.err.println("LstDrm: Exoplayer Requesting: " + request.url());
                        return chain.proceed(request);
                    }
                })
                .build();

        } catch (Exception e) {
            e.printStackTrace();
            // Fallback or throw runtime exception
            throw new RuntimeException("Failed to create SSL trusting client", e);
        }
        /*Sridhar - To bypass proxy for localhost - end*/

        CookieJarContainer container = (CookieJarContainer) client.cookieJar();
        ForwardingCookieHandler handler = new ForwardingCookieHandler(context);
        container.setCookieJar(new JavaNetCookieJar(handler));
        OkHttpDataSource.Factory okHttpDataSourceFactory = new OkHttpDataSource.Factory((Call.Factory) client)
                .setUserAgent(getUserAgent(context))
                .setTransferListener(bandwidthMeter);

        if (requestHeaders != null)
            okHttpDataSourceFactory.setDefaultRequestProperties(requestHeaders);

        return okHttpDataSourceFactory;
    }
}
