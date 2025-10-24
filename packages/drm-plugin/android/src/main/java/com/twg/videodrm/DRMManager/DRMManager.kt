package com.twg.videodrm.DRMManager

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem.DrmConfiguration
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback
import com.twg.video.core.player.DRMManagerSpec
import com.margelo.nitro.NitroModules
import com.margelo.nitro.video.NativeDrmParams
import com.twg.video.core.player.buildHttpDataSourceFactory
import com.twg.video.core.plugins.NativeVideoPlayerSource
import java.util.UUID
import android.util.Log
import android.util.Base64

import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.network.CookieJarContainer
import com.facebook.react.modules.network.ForwardingCookieHandler
import com.margelo.nitro.video.HybridVideoPlayerSourceSpec
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI


class DRMManager(val source: NativeVideoPlayerSource) : DRMManagerSpec {
  private var hasDrmFailed = false
  private val context: Context
    get() {
      return NitroModules.applicationContext ?: throw Error("Context is not found")
    }

  private var headersHash = -1
  private var httpDataSourceFactory: OkHttpDataSource.Factory? = null

  private fun shouldRebuildHttpDataSource(): Boolean {
    val header = source.config.headers
    if (header == null) {
      return false
    }

    val hash = header.hashCode()
    if (hash == headersHash) {
      return false
    }

    headersHash = hash
    return true
  }

  @OptIn(UnstableApi::class)
  private fun getHttpDataSourceFactory(): OkHttpDataSource.Factory {
      if (shouldRebuildHttpDataSource() || httpDataSourceFactory == null) {
          try {
              // ---- Custom ProxySelector: bypass localhost ----
              val proxySelector = object : ProxySelector() {
                  override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                      Log.e("LYST DRMManager.kt", "Proxy connection failed: ${ioe?.message}")
                  }

                  override fun select(uri: URI?): List<Proxy> {
                      return if (uri?.host == "localhost" || uri?.host == "127.0.0.1") {
                          listOf(Proxy.NO_PROXY)
                      } else {
                          ProxySelector.getDefault()?.select(uri) ?: listOf(Proxy.NO_PROXY)
                      }
                  }
              }

              // ---- Build custom OkHttpClient with cookie + logging ----
              val client = OkHttpClient.Builder()
                  .proxySelector(proxySelector)
                  .cookieJar(com.facebook.react.modules.network.ReactCookieJarContainer())
                  .build()

              // ---- Attach React Native cookie jar ----
              val container = client.cookieJar as CookieJarContainer
              val handler = ForwardingCookieHandler(context)
              container.setCookieJar(JavaNetCookieJar(handler))

              // ---- Build OkHttpDataSource.Factory for Media3 ----
              val factory = OkHttpDataSource.Factory(client)
                  .setUserAgent(Util.getUserAgent(context, context.packageName))

              // ---- Apply custom headers from source.config ----
              val headers = source.config.headers
              if (headers != null) {
                  factory.setDefaultRequestProperties(headers)
              }

              httpDataSourceFactory = factory

          } catch (e: Exception) {
              Log.e("LYST DRMManager.kt", "❌ Failed to build DRM HttpDataSourceFactory: ${e.message}")
          }
      }

      return httpDataSourceFactory ?: throw Error("Couldn't build HttpDataSourceFactory")
  }

  @OptIn(UnstableApi::class)
  override fun buildDrmSessionManager(
    drmParams: NativeDrmParams,
    drmUuid: UUID?,
    retryCount: Int
  ): DrmSessionManager {
    val uuid = drmUuid ?: throw Error("DRM UUID is not set")
    val mediaDrm = FrameworkMediaDrm.newInstance(uuid)
    val drmCallback = HttpMediaDrmCallback(
      drmParams.licenseUrl,
      getHttpDataSourceFactory(),
    )

    drmParams.licenseHeaders?.forEach { (key, value) ->
      drmCallback.setKeyRequestProperty(key, value)
    }

    if (hasDrmFailed) mediaDrm.setPropertyString("securityLevel", "L3")

    val builder = DefaultDrmSessionManager.Builder()
      .setUuidAndExoMediaDrmProvider(uuid) { mediaDrm }
      .setMultiSession(drmParams.multiSession == true)

    val drmSessionManager = builder.build(drmCallback)

    // ✅ handle offline keyset (same as old ExoPlayer)
    val offlineKeySetId = drmParams.offlineKeyId // or pass explicitly

    if (!offlineKeySetId.isNullOrEmpty()) {
      val offlineAssetKeyId = Base64.decode(offlineKeySetId, Base64.DEFAULT)
      if (offlineAssetKeyId.isNotEmpty()) {
        drmSessionManager.setMode(DefaultDrmSessionManager.MODE_QUERY, offlineAssetKeyId)
      }
    }

    return drmSessionManager
  }

  @OptIn(UnstableApi::class)
  override fun getDRMConfiguration(drmParams: NativeDrmParams): DrmConfiguration {
    val uuid = Util.getDrmUuid(drmParams.type ?: "widevine") ?: throw Error("DRM UUID is not set")

    val configurationBuilder =  DrmConfiguration.Builder(uuid)
      .setMultiSession(drmParams.multiSession == true)
      .setLicenseUri(drmParams.licenseUrl)

    drmParams.licenseHeaders?.let {
      configurationBuilder.setLicenseRequestHeaders(it)
    }

    return configurationBuilder.build()
  }
}
