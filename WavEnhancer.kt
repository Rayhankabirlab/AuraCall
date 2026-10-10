package com.auracall.audio

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.roundToInt

/** Simple offline PCM16 WAV peak normalizer. It is not AI noise cancellation. */
object WavEnhancer {
    fun normalize(input: File, output: File, targetPeak: Double = 0.85): Result<File> = runCatching {
        require(input.length() > 44) { "WAV ফাইলটি খালি বা অসম্পূর্ণ" }
        RandomAccessFile(input, "r").use { src ->
            val header = ByteArray(44); src.readFully(header)
            require(String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE") { "শুধু PCM WAV সমর্থিত" }
            val bits = (header[34].toInt() and 0xff) or ((header[35].toInt() and 0xff) shl 8)
            require(bits == 16) { "শুধু 16-bit PCM WAV সমর্থিত" }
            val dataSize = (input.length() - 44).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val bytes = ByteArray(dataSize); src.readFully(bytes)
            var peak = 0
            var i = 0
            while (i + 1 < bytes.size) {
                val s = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toInt()
                peak = maxOf(peak, abs(s)); i += 2
            }
            val gain = if (peak == 0) 1.0 else (32767.0 * targetPeak / peak).coerceAtMost(8.0)
            i = 0
            while (i + 1 < bytes.size) {
                val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toInt()
                val scaled = (sample * gain).roundToInt().coerceIn(-32768, 32767)
                bytes[i] = (scaled and 0xff).toByte(); bytes[i + 1] = ((scaled shr 8) and 0xff).toByte(); i += 2
            }
            output.parentFile?.mkdirs()
            RandomAccessFile(output, "rw").use { dst -> dst.setLength(0); dst.write(header); dst.write(bytes) }
        }
        output
    }
}
