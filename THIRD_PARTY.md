# Third-party components

The optional `npm run download-models` command saves pinned browser libraries, their license files, and MobileNet model weights locally. `web/assets-manifest.json` records source URLs, sizes, and SHA-256 hashes of downloaded files.

| Component | Version | Source | License |
| --- | --- | --- | --- |
| TensorFlow.js | 4.22.0 | tensorflow/tfjs via npm/jsDelivr | Apache-2.0 |
| TensorFlow.js MobileNet wrapper | 2.1.1 | tensorflow/tfjs-models via npm/jsDelivr | Apache-2.0 |
| MobileNet V2 1.0 / 224 classification | TensorFlow.js model version 2 | Google on Kaggle Models | Apache-2.0 |
| hls.js | 1.5.18 | video-dev/hls.js via npm/jsDelivr | Apache-2.0 |
| Gradle wrapper | Included wrapper bootstrap | Gradle | Apache-2.0 |

The Android Gradle files pin AndroidX and Google ML Kit packages; Gradle resolves their transitive dependencies from Google's Maven repository and Maven Central. Preserve their licenses and applicable SDK terms when distributing an app. Apple Vision and other Apple SDK frameworks are supplied by the operating system and Xcode.

Chrome manages its own Gemini Nano model. The demo does not redistribute it. VDO.Ninja is embedded as an optional remote viewer, not bundled in this folder.

Android includes EfficientNet-Lite0/Lite2 int8 classifiers and MobileNet V3 Small/Large float32 image embedders from Google's MediaPipe model collection. Each model's embedded metadata declares Apache-2.0; the license, download URLs, byte sizes and SHA-256 hashes are included in `native/android/app/src/main/assets/models/`. TensorFlow Lite 2.17.0 uses Apache-2.0. The optional system AI build uses ML Kit GenAI Image Description 1.0.0-beta1 under [ML Kit GenAI terms](https://developers.google.com/ml-kit/terms); Android supplies the Gemini Nano model separately.

The Android `mlkit-labels.txt` category list is extracted from the bundled ML Kit image-labeling 17.0.9 model's English labels and is subject to the same Google SDK terms.

Android also bundles TensorFlow EfficientDet-Lite0/Lite2 int8 and SSD MobileNet V1 uint8 detectors from the [TensorFlow Android example model downloads](https://github.com/tensorflow/examples/blob/master/lite/examples/object_detection/android/app/download_models.gradle). These exports include box postprocessing and declare Apache-2.0 in their embedded metadata. Exact URLs, sizes and hashes are in the model manifest. Their identical embedded label maps supply `coco-labels.txt`: 80 named COCO categories plus 10 unused output slots marked `???`. The app excludes those placeholders from results and the category list.
