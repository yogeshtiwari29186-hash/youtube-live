package com.example.rtmp

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocketFactory

/**
 * Robust pure-Kotlin RTMP client implementation for streaming H.264 video & AAC audio to YouTube Live.
 * Handles:
 * - Plain RTMP (port 1935) & RTMPS (port 443 via SSL)
 * - Standard C0/C1/S0/S1/C2/S2 handshake
 * - Chunking (default 128 -> dynamic negotiated chunk size 4096)
 * - Connect ("connect"), ReleaseStream, FCPublish, CreateStream ("createStream"), Publish ("publish")
 * - @setDataFrame / onMetaData with SPS/PPS & audio sample rate
 * - Tagging and sending AVC/H.264 NALUs & AAC Audio Data with exact 24-bit timestamps
 * - Dynamic FPS & Bitrate calculation for metrics
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
        const val TYPE_SET_CHUNK_SIZE: Byte = 0x01
        const val TYPE_ABORT_MESSAGE: Byte = 0x02
        const val TYPE_ACKNOWLEDGEMENT: Byte = 0x03
        const val TYPE_USER_CONTROL: Byte = 0x04
        const val TYPE_WINDOW_ACK_SIZE: Byte = 0x05
        const val TYPE_SET_PEER_BANDWIDTH: Byte = 0x06
        const val TYPE_AUDIO: Byte = 0x08
        const val TYPE_VIDEO: Byte = 0x09
        const val TYPE_DATA_AMF0: Byte = 0x12
        const val TYPE_INVOKE_AMF0: Byte = 0x14

        const val CSID_COMMAND = 3
        const val CSID_AUDIO = 4
        const val CSID_VIDEO = 6
        const val CSID_DATA = 5

        const val OUT_CHUNK_SIZE = 4096
    }

    private var socket: Socket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null

    private val isRunning = AtomicBoolean(false)
    private val isPublished = AtomicBoolean(false)
    private var streamId = 1
    private var transactionId = AtomicInteger(1)

    // Stats
    private val bytesSent = AtomicLong(0)
    private val videoFramesSent = AtomicInteger(0)
    private val droppedFrames = AtomicLong(0)
    private var lastStatsTime = 0L
    private var lastBytesCount = 0L
    private var lastFramesCount = 0

    // Parsed components
    private var host: String = ""
    private var port: Int = 1935
    private var appName: String = "live2"
    private var isSsl: Boolean = false

    init {
        parseUrl(rtmpUrl)
    }

    private fun parseUrl(url: String) {
        val cleanUrl = url.trim()
        val isRtmps = cleanUrl.startsWith("rtmps://", ignoreCase = true)
        val isRtmp = cleanUrl.startsWith("rtmp://", ignoreCase = true)
        this.isSsl = isRtmps

        val prefixLen = if (isRtmps) 8 else if (isRtmp) 7 else 0
        val withoutProto = if (prefixLen > 0) cleanUrl.substring(prefixLen) else cleanUrl

        val slashIndex = withoutProto.indexOf('/')
        val hostPort = if (slashIndex != -1) withoutProto.substring(0, slashIndex) else withoutProto
        this.appName = if (slashIndex != -1) withoutProto.substring(slashIndex + 1).trimEnd('/') else "live2"

        if (hostPort.contains(":")) {
            val parts = hostPort.split(":")
            this.host = parts[0]
            this.port = parts[1].toIntOrNull() ?: if (isSsl) 443 else 1935
        } else {
            this.host = hostPort
            this.port = if (isSsl) 443 else 1935
        }
    }

    @Synchronized
    fun connect() {
        if (isRunning.get()) return
        Log.i(TAG, "Connecting to RTMP: host=$host port=$port app=$appName ssl=$isSsl")

        try {
            val sock = if (isSsl) {
                SSLSocketFactory.getDefault().createSocket()
            } else {
                Socket()
            }
            sock.tcpNoDelay = true
            sock.sendBufferSize = 64 * 1024
            sock.receiveBufferSize = 64 * 1024
            sock.connect(InetSocketAddress(host, port), 15000)
            socket = sock

            inStream = BufferedInputStream(sock.getInputStream(), 16 * 1024)
            outStream = BufferedOutputStream(sock.getOutputStream(), 16 * 1024)

            // Handshake
            doHandshake()

            // Negotiate output chunk size
            sendSetChunkSize(OUT_CHUNK_SIZE)

            // Send connect command
            sendConnectCommand()

            // Send createStream and publish
            sendCreateStream()

            isRunning.set(true)
            lastStatsTime = System.currentTimeMillis()
            lastBytesCount = 0L
            lastFramesCount = 0

            listener?.onConnected()
            isPublished.set(true)
            listener?.onStreaming()

            Log.i(TAG, "RTMP connection successfully published to stream: $appName/$streamKey")
        } catch (e: Exception) {
            Log.e(TAG, "RTMP connect error", e)
            close()
            listener?.onError(e)
            throw e
        }
    }

    private fun doHandshake() {
        val out = outStream ?: throw IllegalStateException("outStream null")
        val input = inStream ?: throw IllegalStateException("inStream null")

        // C0: 1 byte (version 3)
        out.write(3)

        // C1: 1536 bytes
        val c1 = ByteArray(1536)
        // 4 bytes timestamp (0) + 4 bytes zero + 1528 random bytes
        SecureRandom().nextBytes(c1)
        c1[0] = 0; c1[1] = 0; c1[2] = 0; c1[3] = 0
        c1[4] = 0; c1[5] = 0; c1[6] = 0; c1[7] = 0
        out.write(c1)
        out.flush()

        // Read S0 (1 byte)
        val s0 = input.read()
        if (s0 != 3) {
            throw java.io.IOException("Invalid RTMP handshake server version: $s0")
        }

        // Read S1 (1536 bytes)
        val s1 = ByteArray(1536)
        readFully(input, s1)

        // C2: Echo S1
        out.write(s1)
        out.flush()

        // Read S2 (1536 bytes)
        val s2 = ByteArray(1536)
        readFully(input, s2)
    }

    private fun sendSetChunkSize(chunkSize: Int) {
        val payload = ByteBuffer.allocate(4).putInt(chunkSize).array()
        sendChunk(csid = 2, messageType = TYPE_SET_CHUNK_SIZE, streamId = 0, timestamp = 0, payload = payload)
    }

    private fun sendConnectCommand() {
        val baos = ByteArrayOutputStream()
        Amf0.writeString(baos, "connect")
        Amf0.writeNumber(baos, transactionId.getAndIncrement().toDouble())

        // Command object
        Amf0.writeObjectHeader(baos)
        Amf0.writeObjectProperty(baos, "app") { Amf0.writeString(baos, appName) }
        Amf0.writeObjectProperty(baos, "flashVer") { Amf0.writeString(baos, "FMLE/3.0 (compatible; LocalStream Live 1.0)") }
        Amf0.writeObjectProperty(baos, "tcUrl") { Amf0.writeString(baos, "${if (isSsl) "rtmps" else "rtmp"}://$host:$port/$appName") }
        Amf0.writeObjectProperty(baos, "fpad") { Amf0.writeBoolean(baos, false) }
        Amf0.writeObjectProperty(baos, "capabilities") { Amf0.writeNumber(baos, 15.0) }
        Amf0.writeObjectProperty(baos, "audioCodecs") { Amf0.writeNumber(baos, 0x0400.toDouble()) } // AAC
        Amf0.writeObjectProperty(baos, "videoCodecs") { Amf0.writeNumber(baos, 0x0080.toDouble()) } // H.264
        Amf0.writeObjectProperty(baos, "videoFunction") { Amf0.writeNumber(baos, 1.0) }
        Amf0.writeObjectEnd(baos)

        sendChunk(csid = CSID_COMMAND, messageType = TYPE_INVOKE_AMF0, streamId = 0, timestamp = 0, payload = baos.toByteArray())

        // Optional ReleaseStream and FCPublish (used widely by YouTube RTMP ingest)
        sendReleaseStream()
        sendFcPublish()
    }

    private fun sendReleaseStream() {
        val baos = ByteArrayOutputStream()
        Amf0.writeString(baos, "releaseStream")
        Amf0.writeNumber(baos, transactionId.getAndIncrement().toDouble())
        Amf0.writeNull(baos)
        Amf0.writeString(baos, streamKey)
        sendChunk(csid = CSID_COMMAND, messageType = TYPE_INVOKE_AMF0, streamId = 0, timestamp = 0, payload = baos.toByteArray())
    }

    private fun sendFcPublish() {
        val baos = ByteArrayOutputStream()
        Amf0.writeString(baos, "FCPublish")
        Amf0.writeNumber(baos, transactionId.getAndIncrement().toDouble())
        Amf0.writeNull(baos)
        Amf0.writeString(baos, streamKey)
        sendChunk(csid = CSID_COMMAND, messageType = TYPE_INVOKE_AMF0, streamId = 0, timestamp = 0, payload = baos.toByteArray())
    }

    private fun sendCreateStream() {
        val baos = ByteArrayOutputStream()
        Amf0.writeString(baos, "createStream")
        Amf0.writeNumber(baos, transactionId.getAndIncrement().toDouble())
        Amf0.writeNull(baos)
        sendChunk(csid = CSID_COMMAND, messageType = TYPE_INVOKE_AMF0, streamId = 0, timestamp = 0, payload = baos.toByteArray())

        // Immediately send publish on streamId 1 (Standard RTMP flow for automated ingestion)
        sendPublish(streamId = 1)
    }

    private fun sendPublish(streamId: Int) {
        val baos = ByteArrayOutputStream()
        Amf0.writeString(baos, "publish")
        Amf0.writeNumber(baos, transactionId.getAndIncrement().toDouble())
        Amf0.writeNull(baos)
        Amf0.writeString(baos, streamKey)
        Amf0.writeString(baos, "live")
        sendChunk(csid = CSID_COMMAND, messageType = TYPE_INVOKE_AMF0, streamId = streamId, timestamp = 0, payload = baos.toByteArray())
    }

    /**
     * Send @setDataFrame onMetaData describing video & audio
     */
    fun sendMetadata(width: Int, height: Int, fps: Int, audioSampleRate: Int = 44100) {
        val baos = ByteArrayOutputStream()
        Amf0.writeString(baos, "@setDataFrame")
        Amf0.writeString(baos, "onMetaData")
        Amf0.writeEcmaArrayHeader(baos, 8)
        Amf0.writeObjectProperty(baos, "duration") { Amf0.writeNumber(baos, 0.0) }
        Amf0.writeObjectProperty(baos, "width") { Amf0.writeNumber(baos, width.toDouble()) }
        Amf0.writeObjectProperty(baos, "height") { Amf0.writeNumber(baos, height.toDouble()) }
        Amf0.writeObjectProperty(baos, "videodatarate") { Amf0.writeNumber(baos, 2500.0) }
        Amf0.writeObjectProperty(baos, "framerate") { Amf0.writeNumber(baos, fps.toDouble()) }
        Amf0.writeObjectProperty(baos, "videocodecid") { Amf0.writeNumber(baos, 7.0) } // AVC (H.264)
        Amf0.writeObjectProperty(baos, "audiodatarate") { Amf0.writeNumber(baos, 128.0) }
        Amf0.writeObjectProperty(baos, "audiocodecid") { Amf0.writeNumber(baos, 10.0) } // AAC
        Amf0.writeObjectEnd(baos)

        sendChunk(csid = CSID_DATA, messageType = TYPE_DATA_AMF0, streamId = 1, timestamp = 0, payload = baos.toByteArray())
    }

    /**
     * Send AVC / H.264 Sequence Header (AVCDecoderConfigurationRecord) containing SPS and PPS.
     */
    fun sendAvcSequenceHeader(sps: ByteArray, pps: ByteArray) {
        val baos = ByteArrayOutputStream()
        // Video Tag Header: FrameType=1(Keyframe), CodecID=7(AVC) -> 0x17
        baos.write(0x17)
        // AVCPacketType = 0 (AVC sequence header)
        baos.write(0x00)
        // CompositionTime = 0 (3 bytes)
        baos.write(0x00); baos.write(0x00); baos.write(0x00)

        // AVCDecoderConfigurationRecord
        baos.write(0x01) // configurationVersion = 1
        baos.write(sps.getOrElse(1) { 0x42.toByte() }.toInt()) // AVCProfileIndication
        baos.write(sps.getOrElse(2) { 0x00.toByte() }.toInt()) // profile_compatibility
        baos.write(sps.getOrElse(3) { 0x1E.toByte() }.toInt()) // AVCLevelIndication
        baos.write(0xFF) // 6 bits reserved (111111) + lengthSizeMinusOne (3 for 4-byte NALU len) -> 0xFF

        // SPS
        baos.write(0xE1) // 3 bits reserved (111) + numOfSequenceParameterSets (1)
        baos.write((sps.size shr 8) and 0xFF)
        baos.write(sps.size and 0xFF)
        baos.write(sps)

        // PPS
        baos.write(0x01) // numOfPictureParameterSets = 1
        baos.write((pps.size shr 8) and 0xFF)
        baos.write(pps.size and 0xFF)
        baos.write(pps)

        val payload = baos.toByteArray()
        sendChunk(csid = CSID_VIDEO, messageType = TYPE_VIDEO, streamId = 1, timestamp = 0, payload = payload)
    }

    /**
     * Send H.264 Video NALU (Keyframe or Interframe)
     */
    fun sendVideoFrame(nalu: ByteArray, isKeyframe: Boolean, timestampMs: Long) {
        if (!isRunning.get()) return

        val baos = ByteArrayOutputStream()
        // FrameType: 1=Keyframe, 2=Interframe. CodecID: 7=AVC
        val header = if (isKeyframe) 0x17 else 0x27
        baos.write(header)
        // AVCPacketType = 1 (AVC NALU)
        baos.write(0x01)
        // CompositionTime = 0 (3 bytes)
        baos.write(0x00); baos.write(0x00); baos.write(0x00)

        // NALU with 4-byte length prefix
        val len = nalu.size
        baos.write((len shr 24) and 0xFF)
        baos.write((len shr 16) and 0xFF)
        baos.write((len shr 8) and 0xFF)
        baos.write(len and 0xFF)
        baos.write(nalu)

        val payload = baos.toByteArray()
        sendChunk(csid = CSID_VIDEO, messageType = TYPE_VIDEO, streamId = 1, timestamp = timestampMs.toInt(), payload = payload)

        videoFramesSent.incrementAndGet()
        updateStats()
    }

    /**
     * Send AAC Sequence Header (AudioSpecificConfig)
     * Typically 2 bytes: e.g. AAC-LC (2), 44100Hz index (4), stereo (2) -> 0x12 0x10
     */
    fun sendAacSequenceHeader(audioSpecificConfig: ByteArray) {
        val baos = ByteArrayOutputStream()
        // SoundFormat=10(AAC), SoundRate=3(44kHz), SoundSize=1(16-bit), SoundType=1(Stereo) -> 0xAF
        baos.write(0xAF)
        // AACPacketType = 0 (AAC sequence header)
        baos.write(0x00)
        baos.write(audioSpecificConfig)

        val payload = baos.toByteArray()
        sendChunk(csid = CSID_AUDIO, messageType = TYPE_AUDIO, streamId = 1, timestamp = 0, payload = payload)
    }

    /**
     * Send AAC Audio Raw Frame
     */
    fun sendAudioFrame(rawAac: ByteArray, timestampMs: Long) {
        if (!isRunning.get()) return

        val baos = ByteArrayOutputStream()
        baos.write(0xAF) // AAC, 44kHz, 16bit, stereo
        baos.write(0x01) // AAC raw
        baos.write(rawAac)

        val payload = baos.toByteArray()
        sendChunk(csid = CSID_AUDIO, messageType = TYPE_AUDIO, streamId = 1, timestamp = timestampMs.toInt(), payload = payload)
        updateStats()
    }

    private fun updateStats() {
        val now = System.currentTimeMillis()
        if (now - lastStatsTime >= 1000) {
            val elapsedSec = (now - lastStatsTime) / 1000.0
            val currentBytes = bytesSent.get()
            val currentFrames = videoFramesSent.get()

            val diffBytes = currentBytes - lastBytesCount
            val diffFrames = currentFrames - lastFramesCount

            val kbps = ((diffBytes * 8) / (elapsedSec * 1000)).toInt()
            val fps = (diffFrames / elapsedSec).toInt()

            lastStatsTime = now
            lastBytesCount = currentBytes
            lastFramesCount = currentFrames

            listener?.onStatsUpdated(kbps, fps, droppedFrames.get())
        }
    }

    @Synchronized
    private fun sendChunk(csid: Int, messageType: Byte, streamId: Int, timestamp: Int, payload: ByteArray) {
        val out = outStream ?: return
        try {
            val length = payload.size
            var offset = 0

            // First chunk: Chunk Type 0 (full header 11 bytes)
            val chunk0Header = ByteArray(12)
            // fmt=0 (2 bits) + csid (6 bits)
            chunk0Header[0] = (0x00 or (csid and 0x3F)).toByte()
            // 24-bit timestamp
            chunk0Header[1] = ((timestamp shr 16) and 0xFF).toByte()
            chunk0Header[2] = ((timestamp shr 8) and 0xFF).toByte()
            chunk0Header[3] = (timestamp and 0xFF).toByte()
            // 24-bit message length
            chunk0Header[4] = ((length shr 16) and 0xFF).toByte()
            chunk0Header[5] = ((length shr 8) and 0xFF).toByte()
            chunk0Header[6] = (length and 0xFF).toByte()
            // 8-bit message type ID
            chunk0Header[7] = messageType
            // 32-bit message stream ID (little-endian)
            chunk0Header[8] = (streamId and 0xFF).toByte()
            chunk0Header[9] = ((streamId shr 8) and 0xFF).toByte()
            chunk0Header[10] = ((streamId shr 16) and 0xFF).toByte()
            chunk0Header[11] = ((streamId shr 24) and 0xFF).toByte()

            out.write(chunk0Header)
            bytesSent.addAndGet(chunk0Header.size.toLong())

            val firstChunkLen = Math.min(length, OUT_CHUNK_SIZE)
            out.write(payload, 0, firstChunkLen)
            bytesSent.addAndGet(firstChunkLen.toLong())
            offset += firstChunkLen

            // Remaining chunks: Chunk Type 3 (fmt=3, 1 byte header)
            val chunk3Header = (0xC0 or (csid and 0x3F)).toByte()
            while (offset < length) {
                out.write(chunk3Header.toInt())
                bytesSent.incrementAndGet()
                val chunkLen = Math.min(length - offset, OUT_CHUNK_SIZE)
                out.write(payload, offset, chunkLen)
                bytesSent.addAndGet(chunkLen.toLong())
                offset += chunkLen
            }

            out.flush()
        } catch (e: Exception) {
            droppedFrames.incrementAndGet()
            Log.e(TAG, "Error sending RTMP chunk: ${e.message}")
            throw e
        }
    }

    @Synchronized
    fun close() {
        isRunning.set(false)
        isPublished.set(false)
        try {
            outStream?.close()
        } catch (_: Exception) {}
        try {
            inStream?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inStream = null
        outStream = null
        listener?.onDisconnected("Closed")
    }

    fun isConnected(): Boolean = isRunning.get() && socket?.isConnected == true && !socket!!.isClosed

    private fun readFully(stream: InputStream, b: ByteArray) {
        var offset = 0
        while (offset < b.size) {
            val count = stream.read(b, offset, b.size - offset)
            if (count < 0) throw java.io.EOFException("Unexpected EOF from RTMP stream")
            offset += count
        }
    }
}
