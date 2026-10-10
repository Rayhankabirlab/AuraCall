# AuraCall Android source project

AuraCall is a Kotlin + Jetpack Compose starter application for transparent microphone recording, local WAV library, playback, and simple offline peak normalization.

## Build
1. Install Android Studio with JDK 17 and Android SDK Platform 35.
2. Open this folder in Android Studio.
3. Allow Gradle sync to complete.
4. Run on a physical Android device (Android 10+ recommended).

This source bundle does not include the Gradle wrapper JAR. Android Studio can sync the project with a local Gradle installation; alternatively generate wrapper files from the IDE/Gradle installation.

## Important limitations
- This project records microphone input only. It does not claim to record both sides of WhatsApp, Messenger, IMO, or Telegram calls.
- Android restricts access to call and other apps' playback audio. `AudioPlaybackCapture` availability does not mean communication-call audio is capturable.
- The enhancement function is a simple PCM16 WAV peak normalizer, not AI denoising or source separation.
- Test on your own target devices and verify current Google Play policies before release.
- Obtain required consent and comply with applicable recording/privacy laws.

## Implemented
- Dark Compose dashboard and AuraCall color palette
- Runtime microphone permission request
- Microphone PCM16 WAV recording
- Recording library with file size/date
- Playback using MediaPlayer
- Offline peak normalization to a separate WAV file
- Basic source capability disclosure and privacy notice

## Not yet implemented
- VoIP two-way capture (not generally available to third-party apps)
- MediaProjection-based playback capture workflow
- Room database, encryption-at-rest, foreground recording service, export/share/delete UI
- Advanced noise reduction, EQ, limiter, AAC export
- Automated and physical-device compatibility test suite

Do not describe these unimplemented items as finished features.

## Best-effort app playback capture (WhatsApp / Telegram limitation)

This source now includes an explicit, user-consented Android `MediaProjection` playback-capture attempt:

- Tap **TRY APP PLAYBACK CAPTURE** in the app.
- Approve the Android system capture prompt.
- Start/continue only audio that Android and the source app allow to be captured.
- Tap **STOP PLAYBACK CAPTURE** to finalize the WAV file.

Important: `AudioPlaybackCaptureConfiguration` only captures eligible playback usages (this implementation requests MEDIA and GAME). WhatsApp/Telegram voice or video calls generally use communication audio paths that may be excluded by Android or the source app. The feature therefore does **not** guarantee call recording or both sides of a conversation. A silent/empty capture is possible and does not mean the call audio can be recovered. Do not attempt to bypass OS protections; obtain all required consent.

The capture service runs as a visible foreground service and requires the user's system MediaProjection consent each session. Test on real devices and inspect the output WAV before relying on it. This project has not been verified on every Android version/device.

## Build an APK on GitHub (phone-friendly)

This project includes a GitHub Actions workflow at `.github/workflows/android.yml`.
It builds a **debug APK** in GitHub's hosted runner, so you do not need Android Studio on your phone.

1. Create a GitHub repository named `AuraCall`.
2. Upload the **contents** of this `AuraCall` project folder to the repository root (so `app/`, `build.gradle.kts`, and `settings.gradle.kts` are at the root).
3. Open the repository's **Actions** tab.
4. Select **AuraCall Android APK**, then tap **Run workflow**. A push to `main` also triggers a build when source files change.
5. When the run finishes successfully, open the run and download the `AuraCall-debug-APK` artifact. Extract the downloaded ZIP to get the APK.

The workflow uses Gradle 8.9 directly; a Gradle wrapper is not included in this source bundle. If the build fails, open the failed workflow run and inspect the first error in the log.

**Audio capture limitation:** a successful APK build does not guarantee recording of WhatsApp or Telegram call audio. Android may block VoIP playback capture, and the app can only capture audio that Android and the source app permit.
