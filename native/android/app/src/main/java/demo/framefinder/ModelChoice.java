package demo.framefinder;

public final class ModelChoice {
    public static final String[] NAMES = {"ML Kit · labels, text & barcodes", "EfficientNet-Lite0 · fast classifier",
        "EfficientNet-Lite2 · larger classifier", "MobileNet V3 Small · my items", "MobileNet V3 Large · my items",
        "System Gemini Nano · Pixel / Samsung", "EfficientDet-Lite0 · object detector",
        "EfficientDet-Lite2 · larger detector", "SSD MobileNet V1 · fast detector"};
    public static final String[] FILES = {null, "efficientnet-lite0-int8.tflite", "efficientnet-lite2-int8.tflite",
        "mobilenet-v3-small-embedding.tflite", "mobilenet-v3-large-embedding.tflite", null,
        "efficientdet-lite0-int8.tflite", "efficientdet-lite2-int8.tflite", "ssd-mobilenet-v1-int8.tflite"};
    public static final String[] INFO = {
        "3.04 MB labeling model · 447 model labels, plus Latin text & barcodes. Google SDK terms. OCR, barcode models and runtime libraries are additional. ML Kit filters returned labels.",
        "5.43 MB · Apache-2.0 · 1,000 ImageNet classes · int8 · CPU, 2 threads. Category estimates, not exact product IDs.",
        "7.17 MB · Apache-2.0 · 1,000 ImageNet classes · int8 · CPU, 2 threads. More compute than Lite0; compare results on your items.",
        "4.12 MB · Apache-2.0 · 1,024-value image embeddings · CPU, 2 threads. Classes are the item names you save. Photo matching only, not text prompts.",
        "10.89 MB · Apache-2.0 · 1,280-value image embeddings · CPU, 2 threads. Classes are the item names you save. Separate reference collection from Small.",
        "0 MB added system model weights · Android AICore / Gemini Nano · Google terms · open-ended descriptions, no fixed class count. Existing Android model storage is excluded. Availability is checked on this phone; this app does not start model downloads.",
        "4.56 MB · Apache-2.0 · 80 COCO categories · int8 · 320 × 320 · CPU, 2 threads. Detects multiple objects, including knives and scissors. No firearm, drug, tobacco or alcohol-specific categories.",
        "7.56 MB · Apache-2.0 · 80 COCO categories · int8 · 448 × 448 · CPU, 2 threads. Larger input and more compute than Lite0; compare small-object results. Same categories, including knives and scissors; no firearm, drug, tobacco or alcohol-specific categories.",
        "4.19 MB · Apache-2.0 · 80 COCO categories · uint8 · 300 × 300 · CPU, 2 threads. Lightweight SSD alternative; compare speed and missed objects. Same categories, including knives and scissors; no firearm, drug, tobacco or alcohol-specific categories."};
    public static boolean isEmbedding(int model) { return model == 3 || model == 4; }
    public static boolean isDetector(int model) { return model >= 6 && model <= 8; }
    private ModelChoice() {}
}
