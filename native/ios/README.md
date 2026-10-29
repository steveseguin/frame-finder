# Run Frame Finder on iPhone

1. Copy this folder to a Mac with Xcode 15 or newer.
2. Open `FrameFinder.xcodeproj`.
3. Select the **FrameFinder** target, then **Signing & Capabilities**. Choose your development team and a unique bundle identifier.
4. Connect an iPhone running iOS 16 or newer, select it as the run destination, and press Run.
5. Tap **Start camera**, allow access, and hold one item close to the camera.

The app is portrait-only and uses the rear camera. Use a physical iPhone for camera input. It does not request microphone permission. Camera capture stops when the app leaves the foreground.

Apple Vision supplies the classification model, OCR, and barcode recognition. No model file, CocoaPods, Swift package, or API key is required. Vision chooses its available compute resources; the app does not promise a particular GPU or Neural Engine path.

See [model comparison and iOS options](../../MODELS.md#ios-options) for Vision image embeddings, bundled detectors, Core ML acceleration, and newer Apple Intelligence image input. Those are integration options; this Xcode starter currently uses Vision classification, text and barcodes. Android's model picker, review flags and battery policy are not yet implemented in the iOS starter.

## Use decoded stream frames

`FrameRecognizer.swift` accepts a `CVPixelBuffer` plus its `CGImagePropertyOrientation`. Call it on a serial background queue and show results on the main thread. Camera capture currently throttles calls to roughly once a second and drops late frames.

For HLS, attach `AVPlayerItemVideoOutput` to your `AVPlayerItem` and sample its pixel buffer at the current item time. For WebRTC, implement an `RTCVideoRenderer` and adapt its frames to `CVPixelBuffer`, including conversion from I420 when required. Supply the correct orientation and keep at most one recognition request active. See [integration notes](../../INTEGRATION.md).

For product matching, use recognized text/barcodes to query your inventory, or replace `VNClassifyImageRequest` with a custom `VNCoreMLRequest`. Do not assume an arbitrary custom model has the same input normalization or output labels.
