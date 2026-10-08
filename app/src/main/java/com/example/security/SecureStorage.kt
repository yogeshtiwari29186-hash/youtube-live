package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure persistent store for sensitive credentials (YouTube Stream Key & RTMP URL)
 * using Android Keystore-backed EncryptedSharedPreferences with fallback to standard prefs.
 */
class SecureStorage(context: Context) {
    companion object {
        private const val TAG = "SecureStorage"
        private const val ENCRYPTED_PREFS_FILE = "localstream_secure_prefs"
        private const val STANDARD_PREFS_FILE = "localstream_fallback_prefs"
        private const val KEY_STREAM_KEY = "yt_stream_key"
        private const val KEY_RTMP_URL = "yt_rtmp_url"
        private const val DEFAULT_RTMP_URL = "rtmp://a.rtmp.youtube.com/live2"
    }

    private val prefs: SharedPreferences

    init {
        prefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable; falling back to private SharedPreferences", e)
            context.getSharedPreferences(STANDARD_PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    fun getStreamKey(): String {
        return prefs.getString(KEY_STREAM_KEY, "") ?: ""
    }

    fun setStreamKey(key: String) {
        prefs.edit().putString(KEY_STREAM_KEY, key.trim()).apply()
    }

    fun clearStreamKey() {
        prefs.edit().remove(KEY_STREAM_KEY).apply()
    }

    fun getRtmpUrl(): String {
        val url = prefs.getString(KEY_RTMP_URL, DEFAULT_RTMP_URL) ?: DEFAULT_RTMP_URL
        return if (url.isBlank()) DEFAULT_RTMP_URL else url.trim()
    }

    fun setRtmpUrl(url: String) {
        val clean = if (url.isBlank()) DEFAULT_RTMP_URL else url.trim()
        prefs.edit().putString(KEY_RTMP_URL, clean).apply()
    }

    fun getMaskedStreamKey(): String {
        val key = getStreamKey()
        if (key.length <= 4) return "••••"
        val visibleSuffix = key.takeLast(4)
        return "••••••••••••" + visibleSuffix
    }
}
