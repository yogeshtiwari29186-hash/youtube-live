package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.StreamConfig
import com.example.model.StreamConnectionState
import com.example.model.StreamMetrics
import com.example.model.VideoItem
import com.example.playlist.PlaylistManager
import com.example.rtmp.RtmpClient
import com.example.security.SecureStorage
import com.example.service.LiveStreamService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistManager = PlaylistManager.getInstance(application)
    private val secureStorage = SecureStorage(application)
    private val setupPrefs = application.getSharedPreferences("localstream_setup", Context.MODE_PRIVATE)

    fun isPermissionSetupComplete(): Boolean = setupPrefs.getBoolean("permission_setup_complete", false)

    fun markPermissionSetupComplete() {
        setupPrefs.edit().putBoolean("permission_setup_complete", true).apply()
    }

    fun resetPermissionSetup() {
        setupPrefs.edit().putBoolean("permission_setup_complete", false).apply()
    }

    // Playlist state
    val playlist: StateFlow<List<VideoItem>> = playlistManager.playlist
    val currentVideoIndex: StateFlow<Int> = playlistManager.currentIndex
    val isLoopEnabled: StateFlow<Boolean> = playlistManager.isLoopEnabled

    // Streaming service states
    val connectionState: StateFlow<StreamConnectionState> = LiveStreamService.serviceState
    val metrics: StateFlow<StreamMetrics> = LiveStreamService.serviceMetrics
    val currentStreamingVideo: StateFlow<VideoItem?> = LiveStreamService.currentStreamingVideo
    val statusMessage: StateFlow<String> = LiveStreamService.streamStatusMessage

    // Secure Setup state
    private val _rtmpUrl = MutableStateFlow(secureStorage.getRtmpUrl())
    val rtmpUrl: StateFlow<String> = _rtmpUrl.asStateFlow()

    private val _streamKey = MutableStateFlow(secureStorage.getStreamKey())
    val streamKey: StateFlow<String> = _streamKey.asStateFlow()

    private val _testConnectionResult = MutableStateFlow<String?>(null)
    val testConnectionResult: StateFlow<String?> = _testConnectionResult.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    // Battery optimization status
    private val _isIgnoringBatteryOptimizations = MutableStateFlow(checkBatteryOptimization())
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    fun updateRtmpUrl(url: String) {
        _rtmpUrl.value = url
        secureStorage.setRtmpUrl(url)
    }

    fun updateStreamKey(key: String) {
        _streamKey.value = key
        secureStorage.setStreamKey(key)
    }

    fun clearStreamKey() {
        _streamKey.value = ""
        secureStorage.clearStreamKey()
    }

    fun saveConnection(url: String, key: String) {
        updateRtmpUrl(url)
        updateStreamKey(key)
    }

    fun addVideos(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistManager.addVideosFromUris(uris)
        }
    }

    fun removeVideo(index: Int) {
        playlistManager.removeVideo(index)
    }

    fun moveVideo(from: Int, to: Int) {
        playlistManager.moveVideo(from, to)
    }

    fun setLoopEnabled(enabled: Boolean) {
        playlistManager.setLoopEnabled(enabled)
    }

    fun clearPlaylist() {
        playlistManager.clearPlaylist()
    }

    fun startLive() {
        LiveStreamService.startService(getApplication())
    }

    fun stopLive() {
        LiveStreamService.stopService(getApplication())
    }

    fun pauseLive() {
        LiveStreamService.pauseStream(getApplication())
    }

    fun resumeLive() {
        LiveStreamService.resumeStream(getApplication())
    }

    fun testConnection() {
        val url = _rtmpUrl.value.trim()
        val key = _streamKey.value.trim()

        if (key.isBlank()) {
            _testConnectionResult.value = "Error: Please enter a YouTube Stream Key first."
            return
        }

        _isTestingConnection.value = true
        _testConnectionResult.value = "Testing RTMP handshake to $url..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = RtmpClient(url, key)
                client.connect()
                withContext(Dispatchers.Main) {
                    _testConnectionResult.value = "Success! Connection & Handshake to YouTube RTMP succeeded."
                }
                client.close()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _testConnectionResult.value = "Connection failed: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isTestingConnection.value = false
                }
            }
        }
    }

    fun checkBatteryOptimization(): Boolean {
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) ?: false
    }

    fun refreshBatteryOptimizationStatus() {
        _isIgnoringBatteryOptimizations.value = checkBatteryOptimization()
    }
}
