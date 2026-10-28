package demo.framefinder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends ComponentActivity {
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final FrameRecognizer recognizer = new FrameRecognizer();
    private PreviewView previewView;
    private TextView status, item, details, timing;
    private Button toggle;
    private ProcessCameraProvider cameraProvider;
    private volatile boolean active = false;
    private volatile boolean destroyed = false;
    private volatile int generation = 0;
    private long lastFrame = 0;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startCamera(); else status.setText("Allow camera access in Settings, then tap Start camera.");
        });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(17, 23, 20));
        int padding = dp(22);
        ViewCompat.setOnApplyWindowInsetsListener(layout, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom);
            return insets;
        });
        addText(layout, "FRAME FINDER", 13);
        addText(layout, "Point. Hold. Identify.", 29);
        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        layout.addView(previewView, new LinearLayout.LayoutParams(-1, dp(330)));
        toggle = new Button(this); toggle.setText("Start camera"); layout.addView(toggle);
        status = addText(layout, "Hold one item close to the camera. No microphone is needed.", 14);
        item = addText(layout, "Nothing identified yet", 25);
        details = addText(layout, "", 16); details.setTextIsSelectable(true);
        timing = addText(layout, "", 12);
        addText(layout, "Recognition runs on this device. Categories, text and barcodes are clues, not verified product identities.", 13);
        ScrollView scroll = new ScrollView(this); scroll.addView(layout); setContentView(scroll);
        toggle.setOnClickListener(view -> {
            if (active) stopCamera();
            else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
            else permission.launch(Manifest.permission.CAMERA);
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView addText(LinearLayout parent, String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size);
        view.setTextColor(Color.rgb(235, 242, 230)); view.setPadding(0, dp(9), 0, dp(9));
        parent.addView(view); return view;
    }

    private void startCamera() {
        if (destroyed || active) return;
        int request = ++generation;
        toggle.setEnabled(false); status.setText("Opening camera…");
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (destroyed || request != generation) return;
            try {
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(analysisExecutor, this::analyze);
                CameraSelector selector = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, analysis);
                active = true;
                toggle.setText("Stop camera");
                status.setText("Recognizing locally · approximately once per second");
            } catch (Exception error) { status.setText("Camera failed: " + error.getMessage()); }
            toggle.setEnabled(true);
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyze(ImageProxy proxy) {
        long now = SystemClock.elapsedRealtime();
        if (!active || now - lastFrame < 1000 || !busy.compareAndSet(false, true)) { proxy.close(); return; }
        int request = generation;
        if (proxy.getImage() == null) { proxy.close(); busy.set(false); return; }
        lastFrame = now;
        try {
            InputImage image = InputImage.fromMediaImage(proxy.getImage(), proxy.getImageInfo().getRotationDegrees());
            recognizer.recognize(image, (name, result, elapsed, error) -> {
                proxy.close(); busy.set(false);
                if (destroyed) { recognizer.close(); return; }
                if (!active || request != generation) return;
                if (error != null) {
                    status.setText("Recognition failed: " + error.getMessage());
                    item.setText("No result"); details.setText("");
                } else {
                    status.setText("Recognizing locally · scores are estimates");
                    item.setText(name); details.setText(result);
                    timing.setText(elapsed + " ms · ML Kit · full frame");
                }
            });
        } catch (Exception error) {
            proxy.close(); busy.set(false);
            runOnUiThread(() -> { if (active && request == generation) status.setText("Recognition failed: " + error.getMessage()); });
        }
    }

    private void stopCamera() {
        active = false; generation++;
        if (cameraProvider != null) cameraProvider.unbindAll();
        toggle.setEnabled(true); toggle.setText("Start camera");
        status.setText("Camera stopped. Tap Start camera to resume.");
    }
    @Override protected void onStop() { stopCamera(); super.onStop(); }
    @Override protected void onDestroy() {
        destroyed = true;
        analysisExecutor.shutdown();
        if (!busy.get()) recognizer.close();
        super.onDestroy();
    }
}
