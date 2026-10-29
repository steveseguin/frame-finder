package demo.framefinder;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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
    private final AtomicBoolean describeRequested = new AtomicBoolean(false);
    private final FrameRecognizer recognizer = new FrameRecognizer();
    private SmallModel smallModel;
    private SystemRecognizer system;
    private ItemCatalog catalog;
    private PreviewView previewView;
    private ImageView sample;
    private Bitmap shownFrame;
    private TextView status, item, details, timing, modelInfo, systemStatus;
    private Spinner picker;
    private Button toggle, describe, recheck, save;
    private EditText itemName;
    private LinearLayout referenceControls;
    private ProcessCameraProvider cameraProvider;
    private volatile boolean active, destroyed;
    private volatile int generation, selected;
    private boolean starting, systemReady;
    private long lastFrame, vectorTime;
    private float[] lastVector;
    private int availabilityRequest;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startCamera(); else status.setText("Allow camera access in Settings, then tap Start camera.");
        });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        smallModel = new SmallModel(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(17, 23, 20));
        int padding = dp(18);
        ViewCompat.setOnApplyWindowInsetsListener(layout, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom);
            return insets;
        });
        addText(layout, "FRAME FINDER", 13);
        addText(layout, "Point. Hold. Identify.", 27);
        addText(layout, "Recognition model - stop the camera to change", 12);
        picker = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ModelChoice.NAMES);
        picker.setAdapter(adapter); picker.setMinimumHeight(dp(48)); layout.addView(picker);
        modelInfo = addText(layout, "", 13);
        systemStatus = addText(layout, "", 13);
        recheck = new Button(this); recheck.setText("Recheck system AI"); layout.addView(recheck);
        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        layout.addView(previewView, new LinearLayout.LayoutParams(-1, dp(260)));
        toggle = new Button(this); toggle.setText("Start camera"); layout.addView(toggle);
        describe = new Button(this); describe.setText("Describe frame"); layout.addView(describe);
        status = addText(layout, "Hold one item near the center. No microphone is needed.", 14);
        item = addText(layout, "Nothing identified yet", 25);
        details = addText(layout, "", 15); details.setTextIsSelectable(true);
        timing = addText(layout, "", 12);
        sample = new ImageView(this); sample.setContentDescription("Last analyzed frame");
        layout.addView(sample, new LinearLayout.LayoutParams(dp(96), dp(96)));
        referenceControls = new LinearLayout(this); referenceControls.setOrientation(LinearLayout.VERTICAL);
        addText(referenceControls, "My items - save 3-5 angles under the same name. Match the item in the small analyzed preview. References stay on this phone.", 13);
        itemName = new EditText(this); itemName.setHint("Item name, e.g. Blue travel mug"); itemName.setSingleLine(true);
        itemName.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        referenceControls.addView(itemName);
        save = new Button(this); save.setText("Save reference view"); referenceControls.addView(save);
        Button clear = new Button(this); clear.setText("Clear this model's references"); referenceControls.addView(clear);
        layout.addView(referenceControls);
        addText(layout, "Recognition runs on this device. Categories, descriptions and similarity matches are estimates, not verified product identities.", 13);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(layout); setContentView(scroll);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { selectModel(position); }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        toggle.setOnClickListener(view -> {
            if (active || starting) stopCamera();
            else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
            else permission.launch(Manifest.permission.CAMERA);
        });
        recheck.setOnClickListener(view -> checkSystem());
        describe.setOnClickListener(view -> { if (active && systemReady && !busy.get()) { describeRequested.set(true); describe.setEnabled(false); status.setText("Waiting for a camera frame..."); } });
        save.setOnClickListener(view -> saveReference());
        clear.setOnClickListener(view -> {
            if (catalog == null) return;
            ItemCatalog current = catalog;
            new AlertDialog.Builder(this).setTitle("Clear saved reference views?")
                .setMessage("This clears this embedding model's item names and reference vectors on this phone.")
                .setNegativeButton("Cancel", null).setPositiveButton("Clear", (dialog, which) -> {
                    try { current.clear(); item.setText("Teach it your first item"); details.setText(""); status.setText("Reference collection cleared."); }
                    catch (Exception e) { status.setText(e.getMessage()); }
                }).show();
        });
        selectModel(0);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView addText(LinearLayout parent, String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size);
        view.setTextColor(Color.rgb(235, 242, 230)); view.setPadding(0, dp(8), 0, dp(8));
        parent.addView(view); return view;
    }
    private void controls() {
        previewView.setKeepScreenOn(active || starting);
        picker.setEnabled(!active && !starting && !busy.get());
        toggle.setEnabled(!busy.get() || active);
        toggle.setText(active || starting ? "Stop camera" : "Start camera");
        describe.setEnabled(active && systemReady && !busy.get() && !describeRequested.get());
        save.setEnabled(active && lastVector != null && catalog != null);
    }
    private void selectModel(int model) {
        if (active || starting || busy.get()) return;
        selected = model; generation++; availabilityRequest++;
        lastVector = null; catalog = null;
        modelInfo.setText(ModelChoice.INFO[model]);
        boolean nano = model == 5;
        describe.setVisibility(nano ? View.VISIBLE : View.GONE);
        recheck.setVisibility(nano ? View.VISIBLE : View.GONE);
        systemStatus.setVisibility(nano ? View.VISIBLE : View.GONE);
        referenceControls.setVisibility(ModelChoice.isEmbedding(model) ? View.VISIBLE : View.GONE);
        item.setText("Nothing identified yet"); details.setText(""); timing.setText("");
        sample.setImageDrawable(null); if (shownFrame != null) shownFrame.recycle(); shownFrame = null;
        try { if (ModelChoice.isEmbedding(model)) catalog = new ItemCatalog(this, model); }
        catch (Exception e) { status.setText("Could not load references: " + e.getMessage()); }
        if (nano) checkSystem();
        controls();
    }
    private void checkSystem() {
        final int request = ++availabilityRequest;
        systemReady = false; recheck.setEnabled(false); controls();
        systemStatus.setText("Checking Android's image-description capability...");
        try {
            if (system == null) system = new SystemRecognizer(this);
            system.check((ready, message) -> {
                if (destroyed || request != availabilityRequest) return;
                systemReady = ready; systemStatus.setText(message); recheck.setEnabled(true); controls();
            });
        } catch (Exception e) { systemStatus.setText("System AI unavailable: " + e.getMessage()); recheck.setEnabled(true); }
    }
    private void saveReference() {
        if (catalog == null || lastVector == null || !active || SystemClock.elapsedRealtime() - vectorTime > 3000) {
            status.setText("Wait for a fresh analyzed frame, then save again."); return;
        }
        try { catalog.save(itemName.getText().toString(), lastVector); status.setText("Saved reference view - " + catalog.sampleCount() + " views in this collection"); }
        catch (Exception e) { status.setText(e.getMessage()); }
    }

    private void startCamera() {
        if (destroyed || active || starting || busy.get()) return;
        int request = ++generation;
        starting = true; lastFrame = 0; lastVector = null;
        controls(); status.setText("Opening camera...");
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
                status.setText(selected == 5 ? "Tap Describe frame for a system AI description." : "Recognizing locally - center one item in the preview");
            } catch (Exception error) { status.setText("Camera failed: " + error.getMessage()); }
            starting = false; controls();
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyze(ImageProxy proxy) {
        long now = SystemClock.elapsedRealtime();
        int model = selected;
        if (!active || now - lastFrame < 1000 || (model == 5 && !describeRequested.get()) || !busy.compareAndSet(false, true)) { proxy.close(); return; }
        int request = generation;
        lastFrame = now; describeRequested.set(false);
        Bitmap image;
        try {
            Bitmap raw = proxy.toBitmap();
            Matrix rotation = new Matrix(); rotation.postRotate(proxy.getImageInfo().getRotationDegrees());
            Bitmap upright = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), rotation, true);
            if (upright != raw) raw.recycle();
            if (model == 0) {
                // Preserve full-frame text and barcode scanning in the original ML Kit mode.
                image = upright;
            } else {
                int side = Math.min(upright.getWidth(), upright.getHeight());
                Bitmap crop = Bitmap.createBitmap(upright, (upright.getWidth() - side) / 2, (upright.getHeight() - side) / 2, side, side);
                if (crop != upright) upright.recycle();
                image = Bitmap.createScaledBitmap(crop, Math.min(side, 512), Math.min(side, 512), true);
                if (image != crop) crop.recycle();
            }
        } catch (Exception error) {
            busy.set(false);
            runOnUiThread(() -> { if (active && request == generation) { status.setText("Frame conversion failed: " + error.getMessage()); controls(); } });
            return;
        } finally { proxy.close(); }
        runOnUiThread(this::controls);
        long start = SystemClock.elapsedRealtime();
        FrameRecognizer.Callback callback = (name, text, elapsed, error) -> runOnUiThread(() -> finish(request, model, image, name, text, elapsed, null, error));
        try {
            if (model == 0) recognizer.recognize(InputImage.fromBitmap(image, 0), callback);
            else if (model == 5) system.recognize(image, callback);
            else {
                SmallModel.Result result = smallModel.recognize(model, image);
                long elapsed = SystemClock.elapsedRealtime() - start;
                runOnUiThread(() -> finish(request, model, image, result.name, result.details, elapsed, result.vector, null));
            }
        } catch (Exception error) { callback.complete("No result", "", 0, error); }
    }
    private void finish(int request, int model, Bitmap image, String name, String text, long elapsed, float[] vector, Exception error) {
        busy.set(false);
        if (destroyed) { image.recycle(); recognizer.close(); if (system != null) system.close(); return; }
        if (!active || request != generation) { image.recycle(); controls(); return; }
        if (shownFrame != null) { sample.setImageDrawable(null); shownFrame.recycle(); }
        shownFrame = image; sample.setImageBitmap(image);
        lastVector = null;
        try {
            if (error != null) throw error;
            if (vector != null && catalog != null) {
                SmallModel.Result match = catalog.match(vector); name = match.name; text = match.details;
                lastVector = vector.clone(); vectorTime = SystemClock.elapsedRealtime();
            }
            item.setText(name); details.setText(text);
            status.setText(model == 5 ? "Description ready. Tap Describe frame to sample again." : "Recognizing locally - estimates only");
            timing.setText(elapsed + " ms - " + (model == 0 ? "ML Kit - full frame" : model == 5 ? "Android AICore - center crop" : "CPU - 2 threads - center crop"));
        } catch (Exception e) { status.setText("Recognition failed: " + e.getMessage()); item.setText("No current result"); details.setText(""); timing.setText(""); }
        controls();
    }
    private void stopCamera() {
        active = false; starting = false; generation++; describeRequested.set(false); lastVector = null;
        if (cameraProvider != null) cameraProvider.unbindAll();
        controls(); status.setText("Camera stopped. Choose a model, then tap Start camera.");
    }
    @Override protected void onStop() { stopCamera(); super.onStop(); }
    @Override protected void onDestroy() {
        destroyed = true; availabilityRequest++;
        analysisExecutor.execute(() -> smallModel.close()); analysisExecutor.shutdown();
        if (!busy.get()) { recognizer.close(); if (system != null) system.close(); }
        sample.setImageDrawable(null); if (shownFrame != null) shownFrame.recycle();
        super.onDestroy();
    }
}
