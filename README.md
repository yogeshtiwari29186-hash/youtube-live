# LocalStream Live

LocalStream Live is a modern Android live broadcasting application that streams local video files directly from device storage continuously to YouTube Live via RTMP/RTMPS.

## Key Features

1. **Zero-Cloud Local Storage Streaming**
   - No video files are ever uploaded to cloud buckets (Firebase Storage, AWS S3, Google Drive).
   - Video files remain safely on the user's Android phone.
   - Files are parsed directly via Android's MediaExtractor into continuous H.264 video NALUs and AAC audio frames.

2. **Continuous Playlist & Seamless Transitions**
   - Add multiple local videos (MP4, MKV, MOV, WebM) via Android Storage Access Framework (SAF).
   - Reorder and delete videos with live duration calculation.
   - Continuous looping keeps the YouTube Live broadcast alive without closing or resetting the RTMP connection when moving to the next video.

3. **Hardware Encrypted YouTube Credentials**
   - Android Keystore AES-256 GCM backed encrypted storage (`EncryptedSharedPreferences`).
   - Stream keys are masked by default and never logged or exposed.
   - Built-in connection tester verifies handshake without sending full video payload.

4. **Background Execution & Foreground Service**
   - Runs in Android `FOREGROUND_SERVICE` with `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
   - CPU `PARTIAL_WAKE_LOCK` ensures encoding continues even when phone is locked or another app is open.
   - Persistent media notification with Pause, Resume, and Stop LIVE actions.

5. **Fault Tolerance & Auto-Reconnect**
   - Automatically skips corrupted video files without dropping the entire broadcast.
   - Exponential backoff automatic reconnect upon network loss with real-time NetworkCallback.
   - Full telemetry dashboard showing bitrate (kbps), FPS, dropped frames, and elapsed time.

6. **Battery & Background Optimization Guides**
   - Settings screen provides battery whitelist guidance to prevent vendor OS background throttling.
