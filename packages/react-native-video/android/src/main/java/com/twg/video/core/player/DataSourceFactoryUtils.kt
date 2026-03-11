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
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
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
    // Build a custom OkHttpClient that trusts self-signed certs for the local proxy
    val trustAllManager = object : X509TrustManager {
      override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
      override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
      override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())

    // Bypass system proxy for localhost
    val noProxySelector = object : ProxySelector() {
      override fun select(uri: URI?): List<Proxy> = listOf(Proxy.NO_PROXY)
      override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) {}
    }

    client = OkHttpClient.Builder()
      .sslSocketFactory(sslContext.socketFactory, trustAllManager)
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
