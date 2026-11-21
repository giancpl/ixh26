package com.example.photoanchor

import java.io.ByteArrayOutputStream

/** Reads bounded JPEG APP2 metadata only. Never rewrites or color-converts a JPEG. */
internal object PrnuDecoderProfile {
    const val PIXEL_SRGB = "33c34ecfeb998459e0075ba26a0f1ea5042e20e2419c5962e495eba3208576f2"
    private val magic = "ICC_PROFILE\u0000".toByteArray(Charsets.US_ASCII)

    fun identify(jpeg: ByteArray, orientation: Int): String {
        require(orientation in 1..8 && jpeg.size in 4..PhotoProtocol.MAX_PHOTO_BYTES)
        require(jpeg[0] == 0xff.toByte() && jpeg[1] == 0xd8.toByte())
        val chunks = mutableMapOf<Int, ByteArray>(); var total = 0; var count = 0; var offset = 2
        while (offset < jpeg.size) {
            require(jpeg[offset].toInt() and 255 == 255)
            while (offset < jpeg.size && jpeg[offset].toInt() and 255 == 255) offset++
            require(offset < jpeg.size)
            val marker = jpeg[offset++].toInt() and 255
            if (marker == 0xda || marker == 0xd9) break
            require(marker != 0 && marker != 0xd8 && marker !in 0xd0..0xd7 && offset + 2 <= jpeg.size)
            val length = ((jpeg[offset].toInt() and 255) shl 8) or (jpeg[offset + 1].toInt() and 255)
            require(length >= 2 && offset + length <= jpeg.size)
            val start = offset + 2
            if (marker == 0xe2 && length >= 14 && magic.indices.all { jpeg[start + it] == magic[it] }) {
                require(length >= 16)
                val sequence = jpeg[start + 12].toInt() and 255
                val expected = jpeg[start + 13].toInt() and 255
                require(expected in 1..16 && sequence in 1..expected && (count == 0 || count == expected))
                count = expected
                val raw = jpeg.copyOfRange(start + 14, offset + length)
                require(raw.isNotEmpty() && chunks.put(sequence, raw) == null)
                total += raw.size; require(total <= 65536)
            }
            offset += length
        }
        val prefix = "pillow-13.0.0/rgb-green-stored-exif-$orientation"
        if (count == 0) return "$prefix/1"
        require(chunks.size == count)
        val profile = ByteArrayOutputStream()
        for (i in 1..count) profile.write(chunks.getValue(i))
        val hash = PhotoProtocol.sha256(profile.toByteArray())
        require(hash == PIXEL_SRGB) { "Unsupported JPEG ICC profile" }
        return "$prefix-icc-$hash/1"
    }
}
