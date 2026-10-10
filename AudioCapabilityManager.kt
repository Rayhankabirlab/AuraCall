package com.auracall.audio

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class AudioCapabilityManager(private val context: Context) {
    data class Report(val microphoneAvailable: Boolean, val playbackCaptureApiAvailable: Boolean, val note: String)
    fun inspect(): Report {
        val mic = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val playbackApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        return Report(
            microphoneAvailable = mic,
            playbackCaptureApiAvailable = playbackApi,
            note = "Playback capture API থাকলেই WhatsApp/Messenger/IMO/Telegram-এর কলের দুই পাশের অডিও পাওয়া যাবে এমন নয়। কল-অডিও সাধারণত Android ও source app দ্বারা সীমাবদ্ধ।"
        )
    }
}
