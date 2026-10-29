package demo.framefinder;

/** Sampling policy; no model or camera work runs in this class. */
public final class BatteryPolicy {
    public static long interval(boolean adaptive, int percent, boolean plugged, boolean powerSave) {
        if (!adaptive) return 1000;
        if (plugged) return 500;
        if (percent >= 0 && percent <= 10) return 0;
        if (powerSave || (percent >= 0 && percent <= 20)) return 5000;
        return 1000;
    }
    public static String description(boolean adaptive, int percent, boolean plugged, boolean powerSave) {
        long interval = interval(adaptive, percent, plugged, powerSave);
        String battery = percent < 0 ? "level unavailable" : percent + "%";
        String mode = interval == 0 ? "Camera paused at 10% or lower. Connect power or disable battery-aware sampling, then tap Start camera."
            : "Checks at most every " + (interval / 1000.0) + " s" + (adaptive ? "." : " (fixed rate).");
        return "Battery: " + battery + (plugged ? " · power connected" : " · unplugged")
            + (powerSave ? " · Battery Saver on" : "") + "\n" + mode
            + "\nSystem AI still requires a tap. Actual speed also depends on inference time.";
    }
    private BatteryPolicy() {}
}
