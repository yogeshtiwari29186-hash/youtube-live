package com.example.playlist

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Manages the local playlist:
 * - Persists playlist references (URIs and metadata) locally in shared preferences.
 * - Retains persistable URI permissions so background services can read the video files.
 * - Supports add, remove, reorder, clear, loop toggle, and advancing to next video.
 */
class PlaylistManager private constructor(private val appContext: Context) {
    companion object {
        private const val TAG = "PlaylistManager"
        private const val PREFS_NAME = "localstream_playlist_prefs"
        private const val KEY_PLAYLIST_JSON = "saved_playlist_json"
        private const val KEY_LOOP_ENABLED = "loop_enabled"

        @Volatile
        private var INSTANCE: PlaylistManager? = null

        fun getInstance(context: Context): PlaylistManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaylistManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _playlist = MutableStateFlow<List<VideoItem>>(emptyList())
    val playlist: StateFlow<List<VideoItem>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isLoopEnabled = MutableStateFlow(prefs.getBoolean(KEY_LOOP_ENABLED, true))
    val isLoopEnabled: StateFlow<Boolean> = _isLoopEnabled.asStateFlow()

    init {
        loadPersistedPlaylist()
    }

    fun addVideosFromUris(uris: List<Uri>) {
        val currentList = _playlist.value.toMutableList()
        val resolver = appContext.contentResolver

        for (uri in uris) {
            try {
                // Take persistable permission to keep access across reboots/background
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                try {
                    resolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Could not take persistable URI permission for $uri", e)
                }

                // Extract filename & size
                var name = "Video_${System.currentTimeMillis()}"
                var size: Long = 0
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                        if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                    }
                }

                val mime = resolver.getType(uri) ?: "video/*"

                // Extract duration and dimensions
                var duration: Long = 0
                var width = 0
                var height = 0
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(appContext, uri)
                    val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    duration = durStr?.toLongOrNull() ?: 0L
                    val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    width = wStr?.toIntOrNull() ?: 0
                    height = hStr?.toIntOrNull() ?: 0
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to retrieve media metadata for $uri", e)
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }

                val item = VideoItem(
                    id = UUID.randomUUID().toString(),
                    uriString = uri.toString(),
                    title = name,
                    durationMs = duration,
                    sizeBytes = size,
                    mimeType = mime,
                    width = width,
                    height = height
                )
                currentList.add(item)
            } catch (e: Exception) {
                Log.e(TAG, "Error importing URI: $uri", e)
            }
        }

        _playlist.value = currentList
        savePlaylist()
    }

    fun removeVideo(index: Int) {
        val current = _playlist.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _playlist.value = current
            if (_currentIndex.value >= current.size && current.isNotEmpty()) {
                _currentIndex.value = current.size - 1
            } else if (current.isEmpty()) {
                _currentIndex.value = 0
            }
            savePlaylist()
        }
    }

    fun moveVideo(fromIndex: Int, toIndex: Int) {
        val current = _playlist.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _playlist.value = current
            savePlaylist()
        }
    }

    fun clearPlaylist() {
        _playlist.value = emptyList()
        _currentIndex.value = 0
        savePlaylist()
    }

    fun setLoopEnabled(enabled: Boolean) {
        _isLoopEnabled.value = enabled
        prefs.edit().putBoolean(KEY_LOOP_ENABLED, enabled).apply()
    }

    fun setCurrentIndex(index: Int) {
        if (index in _playlist.value.indices) {
            _currentIndex.value = index
        }
    }

    /**
     * Advance to the next video. Returns null if playlist finished and loop is disabled.
     */
    fun nextVideo(): VideoItem? {
        val list = _playlist.value
        if (list.isEmpty()) return null

        val nextIdx = _currentIndex.value + 1
        return if (nextIdx < list.size) {
            _currentIndex.value = nextIdx
            list[nextIdx]
        } else if (_isLoopEnabled.value) {
            _currentIndex.value = 0
            list[0]
        } else {
            null
        }
    }

    fun getCurrentVideo(): VideoItem? {
        val list = _playlist.value
        val idx = _currentIndex.value
        return if (idx in list.indices) list[idx] else null
    }

    fun getTotalDurationMs(): Long {
        return _playlist.value.sumOf { it.durationMs }
    }

    private fun savePlaylist() {
        try {
            val array = JSONArray()
            for (item in _playlist.value) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("uri", item.uriString)
                obj.put("title", item.title)
                obj.put("duration", item.durationMs)
                obj.put("size", item.sizeBytes)
                obj.put("mime", item.mimeType)
                obj.put("width", item.width)
                obj.put("height", item.height)
                array.put(obj)
            }
            prefs.edit().putString(KEY_PLAYLIST_JSON, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist playlist", e)
        }
    }

    private fun loadPersistedPlaylist() {
        val jsonStr = prefs.getString(KEY_PLAYLIST_JSON, null) ?: return
        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<VideoItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    VideoItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        uriString = obj.getString("uri"),
                        title = obj.getString("title"),
                        durationMs = obj.optLong("duration", 0L),
                        sizeBytes = obj.optLong("size", 0L),
                        mimeType = obj.optString("mime", "video/*"),
                        width = obj.optInt("width", 0),
                        height = obj.optInt("height", 0)
                    )
                )
            }
            _playlist.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load saved playlist", e)
        }
    }
}
