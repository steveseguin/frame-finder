package demo.framefinder;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.imagedescription.ImageDescription;
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest;
import com.google.mlkit.genai.imagedescription.ImageDescriptionResult;
import com.google.mlkit.genai.imagedescription.ImageDescriber;
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions;
import java.util.concurrent.Executor;

/** AICore serves both supported Pixels and Samsungs; branding is not a capability check. */
public final class SystemRecognizer implements AutoCloseable {
    public interface Availability { void complete(boolean ready, String message); }
    private final Executor main;
    private final ImageDescriber client;
    private boolean closed;
    public SystemRecognizer(Context context) {
        main = ContextCompat.getMainExecutor(context);
        client = ImageDescription.getClient(ImageDescriberOptions.builder(context.getApplicationContext()).build());
    }
    public void check(Availability callback) {
        ListenableFuture<Integer> future = client.checkFeatureStatus();
        java.util.concurrent.atomic.AtomicBoolean answered = new java.util.concurrent.atomic.AtomicBoolean();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable timeout = () -> {
            if (!closed && answered.compareAndSet(false, true)) callback.complete(false, "Android's system AI check timed out. Recheck availability or choose a bundled model.");
        };
        handler.postDelayed(timeout, 15000);
        future.addListener(() -> {
            handler.removeCallbacks(timeout);
            if (closed || !answered.compareAndSet(false, true)) return;
            try {
                int state = future.get();
                if (state == FeatureStatus.AVAILABLE) callback.complete(true, "System Gemini Nano is ready on this phone. Tap Describe frame after starting the camera.");
                else if (state == FeatureStatus.DOWNLOADABLE) callback.complete(false, "Image description is supported, but its system model is not installed. No download was started; it may exceed 50 MB. Choose a bundled model to continue.");
                else if (state == FeatureStatus.DOWNLOADING) callback.complete(false, "Android is preparing its system model. Recheck availability when it finishes, or choose a bundled model.");
                else callback.complete(false, "System image description is unavailable on this phone. Choose a bundled model; an installed AICore app alone does not guarantee support.");
            } catch (Exception e) { callback.complete(false, "System image description could not be enabled: " + reason(e) + ". Choose a bundled model or recheck after an Android/AICore update."); }
        }, main);
    }
    public void recognize(Bitmap image, FrameRecognizer.Callback callback) {
        long start = SystemClock.elapsedRealtime();
        ListenableFuture<ImageDescriptionResult> future = client.runInference(ImageDescriptionRequest.builder(image).build());
        future.addListener(() -> {
            try { callback.complete("Scene description", future.get().getDescription(), SystemClock.elapsedRealtime() - start, null); }
            catch (Exception e) { callback.complete("No description", "", 0, new Exception(reason(e))); }
        }, main);
    }
    private static String reason(Exception e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        String text = cause.getMessage();
        return text == null ? cause.getClass().getSimpleName() : text.substring(0, Math.min(text.length(), 280));
    }
    @Override public void close() { closed = true; client.close(); }
}
