# Add live product recognition to your player

Start with one item held close to the camera. Measure recognition on the actual phones and product set before choosing a model. Fast category recognition, an open-ended description, and an exact catalog match are different outputs.

## Browser frame input

`web/app.js` owns video sources, resizing/cropping, timing, and UI. `web/engines.js` owns model loading and recognition. Both engines receive a canvas containing the current frame; a new WebRTC receiver can assign its remote `MediaStream` to the video element and reuse the frame path.

The crop is a centered square covering 80% of the shorter source dimension. Full-frame mode preserves aspect ratio. The maximum sampled dimension is 384 pixels; MobileNet then applies its own 224-pixel preprocessing. Recognition is sequential. The selected interval is the delay **after** a completed check, so actual update rate depends on inference time. Stopping invalidates in-flight results and closes media tracks. Pausing retains the preview; hiding the page stops new checks.

### VDO.Ninja

The demo sends a callback-tagged request to the viewer's exact origin:

```js
iframe.contentWindow.postMessage({
  getVideoFrame: true,
  streamID: 'publisher-stream-id',
  cib: 'unique-request-id'
}, 'https://vdo.ninja');
```

It expects this response from that iframe window and origin:

```js
{
  action: 'image-frame-capture',
  value: {
    imageData: /* PNG Blob */,
    imageType: 'png',
    streamID: 'publisher-stream-id',
    cib: 'unique-request-id'
  }
}
```

The viewer URL sets `iframetarget` to the parent page's origin. The wrapper validates the sender, origin, callback ID, stream ID, and Blob size. It uses a six-second response timeout and only one pending request. This API's PNG Blob response differs from the `getSnapshotByStreamID` API's `type: 'frame'` data-URL response. Use the correct decoder for whichever API you adopt.

VDO.Ninja handles WebRTC signaling, playback, and its own network connections. No VDO.Ninja source change is needed. Password-protected viewer parameters remain in the entered URL; the demo does not persist it or transmit it to an inference service.

## Native frame input

| Source | iOS | Android |
| --- | --- | --- |
| Camera | `AVCaptureVideoDataOutput` → `CVPixelBuffer` | CameraX `ImageAnalysis` → `InputImage` |
| WebRTC | `RTCVideoRenderer` → pixel buffer; convert non-CV buffers as needed | `VideoSink` → retained frame → RGB bitmap or supported YUV input |
| HLS | `AVPlayerItemVideoOutput` → current pixel buffer | Media3 decoded output → sampled bitmap or custom video frame processor |

The native camera apps are complete starting points; native WebRTC/HLS playback is not included. Reuse your existing player/receiver instead of introducing a second connection. Decode first, apply orientation, and only then invoke recognition. iOS expects the caller to handle background work; Android returns asynchronously. Skip frames when busy rather than accumulating a queue. If pixels need conversion or GPU readback, include that cost in latency measurements.

## Choose the output you need

- **Common category:** use the supplied classifier. A score below the selected cutoff produces no clear match. A label repeated across frames is only a consistency indication, not independent proof.
- **Visible description:** select Chrome's built-in Gemini Nano mode. Each frame uses a fresh session clone so previous items don't bias later ones. Keep output short. Treat generated details as suggestions.
- **Exact catalog item:** create several reference photos per item, crop the live item, compute image embeddings with the same model/preprocessing, and compare with inventory embeddings. Use a minimum match threshold and a margin over the second-best match. Confirm the match with OCR, barcode, or seller selection when available.
- **Custom detector:** add an object detector when you need multiple boxes or automatic item selection. A detector trained on a fixed label set cannot name products outside that set. Validate model licensing and export compatibility before distributing a replacement model.

For native acceleration with a custom model, use Core ML on iOS or LiteRT on Android and benchmark supported delegates on your devices. The supplied Apple Vision and ML Kit starters let their platform choose compute; they do not claim identical behavior or performance to MobileNet in the browser.

## References

- [TensorFlow.js MobileNet](https://github.com/tensorflow/tfjs-models/tree/master/mobilenet)
- [Chrome Prompt API, image input and device requirements](https://developer.chrome.com/docs/ai/prompt-api)
- [VDO.Ninja iframe API](https://github.com/steveseguin/vdo.ninja/blob/master/examples/iframeapi.md)
- [Apple Vision classification](https://developer.apple.com/documentation/vision/vnclassifyimagerequest)
- [Android ML Kit image labeling](https://developers.google.com/ml-kit/vision/image-labeling/android)
- [Android ML Kit direct Gemini Nano access](https://developers.google.com/ml-kit/genai/prompt/android)
- [Apple Foundation Models](https://developer.apple.com/foundation-models-framework/)

The built-in multimodal APIs depend on OS, browser, device, and model availability. Check their capability APIs before offering them; keep the small classifier available as an alternative.
