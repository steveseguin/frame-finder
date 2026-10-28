package demo.framefinder;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import java.util.List;
import java.util.Locale;

/** Accepts decoded camera, WebRTC or HLS pixels. Submit only one image at a time. */
public final class FrameRecognizer implements AutoCloseable {
    private final ImageLabeler labeler = ImageLabeling.getClient(
        new ImageLabelerOptions.Builder().setConfidenceThreshold(0.1f).build());
    private final TextRecognizer textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final BarcodeScanner scanner = BarcodeScanning.getClient();

    public interface Callback { void complete(String item, String details, long milliseconds, Exception error); }

    public void recognize(InputImage image, Callback callback) {
        final long start = android.os.SystemClock.elapsedRealtime();
        Task<List<ImageLabel>> labels = labeler.process(image);
        Task<Text> text = textRecognizer.process(image);
        Task<List<Barcode>> codes = scanner.process(image);
        // All readers must finish before the camera's ImageProxy can be closed.
        Tasks.whenAllComplete(labels, text, codes).addOnCompleteListener(done -> {
            if (!labels.isSuccessful() || !text.isSuccessful() || !codes.isSuccessful()) {
                Exception error = !labels.isSuccessful() ? labels.getException() : !text.isSuccessful() ? text.getException() : codes.getException();
                callback.complete("No result", "", 0, error != null ? error : new Exception("Recognition was canceled"));
                return;
            }
            List<ImageLabel> found = labels.getResult();
            found.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
            String item = !found.isEmpty() && found.get(0).getConfidence() >= 0.35f ? found.get(0).getText() : "No clear match";
            StringBuilder details = new StringBuilder();
            for (int i = 0; i < Math.min(3, found.size()); i++) {
                ImageLabel label = found.get(i);
                details.append(String.format(Locale.US, "%s · %.0f%%\n", label.getText(), label.getConfidence() * 100));
            }
            if (!text.getResult().getText().isEmpty()) details.append("\nVISIBLE TEXT\n").append(text.getResult().getText());
            for (Barcode code : codes.getResult()) {
                if (code.getRawValue() != null) details.append("\nBARCODE\n").append(code.getRawValue());
            }
            callback.complete(item, details.toString(), android.os.SystemClock.elapsedRealtime() - start, null);
        });
    }

    @Override public void close() { labeler.close(); textRecognizer.close(); scanner.close(); }
}
