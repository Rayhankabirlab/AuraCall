package com.auracall.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Best-effort Android playback capture, only for audio streams the source app/device permits.
 * It cannot bypass VOIP/call-audio capture restrictions and may record silence for WhatsApp/Telegram calls.
 */
class PlaybackCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.auracall.action.START_PLAYBACK_CAPTURE"
        const val ACTION_STOP = "com.auracall.action.STOP_PLAYBACK_CAPTURE"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        const val EXTRA_OUTPUT_PATH = "output_path"
        private const val CHANNEL_ID = "auracall_playback_capture"
        private const val NOTIFICATION_ID = 4207
        private const val SAMPLE_RATE = 44100
    }

    private val running = AtomicBoolean(false)
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var projection: MediaProjection? = null
    @Volatile private var captureThread: Thread? = null
    @Volatile private var outputFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        if (running.get()) return START_NOT_STICKY

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        val path = intent?.getStringExtra(EXTRA_OUTPUT_PATH)
        if (resultData == null || path.isNullOrBlank()) {
            stopSelf(); return START_NOT_STICKY
        }

        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = manager.getMediaProjection(resultCode, resultData)
            projection = mediaProjection
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCapture()
                    stopSelf()
                }
            }, android.os.Handler(mainLooper))
            outputFile = File(path)
            startPlaybackCapture(mediaProjection, outputFile!!)
        } catch (e: Exception) {
            stopCapture()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startPlaybackCapture(mediaProjection: MediaProjection, file: File) {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val config = android.media.AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuffer > 0) { "AudioRecord buffer unavailable: $minBuffer" }
        val record = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuffer * 2, 16384))
            .setAudioPlaybackCaptureConfig(config)
            .build()
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Playback capture AudioRecord could not initialize")
        }
        file.parentFile?.mkdirs()
        audioRecord = record
        running.set(true)
        captureThread = thread(name = "AuraCall-PlaybackCapture") {
            try {
                RandomAccessFile(file, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write(ByteArray(44))
                    val buffer = ByteArray(maxOf(minBuffer, 8192))
                    record.startRecording()
                    while (running.get()) {
                        val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                        if (count > 0) raf.write(buffer, 0, count)
                        else if (count < 0) break
                    }
                    val dataLength = (raf.length() - 44).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    raf.seek(0)
                    raf.write(wavHeader(dataLength, SAMPLE_RATE))
                }
            } catch (_: Exception) {
                // The file is finalized below if it was created; UI explains source restrictions.
            } finally {
                try { if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop() } catch (_: Exception) {}
                record.release()
                audioRecord = null
                running.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopCapture() {
        running.set(false)
        try { audioRecord?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() } } catch (_: Exception) {}
        try { captureThread?.join(1500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        captureThread = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "AuraCall playback recording", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("AuraCall playback capture active")
        .setContentText("Only permitted playback audio can be captured. Tap Stop in AuraCall to finish.")
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun wavHeader(dataLength: Int, sampleRate: Int): ByteArray {
        val byteRate = sampleRate * 2
        return java.io.ByteArrayOutputStream(44).apply {
            fun ascii(s: String) = write(s.toByteArray(Charsets.US_ASCII))
            fun le16(v: Int) { write(v and 255); write((v shr 8) and 255) }
            fun le32(v: Int) { write(v and 255); write((v shr 8) and 255); write((v shr 16) and 255); write((v shr 24) and 255) }
            ascii("RIFF"); le32(dataLength + 36); ascii("WAVE"); ascii("fmt "); le32(16); le16(1)
            le16(1); le32(sampleRate); le32(byteRate); le16(2); le16(16); ascii("data"); le32(dataLength)
        }.toByteArray()
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
}
