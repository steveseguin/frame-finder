package demo.framefinder;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A small, private photo-reference classifier. Stores vectors, never camera photos. */
public final class ItemCatalog {
    private final AtomicFile file;
    private final int dimensions;
    private JSONArray samples;
    public ItemCatalog(Context context, int model) throws Exception {
        if (!ModelChoice.isEmbedding(model)) throw new IllegalArgumentException("Choose an embedding model");
        dimensions = model == 3 ? 1024 : 1280;
        file = new AtomicFile(new File(context.getFilesDir(), "items-" + ModelChoice.FILES[model] + ".json"));
        samples = file.getBaseFile().exists() ? new JSONArray(new String(file.readFully(), StandardCharsets.UTF_8)) : new JSONArray();
        if (samples.length() > 200) throw new IllegalStateException("Reference collection is too large");
        for (int i = 0; i < samples.length(); i++) vector(samples.getJSONObject(i));
    }
    public static float[] normalize(float[] input) {
        double sum = 0;
        for (float value : input) { if ((Float.isNaN(value) || Float.isInfinite(value))) throw new IllegalArgumentException("Invalid embedding"); sum += value * (double)value; }
        if (sum <= 1e-12) throw new IllegalArgumentException("Empty embedding");
        float[] result = new float[input.length];
        for (int i = 0; i < input.length; i++) result[i] = (float)(input[i] / Math.sqrt(sum));
        return result;
    }
    public static float cosine(float[] a, float[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("Embedding sizes differ");
        double sum = 0; for (int i = 0; i < a.length; i++) sum += a[i] * (double)b[i];
        return (float)Math.max(-1, Math.min(1, sum));
    }
    private float[] vector(JSONObject entry) throws Exception {
        JSONArray stored = entry.getJSONArray("vector");
        if (stored.length() != dimensions) throw new IllegalStateException("Reference belongs to another model");
        float[] result = new float[dimensions];
        for (int i = 0; i < result.length; i++) result[i] = (float)stored.getDouble(i);
        return normalize(result);
    }
    public int sampleCount() { return samples.length(); }
    public void save(String name, float[] value) throws Exception {
        name = name.trim();
        if (name.isEmpty() || name.length() > 80) throw new IllegalArgumentException("Enter an item name, up to 80 characters");
        if (samples.length() >= 200) throw new IllegalStateException("200 reference views saved. Clear this collection to start again.");
        if (value.length != dimensions) throw new IllegalArgumentException("Reference belongs to another model");
        JSONArray values = new JSONArray(); for (float f : normalize(value)) values.put((double)f);
        JSONArray updated = new JSONArray(samples.toString());
        updated.put(new JSONObject().put("name", name).put("vector", values));
        write(updated);
    }
    public void clear() throws Exception { write(new JSONArray()); }
    private void write(JSONArray updated) throws Exception {
        FileOutputStream output = null;
        try {
            output = file.startWrite(); output.write(updated.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(output); samples = updated;
        } catch (Exception e) { if (output != null) file.failWrite(output); throw e; }
    }
    public SmallModel.Result match(float[] query) throws Exception {
        Map<String, Float> scores = new LinkedHashMap<>();
        query = normalize(query);
        for (int i = 0; i < samples.length(); i++) {
            JSONObject entry = samples.getJSONObject(i); String name = entry.getString("name");
            float score = cosine(query, vector(entry));
            Float previous = scores.get(name); if (previous == null || score > previous) scores.put(name, score);
        }
        if (scores.isEmpty()) return new SmallModel.Result("Teach it your first item", "Enter a name and save a reference view. Add several angles for each item.", query);
        List<Map.Entry<String, Float>> ranked = new ArrayList<>(scores.entrySet());
        java.util.Collections.sort(ranked, (a, b) -> Float.compare(b.getValue(), a.getValue()));
        float best = ranked.get(0).getValue();
        boolean confident = best >= .80f && (ranked.size() == 1 || best - ranked.get(1).getValue() >= .05f);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(3, ranked.size()); i++) text.append(String.format(Locale.US, "%s · similarity %.3f\n", ranked.get(i).getKey(), ranked.get(i).getValue()));
        text.append(scores.size()).append(" saved item names · ").append(samples.length()).append(" reference views\nSimilarity is not a probability. Match requires ≥0.80 and a ≥0.05 lead over another item.");
        return new SmallModel.Result(confident ? ranked.get(0).getKey() : "No clear catalog match", text.toString(), query);
    }
}
