# Frame Finder

Point a camera at an item and see local AI estimates of what it is. The browser demo also accepts image/video files, VDO.Ninja streams, and HLS/video URLs. No API key or inference server is needed.

**[Open the browser demo](https://steveseguin.github.io/frame-finder/)** on your phone or computer. Tap **Start camera**, allow camera access, and hold one item inside the focus square. Use **Fast classifier** on phones. No microphone is requested.

[Download the portable folder or Android APK](https://github.com/steveseguin/frame-finder/releases/latest). The portable folder includes both native projects and the browser model for use on your Mac or PC.

## Run locally

Install Node.js 18 or newer, open a terminal in this folder, and run:

```sh
npm start
```

Open **http://localhost:8080**, click **Start camera**, and allow camera access. Hold one item inside the focus square. Use **Stop** to release the camera. No microphone is requested.

- **Fast classifier:** MobileNet V2 recognizes common ImageNet categories. Uses WebGL GPU acceleration with a CPU fallback. The top three scores are estimates, not calibrated certainty.
- **Chrome built-in AI:** Gemini Nano gives a short item description on supported desktop Chrome installations. Chrome manages the model download and compute. If image input is unavailable, choose Fast classifier.
- **Analysis settings:** Change the sampling interval, minimum classifier score, or center crop. The app processes one frame at a time and suspends new checks while hidden.

| Browser model | Model size | Coverage | License / terms |
| --- | --- | --- | --- |
| [MobileNet V2 1.0 / 224](https://www.kaggle.com/models/google/mobilenet-v2/tfJs/100-224-classification/2) | 14.04 MB (13.39 MiB) | 1,000 ImageNet classes | Apache-2.0 |
| [Chrome Gemini Nano](https://developer.chrome.com/docs/ai/prompt-api) | Varies with Chrome's model version | Open-ended descriptions; no fixed class count | Proprietary; Google / Chrome terms |

MobileNet's size includes weights and graph, excluding browser libraries and working memory. Chrome downloads Nano separately. Tap the model details under the engine selector for more information.

The supplied `web/vendor/` and `web/models/` files let camera/file classification run offline. Inference stays on your device. To restore these assets if they are missing, run this while connected:

```sh
npm run download-models
```

Copy the **entire folder** to your Mac and run `npm start` there. There is no `npm install` step. Chrome's built-in model is installed separately by Chrome and is not included in the downloaded assets.

## Other video sources

Select a source, choose its file or enter its URL, then click the start/connect button.

- **VDO.Ninja:** Paste a single-stream viewer link such as `https://vdo.ninja/?view=YOUR_ID`. Enter the publisher's stream ID separately if needed. The viewer must show live video. Frames come through VDO.Ninja's iframe API; the small analyzed-frame preview shows what the model sees.
- **HLS / video URL:** Use a direct `.m3u8` playlist or browser-playable video URL. The playlist, segments, and any encryption keys need CORS permission for this page. HTTPS pages need HTTPS sources. DRM-protected media cannot be sampled. HLS playback delay is additional to inference time.
- **Image or video file:** Choose a local photo or video to compare recognition without a camera. Video files loop.

Camera access requires `localhost` or HTTPS. The supplied server binds only to this computer. On a phone, use the [hosted demo](https://steveseguin.github.io/frame-finder/), serve `web/` from your own HTTPS host, or use a native app below.

## Native apps

| Platform | Open | Recognition |
| --- | --- | --- |
| iPhone | [Xcode project](native/ios/FrameFinder.xcodeproj) · [setup](native/ios/README.md) | Apple's built-in Vision classification, text recognition, barcodes |
| Android | [Android Studio project](native/android) · [setup](native/android/README.md) | Small classifiers, saved-item photo matching, ML Kit text/barcodes, and supported system Gemini Nano |

Both native apps use the device camera and expose a separate frame recognizer for later WebRTC/HLS integration. They use platform-specific models, so their labels and scores differ from the browser. See [stream integration and model choices](INTEGRATION.md).

This demo identifies categories and visible attributes. For exact inventory products, compare against catalog photos or look up a recognized barcode/model number; don't turn a category guess into a verified listing.
