package demo.framefinder;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** All methods run on the same single-thread executor. */
public final class SmallModel implements AutoCloseable {
    private final Context context;
    private Interpreter interpreter;
    private int current = -1;
    private final List<String> labels = new ArrayList<>();
    public SmallModel(Context context) { this.context = context.getApplicationContext(); }

    public static final class Result {
        public final String name, details;
        public final float[] vector;
        Result(String name, String details, float[] vector) { this.name = name; this.details = details; this.vector = vector; }
    }

    public Result recognize(int model, Bitmap image) throws Exception {
        if (model < 1 || model > 4) throw new IllegalArgumentException("Choose a small model");
        if (current != model) {
            close();
            try (AssetFileDescriptor asset = context.getAssets().openFd("models/" + ModelChoice.FILES[model]);
                 FileInputStream stream = new FileInputStream(asset.getFileDescriptor())) {
                ByteBuffer weights = stream.getChannel().map(FileChannel.MapMode.READ_ONLY, asset.getStartOffset(), asset.getDeclaredLength());
                interpreter = new Interpreter(weights, new Interpreter.Options().setNumThreads(2));
            }
            if (!ModelChoice.isEmbedding(model)) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("models/imagenet-labels.txt"), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line; while ((line = reader.readLine()) != null) labels.add(line);
                }
            }
            current = model;
        }
        Tensor input = interpreter.getInputTensor(0), output = interpreter.getOutputTensor(0);
        int[] shape = input.shape();
        Bitmap resized = Bitmap.createScaledBitmap(image, shape[2], shape[1], true);
        int[] pixels = new int[shape[1] * shape[2]];
        resized.getPixels(pixels, 0, shape[2], 0, 0, shape[2], shape[1]);
        if (resized != image) resized.recycle();
        ByteBuffer in = ByteBuffer.allocateDirect(input.numBytes()).order(ByteOrder.nativeOrder());
        boolean embedding = ModelChoice.isEmbedding(model);
        // Normalization comes from these pinned models' TFLite metadata.
        float mean = embedding ? 0f : 127f, std = embedding ? 255f : 128f;
        for (int pixel : pixels) for (int shift = 16; shift >= 0; shift -= 8) {
            float normalized = (((pixel >> shift) & 255) - mean) / std;
            if (input.dataType() == DataType.FLOAT32) in.putFloat(normalized);
            else if (input.dataType() == DataType.UINT8) {
                int quantized = Math.round(normalized / input.quantizationParams().getScale()) + input.quantizationParams().getZeroPoint();
                in.put((byte)Math.max(0, Math.min(255, quantized)));
            } else throw new IllegalStateException("Unsupported model input type");
        }
        in.rewind();
        ByteBuffer out = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder());
        interpreter.run(in, out); out.rewind();
        float[] values = new float[output.numElements()];
        for (int i = 0; i < values.length; i++) {
            if (output.dataType() == DataType.FLOAT32) values[i] = out.getFloat();
            else if (output.dataType() == DataType.UINT8) values[i] = ((out.get() & 255) - output.quantizationParams().getZeroPoint()) * output.quantizationParams().getScale();
            else throw new IllegalStateException("Unsupported model output type");
            if ((Float.isNaN(values[i]) || Float.isInfinite(values[i]))) throw new IllegalStateException("Model returned invalid values");
        }
        if (embedding) return new Result("", "", ItemCatalog.normalize(values));
        if (labels.size() != values.length) throw new IllegalStateException("Model labels do not match outputs");
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < values.length; i++) indices.add(i);
        java.util.Collections.sort(indices, (a, b) -> Float.compare(values[b], values[a]));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 3; i++) { int k = indices.get(i); text.append(String.format(Locale.US, "%s · %.0f%%\n", labels.get(k), values[k] * 100)); }
        int best = indices.get(0);
        return new Result(values[best] >= .35f ? labels.get(best) : "No clear match", text.toString(), null);
    }
    @Override public void close() { if (interpreter != null) interpreter.close(); interpreter = null; current = -1; labels.clear(); }
}
