package demo.framefinder;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Coordinates are normalized to the complete upright analyzed frame. */
public final class DetectedObject {
    public final String name;
    public final float score, left, top, right, bottom;
    DetectedObject(String name, float score, float left, float top, float right, float bottom) {
        this.name = name; this.score = score; this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }
    public static String summary(List<DetectedObject> objects, Set<String> review) {
        java.util.Set<String> flagged = new java.util.TreeSet<>();
        for (DetectedObject object : objects) if (review.contains(object.name)) flagged.add(object.name);
        if (!flagged.isEmpty()) return "Review: " + android.text.TextUtils.join(", ", flagged);
        return objects.isEmpty() ? "No objects above threshold" : objects.size() + " objects detected";
    }
    public static String details(List<DetectedObject> objects, Set<String> review, float threshold) {
        StringBuilder text = new StringBuilder();
        for (DetectedObject object : objects) text.append(String.format(Locale.US, "%s%s · %.0f%%\n",
            review.contains(object.name) ? "REVIEW · " : "", object.name, object.score * 100));
        text.append(String.format(Locale.US, "\nThreshold: %.0f%% · model output limits apply.\n", threshold * 100));
        text.append("Boxes mark the last analyzed frame below, not the live preview. Review flags are category matches, not a prohibited-item decision. Missed detections do not mean an item is safe.");
        return text.toString();
    }
    public static void draw(Bitmap frame, List<DetectedObject> objects, Set<String> review) {
        Canvas canvas = new Canvas(frame); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float unit = Math.max(frame.getWidth(), frame.getHeight()) / 500f;
        paint.setStrokeWidth(Math.max(2, 2 * unit)); paint.setTextSize(16 * unit);
        for (DetectedObject object : objects) {
            int color = review.contains(object.name) ? Color.rgb(255, 170, 60) : Color.rgb(105, 240, 174);
            float left = object.left * frame.getWidth(), top = object.top * frame.getHeight();
            paint.setColor(color); paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(left, top, object.right * frame.getWidth(), object.bottom * frame.getHeight(), paint);
            String label = String.format(Locale.US, "%s %.0f%%", object.name, object.score * 100);
            float width = paint.measureText(label) + 8 * unit;
            float x = Math.max(0, Math.min(left, frame.getWidth() - width));
            float baseline = Math.max(paint.getTextSize(), Math.min(frame.getHeight() - 4 * unit, top));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(x, baseline - paint.getTextSize(), x + width, baseline + 4 * unit, paint);
            paint.setColor(Color.BLACK); canvas.drawText(label, x + 4 * unit, baseline, paint);
        }
    }
}
