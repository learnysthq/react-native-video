package com.twg.video.core.utils

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.margelo.nitro.video.AudioTrack
import com.margelo.nitro.video.VideoTrack

@UnstableApi
object VideoTrackUtils {
    fun getAvailableVideoTracks(player: ExoPlayer): Array<VideoTrack> {
        return Threading.runOnMainThreadSync {
            val tracks = mutableListOf<VideoTrack>()
            val currentTracks = player.currentTracks
            var globalTrackIndex = 0

            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_VIDEO) {
                    for (trackIndex in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(trackIndex)
                        val trackId = format.id ?: "video-$globalTrackIndex"
                        val width = format.width.toDouble()
                        val height = format.height.toDouble()
                        val bitrate = format.bitrate.toDouble()
                        val isSelected = trackGroup.isTrackSelected(trackIndex)

                        tracks.add(
                            VideoTrack(
                                id = trackId,
                                width = width,
                                height = height,
                                bitrate = bitrate,
                                selected = isSelected
                            )
                        )

                        globalTrackIndex++
                    }
                }
            }

            tracks.toTypedArray()
        }
    }

    fun getAvailableAudioTracks(player: ExoPlayer): Array<AudioTrack> {
        return Threading.runOnMainThreadSync {
            val tracks = mutableListOf<AudioTrack>()
            val currentTracks = player.currentTracks
            var globalTrackIndex = 0

            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                    for (trackIndex in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(trackIndex)
                        val trackId = format.id ?: "audio-$globalTrackIndex"
                        val label = format.label ?: "Audio ${globalTrackIndex + 1}"
                        val language = format.language
                        val bitrate = format.bitrate.toDouble()
                        val isSelected = trackGroup.isTrackSelected(trackIndex)

                        tracks.add(
                            AudioTrack(
                                id = trackId,
                                label = label,
                                language = language,
                                bitrate = bitrate,
                                selected = isSelected
                            )
                        )

                        globalTrackIndex++
                    }
                }
            }

            tracks.toTypedArray()
        }
    }
}
