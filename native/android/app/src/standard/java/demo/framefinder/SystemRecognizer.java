package demo.framefinder;

import android.content.Context;
import android.graphics.Bitmap;

/** The standard build retains Android 6/7 support without the Android 8+ GenAI SDK. */
public final class SystemRecognizer implements AutoCloseable {
    public interface Availability { void complete(boolean ready, String message); }
    public SystemRecognizer(Context context) {}
    public void check(Availability callback) { callback.complete(false, "System AI requires the systemAi APK on Android 8+ and a supported device. The bundled models work in this build."); }
    public void recognize(Bitmap image, FrameRecognizer.Callback callback) { callback.complete("Unavailable", "", 0, new Exception("System AI is not included in the standard APK")); }
    @Override public void close() {}
}
