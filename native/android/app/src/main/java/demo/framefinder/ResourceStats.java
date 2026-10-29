package demo.framefinder;

import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import java.util.Locale;

/** Per-camera-run observations of this process, not isolated model allocations. */
public final class ResourceStats {
    private long baselinePss = -1, latestPss = -1, peakPss = -1;
    private long sampledAt, cpuAtSample, cpuWindowMs;
    private double cpuPercent = -1;
    private int count;
    private long firstLatency, lastLatency, laterTotal, lastCpuMs;

    // Called on the analysis executor before the first inference.
    public synchronized void begin() { if (sampledAt == 0) sample(); }
    public synchronized void record(long latency, long cpuMs) {
        if (count++ == 0) firstLatency = latency; else laterTotal += latency;
        lastLatency = latency; lastCpuMs = Math.max(0, cpuMs);
    }
    public synchronized boolean shouldSample() {
        return count == 1 || SystemClock.elapsedRealtime() - sampledAt >= 2000;
    }
    // Memory sampling stays outside the measured inference interval and off the UI thread.
    public synchronized void sample() {
        long now = SystemClock.elapsedRealtime(), cpu = Process.getElapsedCpuTime();
        if (sampledAt != 0 && now - sampledAt >= 1000) {
            cpuWindowMs = now - sampledAt;
            cpuPercent = Math.max(0, 100.0 * (cpu - cpuAtSample) / cpuWindowMs);
        }
        long pss;
        try { pss = Debug.getPss(); } catch (RuntimeException e) { pss = 0; }
        if (pss > 0) {
            if (baselinePss < 0) baselinePss = pss;
            latestPss = pss; peakPss = Math.max(peakPss, pss);
        }
        sampledAt = now; cpuAtSample = cpu;
    }
    public synchronized String summary() {
        if (count == 0) return "Start camera to measure RAM, CPU and latency. System AI also needs a Describe frame request.";
        StringBuilder out = new StringBuilder();
        if (latestPss > 0) {
            out.append(String.format(Locale.US, "App RAM (PSS): %.1f MB\nChange from before first check: %+.1f MB\nSampled RAM peak: %.1f MB\n",
                latestPss * 1024.0 / 1e6, (latestPss - baselinePss) * 1024.0 / 1e6, peakPss * 1024.0 / 1e6));
        } else out.append("App RAM: unavailable\n");
        if (cpuPercent >= 0) out.append(String.format(Locale.US, "App CPU: %.1f%% of one core (last %.1f s)\n", cpuPercent, cpuWindowMs / 1000.0));
        else out.append("App CPU: collecting a longer sample…\n");
        out.append("App CPU time during last check: ").append(lastCpuMs).append(" ms\n");
        out.append("First check: ").append(firstLatency).append(" ms\nLatest check: ").append(lastLatency).append(" ms\n");
        if (count > 1) out.append(String.format(Locale.US, "Average after first: %.1f ms (%d checks)", laterTotal / (double)(count - 1), count - 1));
        else out.append("Average after first: waiting for another check");
        return out.toString();
    }
}
