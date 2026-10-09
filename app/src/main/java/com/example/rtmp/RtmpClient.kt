package com.example.rtmp

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Real YouTube RTMP publisher.
 *
 * RootEncoder performs the RTMP handshake, connect/createStream/publish command sequence
 * and waits for YouTube's NetStream.Publish.Start before reporting success.
 */
class RtmpClient(
    private val rtmpUrl: String,
    private val streamKey: String,
    private val listener: Listener? = null
) {
    interface Listener {
        fun onConnected()
        fun onStreaming()
        fun onDisconnected(reason: String)
        fun onError(error: Exception)
        fun onStatsUpdated(bitrateKbps: Int, fps: Int, droppedFrames: Long)
    }

    companion object {
        private const val TAG = "RtmpClient"
        private const val CONNECT_TIMEOUT_SECONDS = 25L
    }

    private val connectedLatch = CountDownLatch(1)
    private val connectionError = AtomicReference<String?>(null)

    private val rootClient = com.pedro.rtmp.rtmp.RtmpClient(object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            Log.i(TAG, "RTMP_CONNECTING: $url")
        }

        override fun onConnectionSuccess() {
            Log.i(TAG, "RTMP_PUBLISH_STARTED: YouTube accepted NetStream.Publish.Start")
            listener?.onConnected()
            listener?.onStreaming()
            connectedLatch.countDown()
        }

        override fun onConnectionFailed(reason: String) {
            Log.e(TAG, "RTMP_ERROR: $reason")
            connectionError.set(reason)
            connectedLatch.countDown()
            listener?.onError(IllegalStateException(reason))
        }

        override fun onDisconnect() {
            Log.w(TAG, "RTMP_DISCONNECTED")
            if (connectedLatch.count > 0) {
                connectionError.compareAndSet(
                    null,
                    "Disconnected before YouTube accepted the publish"
                )
                connectedLatch.countDown()
            }
            listener?.onDisconnected("RTMP disconnected")
        }

        override fun onAuthError() {
            val message = "YouTube RTMP authentication failed"
            connectionError.set(message)
            connectedLatch.countDown()
            listener?.onError(IllegalStateException(message))
        }

        override fun onAuthSuccess() {
            Log.i(TAG, "RTMP_AUTH_SUCCESS")
        }

        override fun onNewBitrate(bitrate: Long) {
            listener?.onStatsUpdated(bitrate.toInt(), 0, 0)
        }
    })

    @Volatile
    private var connected = false

    fun connect() {
        require(rtmpUrl.isNotBlank()) { "RTMP Server URL is empty" }
        require(streamKey.isNotBlank()) { "YouTube Stream Key is empty" }

        rootClient.setVideoCodec(VideoCodec.H264)
        rootClient.setAudioCodec(AudioCodec.AAC)
        rootClient.setReTries(0)
        rootClient.shouldSendPings(true)
        rootClient.setCheckServerAlive(true)

        val endpoint = buildEndpoint(rtmpUrl, streamKey)
        Log.i(TAG, "Connecting to YouTube endpoint: ${endpoint.substringBeforeLast('/')}/***")

        rootClient.connect(endpoint)

        if (!connectedLatch.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            rootClient.disconnect()
            throw IllegalStateException(
                "YouTube RTMP connection timed out. Check Server URL, Stream Key and YouTube Live Control Room."
            )
        }

        val error = connectionError.get()
        if (error != null) {
            rootClient.disconnect()
            throw IllegalStateException(error)
        }

        connected = true
        Log.i(TAG, "RTMP_CONNECTED + RTMP_PUBLISH_STARTED")
    }

    fun sendMetadata(width: Int, height: Int, fps: Int, audioSampleRate: Int = 44100) {
        rootClient.setVideoResolution(width.coerceAtLeast(2), height.coerceAtLeast(2))
        rootClient.setFps(fps.coerceIn(1, 60))
        rootClient.setAudioInfo(audioSampleRate, true)
        Log.d(TAG, "Metadata configured: ${width}x${height} @ ${fps}fps, audio=${audioSampleRate}Hz")
    }

    fun sendAvcSequenceHeader(sps: ByteArray, pps: ByteArray) {
        if (sps.isEmpty() || pps.isEmpty()) return
        rootClient.setVideoInfo(ByteBuffer.wrap(sps), ByteBuffer.wrap(pps), null)
    }

    fun sendAacSequenceHeader(audioSpecificConfig: ByteArray) {
        val (sampleRate, channels) = parseAudioSpecificConfig(audioSpecificConfig)
        rootClient.setAudioInfo(sampleRate, channels >= 2)
    }

    fun sendVideoFrame(naluOrSample: ByteArray, isKeyframe: Boolean, timestampMs: Long) {
        if (!connected || naluOrSample.isEmpty()) return

        val annexB = toAnnexB(naluOrSample)
        val buffer = ByteBuffer.wrap(annexB)
        val info = MediaCodec.BufferInfo().apply {
            set(
                0,
                annexB.size,
                timestampMs.coerceAtLeast(0L) * 1000L,
                if (isKeyframe) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            )
        }
        rootClient.sendVideo(buffer, info)
    }

    fun sendAudioFrame(rawAac: ByteArray, timestampMs: Long) {
        if (!connected || rawAac.isEmpty()) return

        val buffer = ByteBuffer.wrap(rawAac)
        val info = MediaCodec.BufferInfo().apply {
            set(0, rawAac.size, timestampMs.coerceAtLeast(0L) * 1000L, 0)
        }
        rootClient.sendAudio(buffer, info)
    }

    fun isConnected(): Boolean = connected && rootClient.isStreaming

    fun close() {
        connected = false
        rootClient.disconnect()
    }

    private fun buildEndpoint(serverUrl: String, key: String): String {
        val base = serverUrl.trim().trimEnd('/')
        val cleanKey = key.trim().trim('/')
        require(
            base.startsWith("rtmp://", true) || base.startsWith("rtmps://", true)
        ) {
            "RTMP Server URL must start with rtmp:// or rtmps://"
        }
        require(cleanKey.isNotEmpty()) { "Stream Key is empty" }
        return if (base.endsWith(cleanKey)) base else "$base/$cleanKey"
    }

    /**
     * MediaExtractor commonly returns AVC samples in MP4/AVCC form. Normalize
     * length-prefixed samples to Annex-B before passing them to the packetizer.
     */
    private fun toAnnexB(data: ByteArray): ByteArray {
        if (hasStartCode(data)) return data

        val out = java.io.ByteArrayOutputStream(data.size + 64)
        var offset = 0
        var parsedAny = false

        while (offset + 4 <= data.size) {
            val len = ((data[offset].toInt() and 0xff) shl 24) or
                ((data[offset + 1].toInt() and 0xff) shl 16) or
                ((data[offset + 2].toInt() and 0xff) shl 8) or
                (data[offset + 3].toInt() and 0xff)

            if (len <= 0 || offset + 4 + len > data.size) break

            out.write(byteArrayOf(0, 0, 0, 1))
            out.write(data, offset + 4, len)
            offset += 4 + len
            parsedAny = true
        }

        return if (parsedAny && offset == data.size) {
            out.toByteArray()
        } else {
            byteArrayOf(0, 0, 0, 1) + data
        }
    }

    private fun hasStartCode(data: ByteArray): Boolean =
        (data.size >= 4 &&
            data[0] == 0.toByte() && data[1] == 0.toByte() &&
            data[2] == 0.toByte() && data[3] == 1.toByte()) ||
        (data.size >= 3 &&
            data[0] == 0.toByte() && data[1] == 0.toByte() &&
            data[2] == 1.toByte())

    private fun parseAudioSpecificConfig(config: ByteArray): Pair<Int, Int> {
        if (config.size < 2) return 44100 to 2

        val b0 = config[0].toInt() and 0xff
        val b1 = config[1].toInt() and 0xff
        val sampleIndex = ((b0 and 0x07) shl 1) or ((b1 ushr 7) and 0x01)

        val sampleRates = intArrayOf(
            96000, 88200, 64000, 48000, 44100, 32000, 24000,
            22050, 16000, 12000, 11025, 8000, 7350
        )
        val sampleRate = sampleRates.getOrElse(sampleIndex) { 44100 }
        val channelConfig = (b1 ushr 3) and 0x0F
        val channels = when (channelConfig) {
            1 -> 1
            2 -> 2
            3 -> 3
            4 -> 4
            5 -> 5
            6 -> 6
            7 -> 8
            else -> 2
        }

        return sampleRate to channels
    }
}
