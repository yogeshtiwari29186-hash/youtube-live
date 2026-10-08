package com.example.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.example.model.VideoItem
import com.example.rtmp.RtmpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance streaming pipeline that reads local video files via MediaExtractor,
 * extracts H.264/AVC NAL units and AAC audio frames, normalizes timestamps into a continuous
 * monotonically increasing timeline, and streams them out via RtmpClient.
 *
 * When one video finishes, it immediately advances to the next video without closing
 * the RTMP connection or resetting stream timestamps, enabling uninterrupted multi-video
 * and looping broadcasts.
 */
class StreamingEngine(
    private val context: Context,
    private val rtmpClient: RtmpClient,
    private val listener: Listener
) {
    interface Listener {
        fun onVideoStarted(video: VideoItem, index: Int, total: Int)
        fun onVideoProgress(elapsedMs: Long, durationMs: Long)
        fun onVideoCompleted(video: VideoItem)
        fun onVideoError(video: VideoItem, error: Throwable)
        fun onStreamEnded()
    }

    companion object {
        private const val TAG = "StreamingEngine"
    }

    private val isStreaming = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var engineJob: Job? = null

    // Continuous timeline offsets
    private var baseTimestampMs: Long = 0L
    private var lastEmittedVideoPtsMs: Long = 0L
    private var lastEmittedAudioPtsMs: Long = 0L

    fun start(scope: CoroutineScope, videoProvider: suspend () -> VideoItem?) {
        if (isStreaming.getAndSet(true)) return
        isPaused.set(false)
        baseTimestampMs = 0L
        lastEmittedVideoPtsMs = 0L
        lastEmittedAudioPtsMs = 0L

        engineJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive && isStreaming.get()) {
                    val currentVideo = videoProvider()
                    if (currentVideo == null) {
                        Log.i(TAG, "No more videos to stream. Ending session.")
                        break
                    }

                    try {
                        streamSingleVideo(currentVideo)
                        listener.onVideoCompleted(currentVideo)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to stream video: ${currentVideo.title}, skipping to next", e)
                        listener.onVideoError(currentVideo, e)
                        // Brief safety delay before next video in case of corrupted file
                        SystemClock.sleep(500)
                    }

                    // Advance base timestamp for smooth seamless transition
                    val nextBase = Math.max(lastEmittedVideoPtsMs, lastEmittedAudioPtsMs) + 33L
                    baseTimestampMs = nextBase
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "Streaming engine cancelled")
            } finally {
                isStreaming.set(false)
                listener.onStreamEnded()
            }
        }
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
    }

    fun stop() {
        isStreaming.set(false)
        engineJob?.cancel()
        engineJob = null
    }

    private fun streamSingleVideo(video: VideoItem) {
        val uri = video.uri
        Log.i(TAG, "Opening video track for: ${video.title} (URI: $uri)")

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            extractor.release()
            throw RuntimeException("Cannot open video file: ${e.message}", e)
        }

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null

        val trackCount = extractor.trackCount
        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/") && videoTrackIndex == -1) {
                videoTrackIndex = i
                videoFormat = format
            } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                audioTrackIndex = i
                audioFormat = format
            }
        }

        if (videoTrackIndex == -1) {
            extractor.release()
            throw IllegalArgumentException("No video track found in ${video.title}")
        }

        // Parse video configuration (SPS/PPS)
        val width = videoFormat?.let {
            if (it.containsKey(MediaFormat.KEY_WIDTH)) it.getInteger(MediaFormat.KEY_WIDTH) else 1280
        } ?: 1280
        val height = videoFormat?.let {
            if (it.containsKey(MediaFormat.KEY_HEIGHT)) it.getInteger(MediaFormat.KEY_HEIGHT) else 720
        } ?: 720

        rtmpClient.sendMetadata(width, height, 30)

        // Try to get SPS / PPS from csd-0 / csd-1 if available
        if (videoFormat?.containsKey("csd-0") == true) {
            val csd0 = videoFormat.getByteBuffer("csd-0")
            val csd1 = if (videoFormat.containsKey("csd-1")) videoFormat.getByteBuffer("csd-1") else null
            if (csd0 != null) {
                val sps = extractNaluBytes(csd0)
                val pps = if (csd1 != null) extractNaluBytes(csd1) else sps
                rtmpClient.sendAvcSequenceHeader(sps, pps)
            }
        }

        // Audio configuration
        if (audioTrackIndex != -1 && audioFormat?.containsKey("csd-0") == true) {
            val audioCsd = audioFormat.getByteBuffer("csd-0")
            if (audioCsd != null) {
                val config = ByteArray(audioCsd.remaining())
                audioCsd.get(config)
                rtmpClient.sendAacSequenceHeader(config)
            }
        } else {
            // Default AAC-LC 44.1kHz Stereo config: 0x12, 0x10
            rtmpClient.sendAacSequenceHeader(byteArrayOf(0x12.toByte(), 0x10.toByte()))
        }

        // Select tracks
        extractor.selectTrack(videoTrackIndex)
        if (audioTrackIndex != -1) {
            extractor.selectTrack(audioTrackIndex)
        }

        val buffer = ByteBuffer.allocateDirect(1024 * 1024)
        var firstVideoSampleTimeUs: Long = -1L
        var firstAudioSampleTimeUs: Long = -1L

        var streamStartWallClock = SystemClock.elapsedRealtime()
        var firstPtsOffsetWallClock: Long = -1L

        val durationUs = if (video.durationMs > 0) video.durationMs * 1000L else 0L

        try {
            while (isStreaming.get()) {
                while (isPaused.get() && isStreaming.get()) {
                    SystemClock.sleep(100)
                }
                if (!isStreaming.get()) break

                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    Log.i(TAG, "End of stream reached for ${video.title}")
                    break
                }

                val trackIndex = extractor.sampleTrackIndex
                val sampleTimeUs = extractor.sampleTime
                val flags = extractor.sampleFlags

                if (trackIndex == videoTrackIndex) {
                    if (firstVideoSampleTimeUs == -1L) {
                        firstVideoSampleTimeUs = sampleTimeUs
                    }
                    val relativeTimeUs = Math.max(0L, sampleTimeUs - firstVideoSampleTimeUs)
                    val relativeTimeMs = relativeTimeUs / 1000L
                    val ptsMs = baseTimestampMs + relativeTimeMs
                    lastEmittedVideoPtsMs = ptsMs

                    // Wall-clock synchronization to stream in real-time
                    if (firstPtsOffsetWallClock == -1L) {
                        firstPtsOffsetWallClock = SystemClock.elapsedRealtime()
                    }
                    val elapsedWallClock = SystemClock.elapsedRealtime() - firstPtsOffsetWallClock
                    val timeAhead = relativeTimeMs - elapsedWallClock
                    if (timeAhead > 10) {
                        SystemClock.sleep(Math.min(timeAhead, 40L))
                    }

                    val isKeyframe = (flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    val frameBytes = ByteArray(sampleSize)
                    buffer.get(frameBytes, 0, sampleSize)

                    val cleanNalu = stripStartCode(frameBytes)
                    rtmpClient.sendVideoFrame(cleanNalu, isKeyframe, ptsMs)

                    listener.onVideoProgress(relativeTimeMs, video.durationMs)
                } else if (trackIndex == audioTrackIndex) {
                    if (firstAudioSampleTimeUs == -1L) {
                        firstAudioSampleTimeUs = sampleTimeUs
                    }
                    val relativeTimeUs = Math.max(0L, sampleTimeUs - firstAudioSampleTimeUs)
                    val relativeTimeMs = relativeTimeUs / 1000L
                    val ptsMs = baseTimestampMs + relativeTimeMs
                    lastEmittedAudioPtsMs = ptsMs

                    val audioBytes = ByteArray(sampleSize)
                    buffer.get(audioBytes, 0, sampleSize)
                    val cleanAudio = stripAdtsHeaderIfNeeded(audioBytes)
                    rtmpClient.sendAudioFrame(cleanAudio, ptsMs)
                }

                extractor.advance()
            }
        } finally {
            extractor.release()
        }
    }

    private fun extractNaluBytes(buffer: ByteBuffer): ByteArray {
        val dup = buffer.duplicate()
        val bytes = ByteArray(dup.remaining())
        dup.get(bytes)
        return stripStartCode(bytes)
    }

    private fun stripStartCode(data: ByteArray): ByteArray {
        var startOffset = 0
        if (data.size >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 0.toByte() && data[3] == 1.toByte()) {
            startOffset = 4
        } else if (data.size >= 3 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 1.toByte()) {
            startOffset = 3
        }
        return if (startOffset > 0) {
            data.copyOfRange(startOffset, data.size)
        } else {
            data
        }
    }

    private fun stripAdtsHeaderIfNeeded(data: ByteArray): ByteArray {
        // ADTS header is 7 or 9 bytes if starts with 0xFF 0xFx
        if (data.size > 7 && (data[0].toInt() and 0xFF) == 0xFF && (data[1].toInt() and 0xF0) == 0xF0) {
            val hasCrc = (data[1].toInt() and 0x01) == 0
            val headerSize = if (hasCrc) 9 else 7
            return if (data.size > headerSize) data.copyOfRange(headerSize, data.size) else data
        }
        return data
    }
}
