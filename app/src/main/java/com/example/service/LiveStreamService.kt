package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.engine.StreamingEngine
import com.example.model.StreamConfig
import com.example.model.StreamConnectionState
import com.example.model.StreamMetrics
import com.example.model.VideoItem
import com.example.playlist.PlaylistManager
import com.example.rtmp.RtmpClient
import com.example.security.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground Service that handles the complete YouTube Live RTMP streaming lifecycle.
 * - Runs reliably in the background when the user minimizes or locks the phone.
 * - Holds a CPU Partial WakeLock to prevent device sleep during active live streaming.
 * - Monitors network connectivity and executes exponential backoff auto-reconnects.
 * - Emits real-time state and metrics to UI and persistent notification.
 */
class LiveStreamService : Service() {

    companion object {
        private const val TAG = "LiveStreamService"
        private const val CHANNEL_ID = "localstream_live_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.service.ACTION_RESUME"

        // Singleton state flow for UI observing service without mandatory binding
        private val _serviceState = MutableStateFlow(StreamConnectionState.OFFLINE)
        val serviceState: StateFlow<StreamConnectionState> = _serviceState.asStateFlow()

        private val _serviceMetrics = MutableStateFlow(StreamMetrics())
        val serviceMetrics: StateFlow<StreamMetrics> = _serviceMetrics.asStateFlow()

        private val _currentStreamingVideo = MutableStateFlow<VideoItem?>(null)
        val currentStreamingVideo: StateFlow<VideoItem?> = _currentStreamingVideo.asStateFlow()

        private val _streamStatusMessage = MutableStateFlow("Ready to stream")
        val streamStatusMessage: StateFlow<String> = _streamStatusMessage.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun pauseStream(context: Context) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeStream(context: Context) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        val service: LiveStreamService get() = this@LiveStreamService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var wakeLock: PowerManager.WakeLock? = null
    private var rtmpClient: RtmpClient? = null
    private var streamingEngine: StreamingEngine? = null

    private lateinit var playlistManager: PlaylistManager
    private lateinit var secureStorage: SecureStorage
    private lateinit var notificationManager: NotificationManager

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var streamStartTime = 0L
    private var reconnectAttempts = 0
    private var isIntentionalStop = false

    override fun onCreate() {
        super.onCreate()
        playlistManager = PlaylistManager.getInstance(applicationContext)
        secureStorage = SecureStorage(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
        acquireWakeLock()
        registerNetworkCallback()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> {
                startForegroundNotification("Initializing YouTube Live stream...")
                startStreamingPipeline()
            }
            ACTION_STOP -> {
                stopStreamingPipeline("User stopped LIVE")
            }
            ACTION_PAUSE -> {
                streamingEngine?.pause()
                _serviceState.value = StreamConnectionState.PAUSED
                updateNotification("Live Stream Paused", "Tap Resume to continue streaming")
            }
            ACTION_RESUME -> {
                streamingEngine?.resume()
                _serviceState.value = StreamConnectionState.LIVE
                updateNotification("Streaming to YouTube Live", _currentStreamingVideo.value?.title ?: "Playing playlist")
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification(status: String) {
        val notification = buildNotification("LocalStream Live", status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startStreamingPipeline() {
        isIntentionalStop = false
        val rtmpUrl = secureStorage.getRtmpUrl()
        val streamKey = secureStorage.getStreamKey()

        if (streamKey.isBlank()) {
            _serviceState.value = StreamConnectionState.ERROR
            _streamStatusMessage.value = "Error: Stream Key is missing. Configure in YouTube Setup."
            updateNotification("Configuration Error", "YouTube Stream Key is missing!")
            stopSelf()
            return
        }

        val videos = playlistManager.playlist.value
        if (videos.isEmpty()) {
            _serviceState.value = StreamConnectionState.ERROR
            _streamStatusMessage.value = "Error: Playlist is empty. Please select video files."
            updateNotification("Playlist Empty", "Please select local video files to stream.")
            stopSelf()
            return
        }

        _serviceState.value = StreamConnectionState.CONNECTING
        _streamStatusMessage.value = "Connecting to YouTube RTMP server..."
        updateNotification("Connecting to YouTube Live...", "Establishing RTMP handshake...")

        serviceScope.launch(Dispatchers.IO) {
            connectAndStream(rtmpUrl, streamKey)
        }
    }

    private suspend fun connectAndStream(rtmpUrl: String, streamKey: String) {
        try {
            cleanupActiveStream()

            val client = RtmpClient(
                rtmpUrl = rtmpUrl,
                streamKey = streamKey,
                listener = object : RtmpClient.Listener {
                    override fun onConnected() {
                        _serviceState.value = StreamConnectionState.CONNECTED
                        _streamStatusMessage.value = "Connected to RTMP ingest"
                    }

                    override fun onStreaming() {
                        _serviceState.value = StreamConnectionState.LIVE
                        _streamStatusMessage.value = "Streaming LIVE to YouTube"
                        streamStartTime = System.currentTimeMillis()
                        reconnectAttempts = 0
                    }

                    override fun onDisconnected(reason: String) {
                        Log.w(TAG, "RTMP disconnected: $reason")
                        if (!isIntentionalStop) {
                            handleStreamDisconnect()
                        }
                    }

                    override fun onError(error: Exception) {
                        Log.e(TAG, "RTMP error: ${error.message}", error)
                        if (!isIntentionalStop) {
                            handleStreamDisconnect()
                        }
                    }

                    override fun onStatsUpdated(bitrateKbps: Int, fps: Int, droppedFrames: Long) {
                        val durationSec = if (streamStartTime > 0) (System.currentTimeMillis() - streamStartTime) / 1000 else 0
                        val currentMetrics = _serviceMetrics.value
                        _serviceMetrics.value = currentMetrics.copy(
                            bitrateKbps = bitrateKbps,
                            fps = fps,
                            droppedFrames = droppedFrames,
                            streamDurationSeconds = durationSec
                        )
                    }
                }
            )
            rtmpClient = client
            client.connect()

            // Initialize engine
            val engine = StreamingEngine(
                context = applicationContext,
                rtmpClient = client,
                listener = object : StreamingEngine.Listener {
                    override fun onVideoStarted(video: VideoItem, index: Int, total: Int) {
                        _currentStreamingVideo.value = video
                        updateNotification("LIVE ● Streaming to YouTube", "${video.title} (${index + 1}/$total)")
                    }

                    override fun onVideoProgress(elapsedMs: Long, durationMs: Long) {
                        val currentMetrics = _serviceMetrics.value
                        _serviceMetrics.value = currentMetrics.copy(
                            currentVideoElapsedMs = elapsedMs,
                            currentVideoDurationMs = durationMs
                        )
                    }

                    override fun onVideoCompleted(video: VideoItem) {
                        Log.i(TAG, "Completed video: ${video.title}")
                    }

                    override fun onVideoError(video: VideoItem, error: Throwable) {
                        Log.w(TAG, "Error playing video ${video.title}, advancing: ${error.message}")
                        _streamStatusMessage.value = "Warning: Skipped corrupted file: ${video.title}"
                    }

                    override fun onStreamEnded() {
                        Log.i(TAG, "Streaming playlist ended")
                        if (!isIntentionalStop) {
                            stopStreamingPipeline("Playlist ended")
                        }
                    }
                }
            )
            streamingEngine = engine

            // Start reading and streaming
            engine.start(serviceScope) {
                // Returns current video and advances to next
                val next = playlistManager.getCurrentVideo()
                playlistManager.nextVideo()
                next
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect and stream", e)
            if (!isIntentionalStop) {
                handleStreamDisconnect()
            }
        }
    }

    private fun handleStreamDisconnect() {
        if (isIntentionalStop) return
        _serviceState.value = StreamConnectionState.RECONNECTING
        _streamStatusMessage.value = "Connection lost. Reconnecting (attempt ${reconnectAttempts + 1})..."
        updateNotification("Reconnecting...", "Connection lost. Retrying YouTube stream...")

        serviceScope.launch {
            reconnectAttempts++
            val backoffMs = Math.min(30000L, 2000L * (1L shl Math.min(reconnectAttempts, 4)))
            Log.i(TAG, "Waiting ${backoffMs}ms before reconnecting...")
            delay(backoffMs)

            if (!isIntentionalStop && _serviceState.value == StreamConnectionState.RECONNECTING) {
                val rtmpUrl = secureStorage.getRtmpUrl()
                val streamKey = secureStorage.getStreamKey()
                connectAndStream(rtmpUrl, streamKey)
            }
        }
    }

    private fun stopStreamingPipeline(reason: String) {
        isIntentionalStop = true
        _serviceState.value = StreamConnectionState.OFFLINE
        _streamStatusMessage.value = reason
        _serviceMetrics.value = StreamMetrics()
        _currentStreamingVideo.value = null

        cleanupActiveStream()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupActiveStream() {
        try {
            streamingEngine?.stop()
        } catch (_: Exception) {}
        streamingEngine = null

        try {
            rtmpClient?.close()
        } catch (_: Exception) {}
        rtmpClient = null
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LiveStreamService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isLive = _serviceState.value == StreamConnectionState.LIVE

        val pauseResumeIntent = Intent(this, LiveStreamService::class.java).apply {
            action = if (isLive) ACTION_PAUSE else ACTION_RESUME
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this, 2, pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                if (isLive) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isLive) "Pause" else "Resume",
                pauseResumePendingIntent
            )
            .addAction(android.R.drawable.ic_delete, "Stop LIVE", stopPendingIntent)

        return builder.build()
    }

    private fun updateNotification(title: String, text: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LocalStream Live Broadcast",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live streaming status, controls, and video progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocalStreamLive:StreamingWakeLock").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours safeguard
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release WakeLock", e)
        }
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (_serviceState.value == StreamConnectionState.RECONNECTING) {
                        Log.i(TAG, "Network became available; triggering immediate reconnect")
                        serviceScope.launch {
                            val rtmpUrl = secureStorage.getRtmpUrl()
                            val streamKey = secureStorage.getStreamKey()
                            connectAndStream(rtmpUrl, streamKey)
                        }
                    }
                }

                override fun onLost(network: Network) {
                    if (_serviceState.value == StreamConnectionState.LIVE) {
                        Log.w(TAG, "Network lost while streaming")
                        handleStreamDisconnect()
                    }
                }
            }
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register NetworkCallback", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isIntentionalStop = true
        cleanupActiveStream()
        releaseWakeLock()
        networkCallback?.let {
            try { connectivityManager?.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        serviceScope.cancel()
    }
}
