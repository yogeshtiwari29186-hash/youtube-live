package com.example

import com.example.model.VideoItem
import com.example.rtmp.Amf0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LocalStreamLogicTest {

    @Test
    fun testAmf0StringSerialization() {
        val out = ByteArrayOutputStream()
        Amf0.writeString(out, "connect")
        val bytes = out.toByteArray()

        val inStream = ByteArrayInputStream(bytes)
        val value = Amf0.readAmfValue(inStream)
        assertEquals("connect", value)
    }

    @Test
    fun testAmf0NumberSerialization() {
        val out = ByteArrayOutputStream()
        Amf0.writeNumber(out, 1234.5)
        val bytes = out.toByteArray()

        val inStream = ByteArrayInputStream(bytes)
        val value = Amf0.readAmfValue(inStream)
        assertEquals(1234.5, value)
    }

    @Test
    fun testVideoItemDurationFormatting() {
        val video = VideoItem(
            id = "test-1",
            uriString = "content://media/123",
            title = "sample.mp4",
            durationMs = 3661000L, // 1h 1m 1s
            sizeBytes = 10485760L,
            mimeType = "video/mp4"
        )

        assertEquals("1:01:01", video.formattedDuration())
        assertEquals("10.0 MB", video.formattedSize())
    }
}
