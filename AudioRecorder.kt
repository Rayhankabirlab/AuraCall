package com.auracall.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Microphone PCM recorder. This does NOT directly capture VoIP call downlink audio. */
class AudioRecorder {
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var worker: Thread? = null
    private val running = AtomicBoolean(false)
    @Volatile private var output: File? = null
    @Volatile private var failure: String? = null

    fun start(file: File, sampleRate: Int = 44100): Boolean {
        if (running.get()) return false
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val min = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
        if (min <= 0) { failure = "এই ডিভাইসে অডিও রেকর্ডার চালু করা যায়নি (code $min)"; return false }
        val audio = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channel, encoding, maxOf(min * 2, 8192))
        } catch (e: Exception) { failure = e.message ?: "AudioRecord initialization failed"; return false }
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release(); failure = "মাইক্রোফোন রেকর্ডার initialize হয়নি"; return false
        }
        file.parentFile?.mkdirs()
        output = file
        recorder = audio
        running.set(true)
        worker = thread(name = "AuraCall-AudioCapture") {
            try {
                RandomAccessFile(file, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write(ByteArray(44)) // WAV header placeholder
                    val buffer = ByteArray(maxOf(min, 4096))
                    audio.startRecording()
                    while (running.get()) {
                        val count = audio.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                        if (count > 0) raf.write(buffer, 0, count)
                        else if (count < 0) { failure = "AudioRecord read error: $count"; break }
                    }
                    val dataSize = (raf.length() - 44).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    raf.seek(0); raf.write(wavHeader(dataSize, sampleRate, 1, 16))
                }
            } catch (e: Exception) { failure = e.message ?: "রেকর্ডিংয়ে সমস্যা হয়েছে" }
            finally {
                try { if (audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) audio.stop() } catch (_: Exception) {}
                audio.release(); recorder = null; running.set(false)
            }
        }
        return true
    }

    fun stop(): File? {
        running.set(false)
        try { recorder?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() } } catch (_: Exception) {}
        try { worker?.join(2500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        worker = null
        return output?.takeIf { it.exists() && it.length() > 44 }
    }

    fun isRecording() = running.get()
    fun lastError() = failure

    private fun wavHeader(dataLength: Int, sampleRate: Int, channels: Int, bits: Int): ByteArray {
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        return java.io.ByteArrayOutputStream(44).apply {
            fun ascii(s: String) = write(s.toByteArray(Charsets.US_ASCII))
            fun le16(v: Int) { write(v and 255); write((v shr 8) and 255) }
            fun le32(v: Int) { write(v and 255); write((v shr 8) and 255); write((v shr 16) and 255); write((v shr 24) and 255) }
            ascii("RIFF"); le32(dataLength + 36); ascii("WAVE"); ascii("fmt "); le32(16); le16(1)
            le16(channels); le32(sampleRate); le32(byteRate); le16(blockAlign); le16(bits); ascii("data"); le32(dataLength)
        }.toByteArray()
    }
}
