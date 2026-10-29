package demo.framefinder;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ModelDetails {
    public static String backend(int model) {
        if (model == 0) return "Compute: ML Kit managed. This API does not report CPU/GPU/TPU attribution.";
        if (model == 5) return "Compute: Android AICore managed. This API does not report the active CPU/GPU/TPU accelerator.";
        return "Compute: CPU, 2 inference threads. No GPU or TPU delegate is configured.";
    }
    public static List<String> categories(Context context, int model) throws Exception {
        List<String> names = new ArrayList<>();
        if (model > 2 && !ModelChoice.isDetector(model)) return names;
        String asset = ModelChoice.isDetector(model) ? "models/coco-labels.txt" : model == 0 ? "models/mlkit-labels.txt" : "models/imagenet-labels.txt";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(asset), StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) if (!line.trim().isEmpty() && !line.equals("???")) names.add(line.trim());
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }
    // Inspect the installed APK(s), including the actual ZIP compression of model assets.
    public static String[] disk(Context context) throws Exception {
        ApplicationInfo app = context.getApplicationInfo();
        List<String> apks = new ArrayList<>(); apks.add(app.sourceDir);
        if (app.splitSourceDirs != null) Collections.addAll(apks, app.splitSourceDirs);
        int count = ModelChoice.NAMES.length;
        long[] packed = new long[count], unpacked = new long[count]; long total = 0;
        for (String path : apks) {
            total += new File(path).length();
            try (ZipFile zip = new ZipFile(path)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement(); if (entry.isDirectory()) continue;
                    int model = -1;
                    if (entry.getName().startsWith("assets/mlkit")) model = 0;
                    else for (int i = 1; i < count; i++) if (ModelChoice.FILES[i] != null && entry.getName().equals("assets/models/" + ModelChoice.FILES[i])) { model = i; break; }
                    if (model >= 0) { packed[model] += entry.getCompressedSize(); unpacked[model] += entry.getSize(); }
                }
            }
        }
        String[] descriptions = new String[count];
        for (int i = 0; i < count; i++) {
            String selected = i == 5 ? "System model weights added to app: 0 MB\nExisting Android model storage is excluded."
                : String.format(Locale.US, "%s in APK: %.2f MB\nUncompressed selected assets: %.2f MB",
                    i == 0 ? "ML Kit model assets (labels + OCR + barcodes)" : "Selected model weights", packed[i] / 1e6, unpacked[i] / 1e6);
            descriptions[i] = selected + String.format(Locale.US, "\nWhole app APK(s): %.2f MB", total / 1e6)
                + "\nShared SDKs, native libraries, labels and ZIP overhead are not attributed to a model. Installed code/cache can use additional disk. All bundled weights are already in the APK; selecting a model adds no download.";
        }
        return descriptions;
    }
    private ModelDetails() {}
}
