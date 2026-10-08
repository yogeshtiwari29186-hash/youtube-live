package com.example.model

import android.net.Uri

data class VideoItem(
    val id: String,
    val uriString: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0
) {
    val uri: Uri get() = Uri.parse(uriString)

    fun formattedDuration(): String {
        if (durationMs <= 0) return "--:--"
        val totalSec = durationMs / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun formattedSize(): String {
        if (sizeBytes <= 0) return ""
        val mb = sizeBytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }
}

enum class StreamConnectionState {
    OFFLINE,
    CONNECTING,
    CONNECTED,
    LIVE,
    PAUSED,
    RECONNECTING,
    ERROR
}

data class StreamMetrics(
    val bitrateKbps: Int = 0,
    val fps: Int = 0,
    val droppedFrames: Long = 0,
    val totalBytesSent: Long = 0,
    val streamDurationSeconds: Long = 0,
    val currentVideoElapsedMs: Long = 0,
    val currentVideoDurationMs: Long = 0
)

data class StreamConfig(
    val rtmpUrl: String = "rtmp://a.rtmp.youtube.com/live2",
    val streamKey: String = "",
    val resolutionWidth: Int = 1280,
    val resolutionHeight: Int = 720,
    val targetFps: Int = 30,
    val targetBitrateKbps: Int = 2500,
    val isLoopEnabled: Boolean = true,
    val autoReconnect: Boolean = true
)
