package com.example.rtmp

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Date

/**
 * AMF0 (Action Message Format 0) encoder and decoder utilities for RTMP handshake and RPC.
 */
object Amf0 {
    const val TYPE_NUMBER: Byte = 0x00
    const val TYPE_BOOLEAN: Byte = 0x01
    const val TYPE_STRING: Byte = 0x02
    const val TYPE_OBJECT: Byte = 0x03
    const val TYPE_NULL: Byte = 0x05
    const val TYPE_ECMA_ARRAY: Byte = 0x08
    const val TYPE_OBJECT_END: Byte = 0x09
    const val TYPE_STRICT_ARRAY: Byte = 0x0A

    fun writeString(out: OutputStream, value: String) {
        out.write(TYPE_STRING.toInt())
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        out.write((bytes.size shr 8) and 0xFF)
        out.write(bytes.size and 0xFF)
        out.write(bytes)
    }

    fun writeNumber(out: OutputStream, value: Double) {
        out.write(TYPE_NUMBER.toInt())
        val bits = java.lang.Double.doubleToRawLongBits(value)
        val buf = ByteArray(8)
        for (i in 7 downTo 0) {
            buf[7 - i] = ((bits ushr (i * 8)) and 0xFF).toByte()
        }
        out.write(buf)
    }

    fun writeBoolean(out: OutputStream, value: Boolean) {
        out.write(TYPE_BOOLEAN.toInt())
        out.write(if (value) 1 else 0)
    }

    fun writeNull(out: OutputStream) {
        out.write(TYPE_NULL.toInt())
    }

    fun writeObjectHeader(out: OutputStream) {
        out.write(TYPE_OBJECT.toInt())
    }

    fun writeObjectProperty(out: OutputStream, name: String, writeValue: () -> Unit) {
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        out.write((nameBytes.size shr 8) and 0xFF)
        out.write(nameBytes.size and 0xFF)
        out.write(nameBytes)
        writeValue()
    }

    fun writeObjectEnd(out: OutputStream) {
        out.write(0) // 16-bit 0 len
        out.write(0)
        out.write(TYPE_OBJECT_END.toInt())
    }

    fun writeEcmaArrayHeader(out: OutputStream, count: Int) {
        out.write(TYPE_ECMA_ARRAY.toInt())
        out.write((count ushr 24) and 0xFF)
        out.write((count ushr 16) and 0xFF)
        out.write((count ushr 8) and 0xFF)
        out.write(count and 0xFF)
    }

    fun readAmfValue(stream: InputStream): Any? {
        val type = stream.read()
        if (type == -1) return null
        return when (type.toByte()) {
            TYPE_NUMBER -> {
                val buf = ByteArray(8)
                readFully(stream, buf)
                var bits: Long = 0
                for (b in buf) {
                    bits = (bits shl 8) or ((b.toLong()) and 0xFF)
                }
                java.lang.Double.longBitsToDouble(bits)
            }
            TYPE_BOOLEAN -> stream.read() != 0
            TYPE_STRING -> {
                val lenHigh = stream.read()
                val lenLow = stream.read()
                val len = (lenHigh shl 8) or lenLow
                val bytes = ByteArray(len)
                readFully(stream, bytes)
                String(bytes, StandardCharsets.UTF_8)
            }
            TYPE_NULL -> null
            TYPE_OBJECT, TYPE_ECMA_ARRAY -> {
                if (type.toByte() == TYPE_ECMA_ARRAY) {
                    // skip 4 count bytes
                    stream.read(); stream.read(); stream.read(); stream.read()
                }
                val map = mutableMapOf<String, Any?>()
                while (true) {
                    val lenHigh = stream.read()
                    val lenLow = stream.read()
                    if (lenHigh == -1 || lenLow == -1) break
                    val nameLen = (lenHigh shl 8) or lenLow
                    if (nameLen == 0) {
                        val endMarker = stream.read()
                        if (endMarker.toByte() == TYPE_OBJECT_END) break
                    }
                    val nameBytes = ByteArray(nameLen)
                    readFully(stream, nameBytes)
                    val name = String(nameBytes, StandardCharsets.UTF_8)
                    val value = readAmfValue(stream)
                    map[name] = value
                }
                map
            }
            else -> null
        }
    }

    private fun readFully(stream: InputStream, b: ByteArray) {
        var offset = 0
        while (offset < b.size) {
            val count = stream.read(b, offset, b.size - offset)
            if (count < 0) throw java.io.EOFException("Unexpected EOF reading AMF")
            offset += count
        }
    }
}
