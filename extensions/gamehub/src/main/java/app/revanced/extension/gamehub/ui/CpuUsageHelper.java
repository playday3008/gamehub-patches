package app.revanced.extension.gamehub.ui;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import app.revanced.extension.gamehub.prefs.GameHubPrefs;
import app.revanced.extension.gamehub.util.GHLog;

/**
 * Uses {@link android.os.Process#getElapsedCpuTime()} to compute app CPU usage
 * percentage and updates a TextView injected as a sibling of the battery icon
 * ImageView by the resource patch.
 *
 * <p>{@code /proc/stat} is blocked by SELinux on Android 8+, so we use the SDK
 * API instead. This gives the app's own CPU usage (across all cores), which is
 * more relevant for a game streaming app than system-wide usage.
 *
 * <p>BatteryUtil.a() fires infrequently (on battery status changes), so we
 * start a self-refreshing Handler loop on the first call to update every second.
 */
@SuppressWarnings("unused")
public final class CpuUsageHelper {

    private static final long REFRESH_INTERVAL_MS = 1000;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static WeakReference<TextView> cpuTextViewRef;
    private static boolean refreshLoopRunning = false;

    // Previous snapshot for delta computation.
    private static long prevCpuTime = -1;
    private static long prevWallTime = -1;

    /**
     * Called from BatteryUtil.a(Context, ImageView) after battery level injection.
     * Locates the tv_cpu_percent TextView and starts a 1-second refresh loop.
     *
     * @param batteryImageView the battery icon ImageView
     */
    public static void updateCpuText(ImageView batteryImageView) {
        try {
            if (batteryImageView == null) return;

            ViewGroup parent = (ViewGroup) batteryImageView.getParent();
            if (parent == null) return;

            int tvId = batteryImageView.getResources().getIdentifier(
                    "tv_cpu_percent", "id", batteryImageView.getContext().getPackageName());
            if (tvId == 0) return;

            View tv = parent.findViewById(tvId);
            if (!(tv instanceof TextView)) return;

            cpuTextViewRef = new WeakReference<>((TextView) tv);
            refreshCpuText();

            if (!refreshLoopRunning) {
                refreshLoopRunning = true;
                scheduleRefresh();
            }
        } catch (Exception e) {
            GHLog.CPU.w("updateCpuText failed", e);
        }
    }

    private static final Runnable refreshRunnable = () -> {
        refreshCpuText();
        scheduleRefresh();
    };

    private static void scheduleRefresh() {
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    private static void refreshCpuText() {
        try {
            WeakReference<TextView> ref = cpuTextViewRef;
            if (ref == null) return;
            TextView cpuTextView = ref.get();
            if (cpuTextView == null) {
                // View was GC'd (activity destroyed) — stop the loop.
                refreshLoopRunning = false;
                handler.removeCallbacks(refreshRunnable);
                return;
            }

            if (!GameHubPrefs.isCpuUsageEnabled()) {
                cpuTextView.setVisibility(View.GONE);
                return;
            }

            int cpuPercent = readCpuUsage();
            if (cpuPercent < 0) {
                cpuTextView.setVisibility(View.GONE);
                return;
            }

            cpuTextView.setText(String.format("CPU: %3d%%", cpuPercent));
            cpuTextView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            GHLog.CPU.w("refreshCpuText failed", e);
        }
    }

    /**
     * Computes app CPU usage as a percentage delta since the last call using
     * {@link android.os.Process#getElapsedCpuTime()} (ms of CPU time consumed
     * by this process across all cores) and {@link SystemClock#elapsedRealtime()}
     * (wall clock ms since boot). Normalized by available processor count so the
     * result stays in the 0–100 range on multi-core devices.
     *
     * @return CPU usage percentage (0–100), or -1 on the first call (no baseline).
     */
    private static int readCpuUsage() {
        long cpuTime = android.os.Process.getElapsedCpuTime();
        long wallTime = SystemClock.elapsedRealtime();

        if (prevCpuTime < 0) {
            prevCpuTime = cpuTime;
            prevWallTime = wallTime;
            return -1;
        }

        long cpuDelta = cpuTime - prevCpuTime;
        long wallDelta = wallTime - prevWallTime;

        prevCpuTime = cpuTime;
        prevWallTime = wallTime;

        if (wallDelta <= 0) return 0;

        int cores = Runtime.getRuntime().availableProcessors();
        int percent = (int) (cpuDelta * 100 / (wallDelta * cores));
        return Math.min(percent, 100);
    }
}
