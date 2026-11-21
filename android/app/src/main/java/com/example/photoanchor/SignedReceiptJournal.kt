package com.example.photoanchor

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest

/** App-private retry journal. This does not make origin/enrollment trust decisions. */
class SignedReceiptJournal(context: Context) {
    private val directory = File(context.applicationContext.noBackupFilesDir, "fingerprinto-receipts")

    /** Producer validates trust and signs locally; it must not submit or perform external writes. */
    fun signOnce(operation: String, requestDigest: ByteArray, producer: () -> ByteArray): ByteArray {
        require(operation.matches(Regex("[0-9a-f]{32}")) && requestDigest.size == 32)
        val request = requestDigest.copyOf()
        return synchronized(PROCESS_LOCK) {
            check(directory.isDirectory || directory.mkdirs())
            RandomAccessFile(File(directory, "journal.lock"), "rw").use { lock ->
                lock.channel.lock().use {
                    val file = File(directory, "$operation.bin")
                    val record = AtomicFile(file)
                    if (file.exists() || File(directory, "$operation.bin.bak").exists()) {
                        val bytes = record.openRead().use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            while (true) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                check(output.size() + count <= 262216) { "Receipt record too large" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        check(bytes.size >= 73 && bytes[0] == 0x46.toByte() && bytes[1] == 0x50.toByte()
                            && bytes[2] == 0x52.toByte() && bytes[3] == 2.toByte()) { "Invalid receipt record" }
                        val input = ByteBuffer.wrap(bytes, 4, bytes.size - 4)
                        val storedRequest = ByteArray(32).also { input.get(it) }
                        check(MessageDigest.isEqual(request, storedRequest)) { "Operation inputs changed" }
                        val size = input.int
                        check(size in 1..262144 && input.remaining() == size + 32) { "Invalid receipt length" }
                        val receipt = ByteArray(size).also { input.get(it) }
                        val digest = ByteArray(32).also { input.get(it) }
                        check(MessageDigest.isEqual(ReceiptV1.hash(receipt), digest)) { "Stored receipt corrupt" }
                        receipt
                    } else {
                        val receipt = producer().copyOf()
                        require(receipt.size in 1..262144)
                        val bytes = ByteBuffer.allocate(72 + receipt.size)
                            .put(byteArrayOf(0x46, 0x50, 0x52, 0x02)).put(request).putInt(receipt.size)
                            .put(receipt).put(ReceiptV1.hash(receipt)).array()
                        val output = record.startWrite()
                        try { output.write(bytes); record.finishWrite(output) }
                        catch (error: Throwable) { record.failWrite(output); throw error }
                        receipt
                    }
                }
            }
        }
    }
    private companion object { val PROCESS_LOCK = Any() }
}
