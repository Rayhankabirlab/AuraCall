package com.auracall

import android.Manifest
import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.auracall.audio.AudioCapabilityManager
import com.auracall.audio.AudioRecorder
import com.auracall.audio.PlaybackCaptureService
import com.auracall.audio.WavEnhancer
import com.auracall.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AuraCallTheme { AuraHome() } } }
}

@Composable
private fun AuraHome() {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder() }
    val capability = remember { AudioCapabilityManager(context).inspect() }
    var recording by remember { mutableStateOf(false) }
    var playbackRecording by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("প্রস্তুত। রেকর্ডিং শুরুর আগে সংশ্লিষ্ট ব্যক্তির সম্মতি নিন।") }
    var refresh by remember { mutableIntStateOf(0) }
    var showPrivacy by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<MediaPlayer?>(null) }
    val requestMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording(context, recorder, { recording = it }, { message = it })
        else message = "মাইক্রোফোন অনুমতি দেওয়া হয়নি। Settings থেকে অনুমতি দিন।"
    }
    val requestPlaybackCapture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val dir = File(context.filesDir, "recordings")
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "AuraCall_Playback_$stamp.wav")
            val serviceIntent = Intent(context, PlaybackCaptureService::class.java).apply {
                action = PlaybackCaptureService.ACTION_START
                putExtra(PlaybackCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(PlaybackCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(PlaybackCaptureService.EXTRA_OUTPUT_PATH, file.absolutePath)
            }
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
                playbackRecording = true
                message = "Playback capture চালু হয়েছে। এটি শুধু Android/source app অনুমোদিত MEDIA/GAME অডিও ধরতে পারে; WhatsApp/Telegram কলের দুই পাশ বা কোনো কল অডিও নাও পাওয়া যেতে পারে।"
            } catch (e: Exception) {
                playbackRecording = false
                message = "Playback capture শুরু হয়নি: ${e.message}"
            }
        } else {
            message = "Playback capture অনুমতি বাতিল করা হয়েছে।"
        }
    }
    val recordings = remember(refresh) { recordingFiles(context) }

    Scaffold(containerColor = Background, topBar = {
        TopAppBarAura(onPrivacy = { showPrivacy = true })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("AUDIO CONTROL CENTER", color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Your sound.\nUnder control.", color = TextPrimary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Text("মাইক্রোফোন রেকর্ডিং ও অডিও ফাইল এনহ্যান্সমেন্ট", color = TextSecondary)
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, null, tint = Accent, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (recording) "Recording in progress" else "Microphone recorder", color = TextPrimary, fontWeight = FontWeight.Bold)
                                Text(if (recording) "অডিও ধারণ চলছে" else "স্টার্ট করলে WAV ফাইল তৈরি হবে", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            Box(Modifier.size(10.dp).background(if (recording) Danger else Accent, RoundedCornerShape(50)))
                        }
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = {
                            if (recording) {
                                val file = recorder.stop(); recording = false
                                message = if (file != null) "রেকর্ডিং সেভ হয়েছে: ${file.name}" else recorder.lastError() ?: "রেকর্ডিং ফাইল তৈরি হয়নি"
                                refresh++
                            } else {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (granted) startRecording(context, recorder, { recording = it }, { message = it }) else requestMic.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = if (recording) Danger else Accent, contentColor = Background)) {
                            Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null)
                            Spacer(Modifier.width(8.dp)); Text(if (recording) "STOP RECORDING" else "START RECORDING", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp)); Text(message, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Elevated), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("SOURCE CAPABILITY", color = Blue, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Microphone: ${if (capability.microphoneAvailable) "Available" else "Not detected"}", color = TextPrimary)
                        Text("Playback capture API: ${if (capability.playbackCaptureApiAvailable) "Android API available" else "Not available"}", color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(capability.note, color = Warning, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Elevated), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("APP PLAYBACK CAPTURE (BEST EFFORT)", color = Blue, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("WhatsApp / Telegram-এর জন্য পরীক্ষামূলক playback capture। Android কেবল অনুমোদিত playback stream ধরতে দেয়; VoIP call audio সাধারণত capture করা যায় না।", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (playbackRecording) {
                                    val stopIntent = Intent(context, PlaybackCaptureService::class.java).apply { action = PlaybackCaptureService.ACTION_STOP }
                                    context.startService(stopIntent)
                                    playbackRecording = false
                                    refresh++
                                    message = "Playback capture বন্ধ করা হয়েছে। WAV ফাইলটি তালিকায় আসতে কিছুক্ষণ লাগতে পারে।"
                                } else if (recording) {
                                    message = "আগে Microphone Recording বন্ধ করুন, তারপর Playback Capture চালু করুন।"
                                } else {
                                    val manager = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    requestPlaybackCapture.launch(manager.createScreenCaptureIntent())
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (playbackRecording) Danger else Blue, contentColor = Background)
                        ) {
                            Icon(if (playbackRecording) Icons.Default.Stop else Icons.Default.GraphicEq, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (playbackRecording) "STOP PLAYBACK CAPTURE" else "TRY APP PLAYBACK CAPTURE", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(if (playbackRecording) "Capture active • stop when finished" else "প্রতিবার Android-এর system consent দিতে হবে। নীরব/অসমর্থিত সোর্সের অডিও উদ্ধার করা যাবে না।", color = if (playbackRecording) Accent else Warning, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, null, tint = Accent)
                    Spacer(Modifier.width(8.dp)); Text("My recordings", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f)); Text("${recordings.size} files", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (recordings.isEmpty()) item { Text("এখনও কোনো রেকর্ডিং নেই।", color = TextSecondary, modifier = Modifier.padding(vertical = 20.dp)) }
            items(recordings, key = { it.absolutePath }) { file ->
                Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(file.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("${formatDate(file.lastModified())} • ${formatSize(file.length())}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            try { playing?.release(); playing = MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); start(); setOnCompletionListener { it.release(); playing = null } }; message = "প্লেব্যাক চলছে: ${file.name}" }
                            catch (e: Exception) { message = "ফাইল প্লে করা যায়নি: ${e.message}" }
                        }) { Icon(Icons.Default.PlayArrow, "Play", tint = Accent) }
                        IconButton(onClick = {
                            val out = File(file.parentFile, file.nameWithoutExtension + "_enhanced.wav")
                            val result = WavEnhancer.normalize(file, out)
                            message = result.fold({ "Normalized copy তৈরি: ${it.name}" }, { "Enhancement failed: ${it.message}" }); refresh++
                        }) { Icon(Icons.Default.Tune, "Normalize audio", tint = Blue) }
                    }
                }
            }
            item { Text("Privacy: AuraCall এই সংস্করণে শুধু আপনার অনুমোদিত মাইক্রোফোন অডিও রেকর্ড করে। এটি গোপনে কল রেকর্ড করে না এবং VoIP কলের দুই পাশের অডিওর নিশ্চয়তা দেয় না।", color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (showPrivacy) AlertDialog(onDismissRequest = { showPrivacy = false }, containerColor = Surface,
        title = { Text("Privacy & recording consent", color = TextPrimary) },
        text = { Text("রেকর্ডিং শুরু করার আগে স্থানীয় আইন মেনে সংশ্লিষ্ট ব্যক্তির সম্মতি নিন। এই অ্যাপটি মাইক্রোফোন ইনপুট এবং Android অনুমোদিত playback stream রেকর্ড করার চেষ্টা করতে পারে। WhatsApp, Messenger, IMO বা Telegram-এর দুই পক্ষের কল অডিও ধরতে পারবে এমন নিশ্চয়তা নেই।", color = TextSecondary) },
        confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Understood", color = Accent) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TopAppBarAura(onPrivacy: () -> Unit) {
    TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.GraphicEq, null, tint = Accent); Spacer(Modifier.width(8.dp)); Text("AuraCall", color = TextPrimary, fontWeight = FontWeight.Bold) } },
        actions = { TextButton(onClick = onPrivacy) { Text("PRIVACY", color = Accent) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Background))
}

private fun startRecording(context: android.content.Context, recorder: AudioRecorder, state: (Boolean) -> Unit, message: (String) -> Unit) {
    val dir = File(context.filesDir, "recordings")
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val file = File(dir, "AuraCall_$stamp.wav")
    val ok = recorder.start(file)
    state(ok)
    message(if (ok) "রেকর্ডিং চলছে। দৃশ্যমান রেকর্ডিং স্ট্যাটাস বজায় রাখুন।" else recorder.lastError() ?: "রেকর্ডিং শুরু করা যায়নি")
}
private fun recordingFiles(context: android.content.Context): List<File> = File(context.filesDir, "recordings").listFiles()?.filter { it.isFile && it.extension.equals("wav", true) }?.sortedByDescending { it.lastModified() } ?: emptyList()
private fun formatDate(time: Long) = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(time))
private fun formatSize(bytes: Long): String = if (bytes < 1024) "$bytes B" else if (bytes < 1024 * 1024) "%.1f KB".format(bytes / 1024.0) else "%.1f MB".format(bytes / (1024.0 * 1024.0))
