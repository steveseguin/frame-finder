# Run Frame Finder on Android

Install `FrameFinder-debug.apk` from the [latest release](https://github.com/steveseguin/frame-finder/releases/latest) on Android 8 or newer. For Android 6/7, use `FrameFinder-standard-debug.apk` instead. These are development builds for direct installation.

Choose a recognition model, tap **Start camera**, and allow access. Object detectors scan the full scene and draw boxes on the analyzed image below the results. For classifiers and saved-item matching, hold one item near the center. **Stop camera** before switching models.

The app uses the rear camera, or the front camera when no rear camera exists. It stops capture in the background. No microphone is requested.

## Choose a model

| Option | Model size | Coverage | License / terms |
| --- | --- | --- | --- |
| ML Kit | 3.04 MB labeling weights; text/barcode models additional | 447 model labels, plus text and barcodes | Google ML Kit terms |
| EfficientNet-Lite0 int8 | 5.43 MB | 1,000 ImageNet classes | Apache-2.0 |
| EfficientNet-Lite2 int8 | 7.17 MB | 1,000 ImageNet classes | Apache-2.0 |
| MobileNet V3 Small | 4.12 MB | Your saved item names; 1,024-value image embeddings | Apache-2.0 |
| MobileNet V3 Large | 10.89 MB | Your saved item names; 1,280-value image embeddings | Apache-2.0 |
| EfficientDet-Lite0 int8 | 4.56 MB | 80 COCO categories with boxes; 320 × 320 input | Apache-2.0 |
| EfficientDet-Lite2 int8 | 7.56 MB | Same 80 categories; 448 × 448 input | Apache-2.0 |
| SSD MobileNet V1 uint8 | 4.19 MB | Same 80 categories; 300 × 300 input | Apache-2.0 |
| System Gemini Nano | 0 MB added model weights; Android owns the existing model | Open-ended image descriptions | Google GenAI terms |

Sizes use decimal MB and exclude runtime libraries and working memory. All seven small models are bundled for offline use, with hashes and source URLs in [the model manifest](app/src/main/assets/models/manifest.json). The small models run on CPU with two inference threads. ML Kit and AICore manage their own compute. See the [model comparison and iOS options](../../MODELS.md).

**Object detectors:** Start with EfficientDet-Lite0, then compare Lite2's larger input or SSD MobileNet V1's speed. All three include knife and scissors, but have no firearm, drug, tobacco or alcohol-specific categories. A bottle label cannot identify its contents. Stop the camera and use **Choose review categories** to select which detections get orange boxes and a **Review** label. Knife and scissors are selected initially. Other categories get green boxes. Choices and the score threshold stay on the phone and apply to all three detectors. Lower thresholds show more candidates and false alarms. Review flags are category matches, not automatic prohibited-item decisions; missed detections are not a safety clearance.

**Battery-aware sampling:** The battery display shows charge level, external power and Battery Saver. With the toggle enabled, checks run at most every 0.5 seconds on external power, every second normally, and every 5 seconds at 20% or lower or with Battery Saver on. At 10% or lower while unplugged, capture stops to save power. Connect power or disable the toggle, then tap **Start camera** to resume; it never restarts the camera automatically. Disabling the toggle uses a fixed one-second interval. Unknown battery levels use the normal interval. System AI remains manually triggered, and slow inference can reduce the actual rate.

Scroll to **Selected model resources** for measured RAM, CPU and latency while the camera runs. It shows first-check latency separately from the average of later checks, app RAM (PSS), the RAM change since before the first check, and app CPU as a percentage of one core. These are app-wide observations, including the camera and runtime caches; AICore's separate service memory, CPU and accelerator utilization are not exposed. Relaunch between models for cleaner comparisons. The displayed backend identifies the CPU path for the seven small models and reports SDK-managed compute for ML Kit/AICore without assuming GPU or TPU use.

Disk figures come from the installed APK: selected model assets inside the ZIP, their uncompressed size, and the total APK size. Shared libraries and packaging overhead are not assigned to individual models. System-owned weights count as 0 MB added to the app; saved item references are counted separately. All bundled models are already present, so changing models adds no download. At the bottom, expand and filter the **447 ML Kit labels**, **1,000 ImageNet categories**, **80 detector categories**, or your **saved item names**. System descriptions have no fixed category list.

**My items:** Choose either MobileNet embedding mode. Start the camera, enter an item name, and tap **Save reference view** while that item is visible in the analyzed preview. Save 3–5 angles with the same name. Repeat for other items. Each model keeps its own collection, up to 200 reference views. Only vectors and names are saved locally, not photographs. A match requires similarity of at least 0.80 and a lead of 0.05 over another item name. Similarity is not a confidence percentage; use distinct backgrounds and several reference angles to compare performance on your items.

**System Gemini Nano:** The app checks the actual AICore image-description capability, including on supported Pixels and Samsungs. If ready, start the camera and tap **Describe frame** for each description. If unavailable or not downloaded, use a bundled model or recheck after an Android/AICore update. The app never starts a system model download. AICore has foreground and request-quota restrictions, so descriptions are requested by tap. See [supported devices](https://developers.google.com/ml-kit/genai).

Image embeddings compare reference photographs; they do not understand typed category descriptions. For image-to-text-label matching, models such as [EmbeddingGemma 2](https://huggingface.co/google/embeddinggemma-2) and [TIPSv2 B/14](https://huggingface.co/google/tipsv2-b14) provide aligned image/text embeddings, but their published checkpoints exceed this demo's 50 MB small-model budget and are not bundled.

## Build the app

1. Open this `android` directory in Android Studio with JDK 17, Android SDK 35, and platform tools installed.
2. Let Gradle sync download the pinned dependencies. The seven small models are already included.
3. Choose `systemAiDebug` for Android 8+ with system AI support, or `standardDebug` for Android 6+ without the GenAI SDK.
4. Connect your device and Run the `app` target. Sign your own release build for distribution.

To build from the command line after configuring your SDK:

```sh
# macOS / Linux
sh gradlew --no-daemon --max-workers=2 assembleSystemAiDebug assembleStandardDebug
```

```powershell
# Windows
.\gradlew.bat --no-daemon --max-workers=2 assembleSystemAiDebug assembleStandardDebug
```

The APKs are written under `app/build/outputs/apk/systemAi/debug/` and `app/build/outputs/apk/standard/debug/`. Android Studio creates a local `local.properties` containing your SDK path; don't copy that machine-specific file to another computer. Alternatively, configure `ANDROID_HOME`.

## Recognition

The ML Kit option bundles image labeling, Latin-script text recognition, and barcode scanning. The seven small models and ML Kit work without Gemini Nano or a special AI-capable phone.

`FrameRecognizer.java` accepts an `InputImage`; `SmallModel.java` and `SystemRecognizer.java` accept an upright `Bitmap`. CameraX frames are rotated and copied before closing the `ImageProxy`. ML Kit receives the full frame for text and barcode scanning. Detectors resize the full scene to their input dimensions and return normalized boxes; other modes use a center crop. The bitmap remains alive until recognition finishes. One frame is analyzed at a time at the displayed battery-dependent interval; newer camera frames replace queued frames. System AI uses one frame per tap. Detectors reuse the TensorFlow Lite runtime with two CPU threads and built-in box postprocessing.

For WebRTC/HLS, supply decoded pixels to the same recognizer. Retain the input buffer until its completion callback fires. See [integration notes](../../INTEGRATION.md).
