package com.twg.video.core.services.playback

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.SimpleBitmapLoader
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.margelo.nitro.NitroModules
import com.margelo.nitro.video.HybridVideoPlayer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VideoPlaybackServiceBinder(val service: VideoPlaybackService): Binder()

@OptIn(UnstableApi::class)
class VideoPlaybackService : MediaSessionService() {
  private var mediaSessionsList = mutableMapOf<HybridVideoPlayer, MediaSession>()
  private var binder = VideoPlaybackServiceBinder(this)
  private var sourceActivity: Class<Activity>? = null // retained for future deep-links; currently unused

  override fun onCreate() {
    super.onCreate()
    setMediaNotificationProvider(CustomMediaNotificationProvider(this))
  }

  // Player Registry
  fun registerPlayer(player: HybridVideoPlayer, from: Class<Activity>) {
    if (mediaSessionsList.containsKey(player)) {
      return
    }
    sourceActivity = from

    val exoPlayer = player.playerOrNull ?: return
    val builder = MediaSession.Builder(this, exoPlayer)
      .setId("RNVideoPlaybackService_" + player.hashCode())
      .setCallback(VideoPlaybackCallback())

    // Ensure tapping the notification opens the app via sessionActivity
    try {
      val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
      if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
          this,
          0,
          launchIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setSessionActivity(contentIntent)
      }
    } catch (_: Exception) {}


    val mediaSession = builder.build()

    mediaSessionsList[player] = mediaSession
    addSession(mediaSession)
  }

  fun unregisterPlayer(player: HybridVideoPlayer) {
    val session = mediaSessionsList.remove(player)
    session?.release()
    stopIfNoPlayers()
  }

  fun updatePlayerPreferences(player: HybridVideoPlayer) {
    val session = mediaSessionsList[player]
    if (session == null) {
      // If not registered but now needs it, register
      if (player.playInBackground || player.showNotificationControls) {
        val activity = try { NitroModules.applicationContext?.currentActivity } catch (_: Exception) { null }
        if (activity != null) registerPlayer(player, activity.javaClass)
      }
      return
    }

    // If no longer needs registration, unregister and possibly stop service
    if (!player.playInBackground && !player.showNotificationControls) {
      unregisterPlayer(player)
      stopIfNoPlayers()
      return
    }
  }

  // Callbacks

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Promote right away so the 5 s startForegroundService() window can never be missed,
    // even when Media3 has no session/activity yet and skips its own promotion. Only for
    // our own start (startService() sets this action): Media3's self-start and media-action
    // intents carry no such action, and a sticky restart delivers a null intent.
    if (intent?.action == VIDEO_PLAYBACK_SERVICE_INTERFACE) {
      promoteToForeground()
    }
    return super.onStartCommand(intent, flags, startId)
  }

  private fun promoteToForeground() {
    try {
      ServiceCompat.startForeground(
        this,
        DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
        currentOrPlaceholderNotification(),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
      )
    } catch (e: Exception) {
      Log.w(TAG, "Could not promote playback service to foreground", e)
    }
  }

  // Reuse the media notification Media3 already posted (a repeat start must not replace
  // it with the placeholder); otherwise build a minimal one on Media3's channel. Media3
  // posts its real notification under the same id, replacing the placeholder.
  private fun currentOrPlaceholderNotification(): Notification {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val existing = try {
        getSystemService(NotificationManager::class.java)?.activeNotifications
          ?.firstOrNull {
            it.id == DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID &&
              NotificationCompat.getChannelId(it.notification) ==
              DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID
          }
          ?.notification
      } catch (_: Exception) { null }
      if (existing != null) return existing
    }

    NotificationManagerCompat.from(this).createNotificationChannel(
      NotificationChannelCompat.Builder(
        DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID,
        NotificationManagerCompat.IMPORTANCE_LOW
      )
        .setName(getString(DefaultMediaNotificationProvider.DEFAULT_CHANNEL_NAME_RESOURCE_ID))
        .build()
    )

    val appName = try {
      packageManager.getApplicationLabel(applicationInfo).toString()
    } catch (_: Exception) { null }

    return NotificationCompat.Builder(this, DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID)
      .setSmallIcon(androidx.media3.session.R.drawable.media3_notification_small_icon)
      .setContentTitle(appName)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  override fun onBind(intent: Intent?): IBinder {
    super.onBind(intent)
    return binder
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    stopForegroundSafely()
    cleanup()
    stopSelf()
  }

  override fun onDestroy() {
    stopForegroundSafely()
    cleanup()
    super.onDestroy()
  }

  private fun stopForegroundSafely() {
    try {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } catch (_: Exception) {}
  }

  private fun cleanup() {
    stopForegroundSafely()
    stopSelf()
    // Snapshot first: release() can re-enter unregisterPlayer() and mutate the map
    // (ConcurrentModificationException).
    val sessions = mediaSessionsList.values.toList()
    mediaSessionsList.clear()
    sessions.forEach { it.release() }
  }

  // Stop the service if there are no active media sessions (no players need it)
  fun stopIfNoPlayers() {
    if (mediaSessionsList.isEmpty()) {
      cleanup()
    }
  }

  companion object {
    private const val TAG = "VideoPlaybackService"
    const val VIDEO_PLAYBACK_SERVICE_INTERFACE = SERVICE_INTERFACE
  }
}
