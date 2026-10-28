# Run Frame Finder on Android

1. Open this `android` directory in Android Studio with JDK 17, Android SDK 35, and platform tools installed.
2. Let Gradle sync download the pinned dependencies and bundled models.
3. Connect an Android 6.0+ device, select it, and Run the `app` target.
4. Tap **Start camera**, allow camera access, and hold one item close to it.

The app uses the rear camera, or the front camera when no rear camera exists. It stops capture in the background. No microphone is requested.

Download `FrameFinder-debug.apk` from the [latest release](https://github.com/steveseguin/frame-finder/releases/latest) for direct installation on an Android device; it is also included in the portable ZIP. It is a development build, not a Play Store release. Build from the project when you want to change the app or sign it with your own key.

To build from the command line after configuring your SDK:

```sh
# macOS / Linux
sh gradlew --no-daemon --max-workers=2 assembleDebug
```

```powershell
# Windows
.\gradlew.bat --no-daemon --max-workers=2 assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Android Studio creates a local `local.properties` containing your SDK path; don't copy that machine-specific file to another computer. Alternatively, configure `ANDROID_HOME`.

## Recognition

The app bundles ML Kit image labeling, Latin-script text recognition, and barcode scanning. It has no dependency on Gemini Nano or a special AI-capable phone. The labeling models use their own category vocabulary and compute implementation; this starter does not force GPU/NPU acceleration.

`FrameRecognizer.java` accepts an `InputImage`. CameraX supplies upright interpretation through the frame's rotation metadata. All recognition readers finish before the `ImageProxy` is closed. One frame is analyzed at a time, approximately once a second; newer camera frames replace queued frames.

For WebRTC/HLS, supply decoded pixels to the same recognizer. Retain the input buffer until its completion callback fires. See [integration notes](../../INTEGRATION.md).
