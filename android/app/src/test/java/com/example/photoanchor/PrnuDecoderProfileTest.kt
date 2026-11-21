package com.example.photoanchor

import java.io.ByteArrayOutputStream
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PrnuDecoderProfileTest {
    private fun vector() = JSONObject(javaClass.classLoader!!.getResourceAsStream("prnu-decoder.json")!!.bufferedReader().readText())
    private fun metadata(chunks: List<Pair<Int,ByteArray>>, count: Int): ByteArray {
        val out=ByteArrayOutputStream();out.write(byteArrayOf(0xff.toByte(),0xd8.toByte()))
        for ((index,data) in chunks) {
            val body="ICC_PROFILE\u0000".toByteArray()+byteArrayOf(index.toByte(),count.toByte())+data
            out.write(byteArrayOf(0xff.toByte(),0xe2.toByte(),((body.size+2) shr 8).toByte(),(body.size+2).toByte()));out.write(body)
        }
        out.write(byteArrayOf(0xff.toByte(),0xd9.toByte()));return out.toByteArray()
    }
    @Test fun bindsExactSharedProfileWithoutModifyingBytes() {
        val v=vector();val icc=Base64.getDecoder().decode(v.getString("icc_base64"))
        assertEquals(PrnuDecoderProfile.PIXEL_SRGB,PhotoProtocol.sha256(icc))
        for (chunks in listOf(listOf(1 to icc),listOf(2 to icc.copyOfRange(200,icc.size),1 to icc.copyOfRange(0,200)))) {
            val jpeg=metadata(chunks,chunks.size);val original=jpeg.copyOf()
            assertEquals(v.getString("decoder"),PrnuDecoderProfile.identify(jpeg,6))
            assertArrayEquals(original,jpeg)
        }
        assertEquals("pillow-13.0.0/rgb-green-stored-exif-1/1",PrnuDecoderProfile.identify(metadata(emptyList(),0),1))
    }
    @Test fun rejectsUnknownMissingDuplicateAndTruncatedProfileChunks() {
        val icc=Base64.getDecoder().decode(vector().getString("icc_base64"))
        val bad=icc.copyOf();bad[bad.size-1]=(bad.last().toInt() xor 1).toByte()
        for (jpeg in listOf(metadata(listOf(1 to bad),1),metadata(listOf(1 to icc),2),metadata(listOf(1 to icc,1 to icc),2),metadata(listOf(1 to icc),1).copyOf(30))) {
            assertThrows(IllegalArgumentException::class.java) { PrnuDecoderProfile.identify(jpeg,6) }
        }
    }
}
