package demo.framefinder;

public final class ModelChoice {
    public static final String[] NAMES = {"ML Kit · labels, text & barcodes", "EfficientNet-Lite0 · fast classifier",
        "EfficientNet-Lite2 · larger classifier", "MobileNet V3 Small · my items", "MobileNet V3 Large · my items",
        "System Gemini Nano · Pixel / Samsung"};
    public static final String[] FILES = {null, "efficientnet-lite0-int8.tflite", "efficientnet-lite2-int8.tflite",
        "mobilenet-v3-small-embedding.tflite", "mobilenet-v3-large-embedding.tflite", null};
    public static final String[] INFO = {
        "3.04 MB labeling model · 447 model labels, plus Latin text & barcodes. Google SDK terms. OCR, barcode models and runtime libraries are additional. ML Kit filters returned labels.",
        "5.43 MB · Apache-2.0 · 1,000 ImageNet classes · int8 · CPU, 2 threads. Category estimates, not exact product IDs.",
        "7.17 MB · Apache-2.0 · 1,000 ImageNet classes · int8 · CPU, 2 threads. More compute than Lite0; compare results on your items.",
        "4.12 MB · Apache-2.0 · 1,024-value image embeddings · CPU, 2 threads. Classes are the item names you save. Photo matching only, not text prompts.",
        "10.89 MB · Apache-2.0 · 1,280-value image embeddings · CPU, 2 threads. Classes are the item names you save. Separate reference collection from Small.",
        "Android AICore / Gemini Nano · Google terms · open-ended descriptions, no fixed class count. Model size varies and can exceed 50 MB. Uses the installed system model; this app does not start model downloads. Availability is checked on this phone."};
    public static boolean isEmbedding(int model) { return model == 3 || model == 4; }
    private ModelChoice() {}
}
