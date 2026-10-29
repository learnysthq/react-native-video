package com.twg.video.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.network.CookieJarContainer
import com.facebook.react.modules.network.ForwardingCookieHandler
import com.facebook.react.modules.network.OkHttpClientProvider
import com.margelo.nitro.video.HybridVideoPlayerSourceSpec
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

fun buildBaseDataSourceFactory(context: Context, source: HybridVideoPlayerSourceSpec): DefaultDataSource.Factory {
  return if (source.uri.startsWith("http")) {
    DefaultDataSource.Factory(context, buildHttpDataSourceFactory(context, source))
  } else {
    DefaultDataSource.Factory(context)
  }
}

private fun isLocalhostHttpsUrl(uri: String): Boolean {
  return uri.startsWith("https://localhost") || uri.startsWith("https://127.0.0.1")
}

@OptIn(UnstableApi::class)
fun buildHttpDataSourceFactory(context: Context, source: HybridVideoPlayerSourceSpec): OkHttpDataSource.Factory {
  val isLocalhostHttps = isLocalhostHttpsUrl(source.uri)
  val client: OkHttpClient
  if (isLocalhostHttps) {
    // Trust ONLY the loopback DRM proxy's fixed self-signed certificate (liblstproxy,
    // lstproxy.c server_cert_pem; CN=localhost, valid to 2034-11-10). The trust-all manager
    // that was here is the twin of the one Google Play flagged in react-native-webview
    // (Unsafe TrustManager, deadline 2027-01-01). The WebView patch pins the same PEM;
    // if secure-apps rotates the proxy certificate, update both in the same release.
    val trustManager = loopbackProxyTrustManager()
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

    // Bypass system proxy for localhost
    val noProxySelector = object : ProxySelector() {
      override fun select(uri: URI?): List<Proxy> = listOf(Proxy.NO_PROXY)
      override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) {}
    }

    client = OkHttpClient.Builder()
      .sslSocketFactory(sslContext.socketFactory, trustManager)
      .hostnameVerifier { hostname, _ -> hostname == "localhost" || hostname == "127.0.0.1" }
      .proxySelector(noProxySelector)
      .build()
  } else {
    client = OkHttpClientProvider.getOkHttpClient()
  }

  if (context is ReactContext) {
    val cookieJar = client.cookieJar
    if (cookieJar is CookieJarContainer) {
      val handler = ForwardingCookieHandler(context)
      cookieJar.setCookieJar(JavaNetCookieJar(handler))
    }
  }

  val factory = OkHttpDataSource.Factory(client)

  val headers: Map<String, String>? = source.config.headers

  if (headers != null) {
    factory.setDefaultRequestProperties(headers)
  }

  if (headers == null || !headers.containsKey("User-Agent")) {
    factory.setUserAgent(getUserAgent(context))
  }

  return factory
}

@OptIn(UnstableApi::class)
fun getUserAgent(context: Context): String {
  return Util.getUserAgent(context, context.packageName)
}

private const val LOOPBACK_PROXY_CERT_PEM =
  "-----BEGIN CERTIFICATE-----\n" +
  "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\n" +
  "fWH+VkeJ0NsN2aMRRpvc\n" +
  "-----END CERTIFICATE-----\n" +
  ""

/** An X509TrustManager whose only trust anchor is the loopback proxy's certificate. */
private fun loopbackProxyTrustManager(): X509TrustManager {
  val cert = CertificateFactory.getInstance("X.509")
    .generateCertificate(ByteArrayInputStream(LOOPBACK_PROXY_CERT_PEM.toByteArray(Charsets.US_ASCII)))
  val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
    load(null, null)
    setCertificateEntry("lst-loopback-proxy", cert)
  }
  val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
    init(trustStore)
  }
  return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
}
