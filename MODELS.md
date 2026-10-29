# Models, categories and device options

For small objects and review flags, install the [Android APK](https://github.com/steveseguin/frame-finder/releases/latest) and start with **EfficientDet-Lite0**. Compare Lite2 for a larger input or SSD MobileNet V1 for speed. The [browser demo](https://steveseguin.github.io/frame-finder/) uses a separate general image classifier. The [iOS starter](native/ios/README.md) uses Apple Vision.

## Included Android models

Model sizes are decimal MB of bundled weights, excluding shared libraries and working memory. The seven standalone models total **43.91 MB**. The full APK is larger because it also contains ML Kit assets and native runtimes for multiple processor architectures. Selecting a bundled model adds no download. Exact byte counts, sources and hashes are in the [model manifest](native/android/app/src/main/assets/models/manifest.json).

| Option | Model MB | Categories / output | License | Compute in this app |
| --- | ---: | --- | --- | --- |
| EfficientDet-Lite0 int8 | 4.56 | 80 COCO categories, boxes; 320 × 320 input | Apache-2.0 | CPU, 2 threads |
| EfficientDet-Lite2 int8 | 7.56 | Same 80 categories, boxes; 448 × 448 input | Apache-2.0 | CPU, 2 threads |
| SSD MobileNet V1 uint8 | 4.19 | Same 80 categories, boxes; 300 × 300 input | Apache-2.0 | CPU, 2 threads |
| EfficientNet-Lite0 int8 | 5.43 | 1,000 ImageNet classification labels | Apache-2.0 | CPU, 2 threads |
| EfficientNet-Lite2 int8 | 7.17 | Same 1,000 classification labels | Apache-2.0 | CPU, 2 threads |
| MobileNet V3 Small | 4.12 | 1,024-value image embedding; your saved item names | Apache-2.0 | CPU, 2 threads |
| MobileNet V3 Large | 10.89 | 1,280-value image embedding; your saved item names | Apache-2.0 | CPU, 2 threads |
| ML Kit | 3.04 labeling weights; OCR/barcode assets additional | 447 labels, Latin text, barcodes | Google SDK terms | SDK managed; accelerator not reported |
| System Gemini Nano | 0 added system-model MB | Open-ended image description; no fixed category count | Google GenAI terms | AICore managed; accelerator not reported |

Efficient**Det** finds multiple objects and their positions. Efficient**Net** classifies an image without locating objects. The embedding options compare saved photographs; they cannot match typed descriptions directly. System Gemini Nano requires an eligible device and an available AICore image-description model. Use the app's capability check and Google's [supported-device guide](https://developers.google.com/ml-kit/genai); a recent Android version or a Pixel/Samsung brand name alone is insufficient.

### Detector categories and review flags

All three detectors have these **80 named categories**:

person, bicycle, car, motorcycle, airplane, bus, train, truck, boat, traffic light, fire hydrant, stop sign, parking meter, bench, bird, cat, dog, horse, sheep, cow, elephant, bear, zebra, giraffe, backpack, umbrella, handbag, tie, suitcase, frisbee, skis, snowboard, sports ball, kite, baseball bat, baseball glove, skateboard, surfboard, tennis racket, bottle, wine glass, cup, fork, **knife**, spoon, bowl, banana, apple, sandwich, orange, broccoli, carrot, hot dog, pizza, donut, cake, chair, couch, potted plant, bed, dining table, toilet, tv, laptop, mouse, remote, keyboard, cell phone, microwave, oven, toaster, sink, refrigerator, book, clock, vase, **scissors**, teddy bear, hair drier, toothbrush.

The label map contains 90 output slots because COCO leaves 10 slots unused; those placeholders are not additional categories. The app lists only the 80 usable names. Its **Choose review categories** control starts with knife and scissors and allows any of the 80 categories to be flagged. It does not retrain or expand the detector.

There are no firearm, drug, tobacco, vape or alcohol-specific classes. A bottle or wine-glass detection does not establish its contents. For additional categories, use a detector trained on the desired labels and representative images. Review flags are candidates for a person to assess; an empty result is not a safety clearance. Smaller, obscured, moving or poorly lit objects can be missed. Lowering the score threshold may reveal more candidates and false alarms.

### RAM, latency, CPU and disk

The Android **Selected model resources** panel reports first-check latency, subsequent average latency, app RAM (PSS), change from the initial baseline, sampled peak RAM and app CPU as a percentage of one core. These observations include the camera, UI and runtime caches; they are not isolated model benchmarks. System services such as AICore run outside these app measurements. Relaunch between models for cleaner comparisons.

The disk panel measures the selected assets inside the installed APK, their uncompressed size and the whole APK. It excludes shared SDK overhead from individual model totals. CPU is explicitly selected for the seven small models; no GPU or TPU delegate is configured. System-managed options report that distinction instead of claiming a particular accelerator. Detection latency includes resizing, inference and box filtering, but excludes camera conversion, drawing the boxes and resource sampling.

### Battery-aware sampling

| Android state, with adaptive sampling enabled | Maximum analysis rate |
| --- | --- |
| External power connected | One check per 0.5 seconds |
| Unplugged, above 20% | One check per second |
| Unplugged, 11–20%, or Battery Saver on | One check per 5 seconds |
| Unplugged, 10% or lower | Stop camera capture and analysis |
| Battery level unavailable | Normal interval; still honor external power / Battery Saver |

Disabling **Battery-aware sampling** uses a fixed one-second interval. After a critical-battery stop, reconnect power or disable the toggle and tap **Start camera**; charging never restarts the camera by itself. Only one inference runs at a time, so actual rates can be lower. System AI still requires **Describe frame**. These intervals are app policy, not a measured battery-life guarantee. Android uses [battery-state broadcasts](https://developer.android.com/training/monitoring-device-state/battery-monitoring) and Battery Saver state rather than another ML model.

## Browser models

| Option | Model MB | Coverage | License / compute |
| --- | ---: | --- | --- |
| MobileNet V2 1.0 / 224 | 14.04, graph and weights | 1,000 ImageNet labels | Apache-2.0; WebGL GPU with CPU fallback |
| Chrome Gemini Nano | 0 app-bundled weights; Chrome manages model storage/download | Image descriptions where the Prompt API supports image input | Google/Chrome terms; browser-managed compute |

The browser's current options do not include the Android object detectors or battery policy. The download link on the hosted demo opens the native APK releases.

## iOS options

The Xcode starter currently includes **Vision classification, text recognition and barcode scanning**, runs on iOS 16+, and adds **0 MB of model weights**. The options below describe how to extend that starter. System-owned models count as 0 MB added to the app, not zero device storage or RAM.

| Option | Added model size | Categories / purpose | Integration |
| --- | --- | --- | --- |
| Apple Vision classification | 0 MB | General image labels; query the current request's identifiers | Already included |
| Apple Vision text / barcodes | 0 MB | OCR and encoded product identifiers | Already included |
| Vision image feature prints | 0 MB | Image similarity; categories come from your reference catalog | Add reference storage and distance matching |
| Foundation Models, iOS 27+ image input | 0 MB bundled weights | Image descriptions or app-defined structured labels; no fixed object taxonomy | Add an availability-gated image prompt option on eligible Apple Intelligence devices |
| Foundation Models, iOS 26 | 0 MB bundled weights | Text processing of OCR, labels or catalog text | Use after Vision; do not pass image attachments to the iOS 26 API |
| Sensitive Content Analysis | 0 MB | Sensitive/nudity analysis of images or video, not weapons/items | Optional separate workflow with Apple's entitlement and policy checks |
| The seven bundled TFLite models | Same 4.12–10.89 MB individual files listed above | 80 detector classes, 1,000 classifier labels, or saved-reference embeddings | Add a compatible iOS LiteRT/MediaPipe runtime and model-specific preprocessing/postprocessing |
| Core ML detector / classifier / embedder | Depends on the exported `.mlpackage` / compiled model | Determined by its training and labels | Export a supported source model for Core ML; measure the actual packaged size |

Apple's system APIs use Apple SDK/service terms. Bundled Google models retain Apache-2.0; custom models retain their own licenses. Neither OS support nor a framework license changes the terms of third-party weights.

### Built-in image recognition and newer iPhones

For Vision classification, obtain the actual label list with `try VNClassifyImageRequest().supportedIdentifiers()` rather than hard-coding a universal count. Labels depend on the request configuration/revision. [Vision classification identifiers](https://developer.apple.com/documentation/vision/vnclassifyimagerequest/supportedidentifiers())

Vision's `VNGenerateImageFeaturePrintRequest` is an alternative to shipping an embedding model. Store reference feature prints and compare distances; use the same request revision and preprocessing for both references and queries. It supplies similarity, not text-aligned embeddings or a prohibited-item taxonomy. [Image feature prints](https://developer.apple.com/documentation/vision/vngenerateimagefeatureprintrequest)

**iOS 27 adds image attachments to Foundation Models.** An image-capable on-device session can receive a decoded frame, including a `CVPixelBuffer`, and return a description or a guided structured response. Use an iOS 27 availability guard for `Attachment`, check `SystemLanguageModel.default.availability`, and handle unavailable models and rejected requests. Sample occasionally or on demand rather than feeding every video frame. [Apple's multimodal prompting guide](https://developer.apple.com/documentation/foundationmodels/analyzing-images-with-multimodal-prompting), [Attachment availability](https://developer.apple.com/documentation/foundationmodels/attachment)

Apple Intelligence eligibility includes iPhone 15 Pro/Pro Max, iPhone 16 models and later, iPhone Air and iPhone Duo, with region/language and model-readiness conditions. iOS 27 exposes the current system model's `variant` (`core3` or `coreAdvanced3`) and display name; show what the device reports instead of inferring a model from the marketing name. The latest hardware can receive a different system variant. Apple manages substantial system storage separately from the app. [Device requirements](https://support.apple.com/en-us/121115), [System model variants](https://developer.apple.com/documentation/foundationmodels/systemlanguagemodel/variant-swift.struct)

Apple's Private Cloud Compute model and third-party cloud providers are separate options, not on-device inference. Keep any such mode explicit so users can choose whether a frame leaves the device. The starter does not use cloud inference. [Foundation Models overview](https://developer.apple.com/foundation-models-framework/)

Sensitive Content Analysis is useful if the review policy eventually includes sexual imagery. It does not detect knives or firearms. Follow its entitlement, authorization and analysis-policy requirements; it is a separate feature from object detection. [Sensitive Content Analysis](https://developer.apple.com/documentation/sensitivecontentanalysis)

### Bundled models and acceleration

The existing `.tflite` files can be reused with a compatible iOS inference runtime. The detector exports contain their own box postprocessing; their outputs are boxes, category indices, scores and a count. Start with a CPU implementation and compare a supported Metal/Core ML delegate on each target device. Operator support and quantization determine which parts can accelerate. [iOS object detection guide](https://developers.google.com/edge/mediapipe/solutions/vision/object_detector/ios)

For a native Core ML model, export from a supported TensorFlow/PyTorch source using conversion tools, then use `VNCoreMLRequest` or Core ML directly. Renaming a `.tflite` file does not convert it. Set `MLModelConfiguration.computeUnits` to `.all` to allow CPU, GPU and Neural Engine, or compare `.cpuOnly`, `.cpuAndGPU` and `.cpuAndNeuralEngine`. These are allowed compute resources, not proof that every operation ran there. Inspect the model and profile it on the target iPhone. Conversion/quantization can change both file size and recognition results. [Core ML compute configurations](https://developer.apple.com/documentation/coreml/mlcomputeunits)

### iOS battery integration

Enable `UIDevice.current.isBatteryMonitoringEnabled`, read `batteryLevel` and `batteryState`, and observe their change notifications. Treat `batteryLevel == -1` as unknown. Combine this with `ProcessInfo.processInfo.isLowPowerModeEnabled` and its power-state notification. The Android rate table above can be reused as app policy, with one active inference, late-frame dropping, and capture stopped on background entry or critical battery. Thermal-state notifications can trigger additional throttling on a hot device. This battery UI/policy is an extension to implement in the iOS starter. [Battery level](https://developer.apple.com/documentation/uikit/uidevice/batterylevel), [Low Power Mode](https://developer.apple.com/documentation/foundation/processinfo/islowpowermodeenabled)

## Larger and custom models

Tiny custom detectors can add categories that COCO lacks, but they require appropriate training data, evaluation and a verified export/license. A generic 80-class detector cannot acquire firearm or drug recognition by adding names to its label file. Model families such as YOLO nano/tiny are candidates; check the chosen weights and runtime terms, including [Ultralytics licensing](https://www.ultralytics.com/license), before distribution.

[EmbeddingGemma 2](https://huggingface.co/google/embeddinggemma-2) and [TIPSv2 B/14](https://huggingface.co/google/tipsv2-b14) support aligned multimodal embeddings, but their published checkpoints are approximately 1,489 MB and 784 MB respectively. They are not bundled under the small-model budget. A separately produced quantized export needs its own size, compatibility and accuracy measurements; the checkpoint sizes do not predict its runtime RAM.
