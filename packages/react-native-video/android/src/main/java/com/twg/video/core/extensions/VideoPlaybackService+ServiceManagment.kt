package com.twg.video.core.extensions

import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Context.BIND_INCLUDE_CAPABILITIES
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.margelo.nitro.NitroModules
import com.margelo.nitro.video.HybridVideoPlayer
import com.twg.video.core.services.playback.VideoPlaybackService
import com.twg.video.core.services.playback.VideoPlaybackServiceConnection

fun VideoPlaybackService.Companion.startService(
  context: Context,
  serviceConnection: VideoPlaybackServiceConnection
) {
  val reactContext = NitroModules.applicationContext ?: return

  val intent = Intent(context, VideoPlaybackService::class.java)
  intent.action = VIDEO_PLAYBACK_SERVICE_INTERFACE

  // Starting a foreground service from the background is not allowed on API 31+
  // (ForegroundServiceStartNotAllowedException), and a start that is never promoted
  // crashes with ForegroundServiceDidNotStartInTimeException. Only start it while the
  // app is in the foreground; binding below still creates the service either way.
  try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (isAppInForeground()) {
        reactContext.startForegroundService(intent)
      }
    } else {
      reactContext.startService(intent)
    }
  } catch (e: Exception) {
    Log.w(TAG, "Could not start playback service, binding only", e)
  }

  val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    BIND_AUTO_CREATE or BIND_INCLUDE_CAPABILITIES
  } else {
    BIND_AUTO_CREATE
  }

  context.bindService(intent, serviceConnection, flags)
}

private const val TAG = "VideoPlaybackService"

private fun isAppInForeground(): Boolean {
  return try {
    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
  } catch (_: Exception) {
    false
  }
}

@OptIn(UnstableApi::class)
fun VideoPlaybackService.Companion.stopService(
  player: HybridVideoPlayer,
  serviceConnection: VideoPlaybackServiceConnection
) {
  try {
    // Unregister the player first; this might stop the service if no players remain
    serviceConnection.unregisterPlayer(player)
    // Ask service (if still connected) to stop when idle
    try { serviceConnection.serviceBinder?.service?.stopIfNoPlayers() } catch (_: Exception) {}
    // Then unbind
    NitroModules.applicationContext?.currentActivity?.unbindService(serviceConnection)
  } catch (_: Exception) {}
}
