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
