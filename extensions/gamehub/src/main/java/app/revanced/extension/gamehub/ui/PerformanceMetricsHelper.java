package app.revanced.extension.gamehub.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.ref.WeakReference;
import java.util.Locale;

import app.revanced.extension.gamehub.prefs.GameHubPrefs;
import app.revanced.extension.gamehub.util.GHLog;

/**
 * Adds CPU, GPU, and RAM usage metrics to the Performance tab of the
 * game overlay sidebar. Views are programmatically appended to the
 * performanceFl LinearLayout and refreshed every second via a Handler loop.
 */
@SuppressWarnings("unused")
public final class PerformanceMetricsHelper {

    private static final long REFRESH_INTERVAL_MS = 1000;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static WeakReference<TextView> cpuTextRef;
    private static WeakReference<TextView> gpuTextRef;
    private static WeakReference<TextView> ramTextRef;
    private static boolean refreshLoopRunning = false;

    // CPU delta tracking.
    private static long prevCpuTime = -1;
    private static long prevWallTime = -1;

    // GPU sysfs path (resolved once, cached).
    private static String gpuSysfsPath;
    private static boolean gpuPathResolved = false;

    /**
     * Called from the injected smali in SidebarPerformanceFragment.m0().
     *
     * @param performanceFl the FocusableLinearLayout containing Performance tab views
     */
    public static void initMetrics(ViewGroup performanceFl) {
        try {
            if (performanceFl == null) return;
            if (!GameHubPrefs.isPerfMetricsEnabled()) return;

            Context ctx = performanceFl.getContext();

            // Add section header.
            TextView header = new TextView(ctx);
            header.setText("Performance Metrics");
            header.setTextColor(0xFFFFFFFF);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            header.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            headerParams.topMargin = dpToPx(ctx, 16);
            headerParams.bottomMargin = dpToPx(ctx, 8);
            headerParams.leftMargin = dpToPx(ctx, 16);
            header.setLayoutParams(headerParams);
            performanceFl.addView(header);

            // Create metric TextViews.
            TextView cpuTv = createMetricTextView(ctx);
            TextView gpuTv = createMetricTextView(ctx);
            TextView ramTv = createMetricTextView(ctx);

            cpuTv.setText("CPU: --");
            gpuTv.setText("GPU: --");
            ramTv.setText("RAM: --");

            performanceFl.addView(cpuTv);
            performanceFl.addView(gpuTv);
            performanceFl.addView(ramTv);

            cpuTextRef = new WeakReference<>(cpuTv);
            gpuTextRef = new WeakReference<>(gpuTv);
            ramTextRef = new WeakReference<>(ramTv);

            // Reset CPU baseline so the first real reading comes after 1 second.
            prevCpuTime = -1;
            prevWallTime = -1;

            if (!refreshLoopRunning) {
                refreshLoopRunning = true;
                scheduleRefresh();
            }

            GHLog.PERF.d("Performance metrics initialized");
        } catch (Exception e) {
            GHLog.PERF.w("initMetrics failed", e);
        }
    }

    private static TextView createMetricTextView(Context ctx) {
        TextView tv = new TextView(ctx);
        tv.setTextColor(0xB3FFFFFF); // 70% white
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dpToPx(ctx, 16);
        params.topMargin = dpToPx(ctx, 2);
        tv.setLayoutParams(params);
        return tv;
    }

    private static int dpToPx(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics());
    }

    // --- Refresh loop ---

    private static final Runnable refreshRunnable = () -> {
        refreshMetrics();
        scheduleRefresh();
    };

    private static void scheduleRefresh() {
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    private static void refreshMetrics() {
        try {
            TextView cpuTv = deref(cpuTextRef);
            TextView gpuTv = deref(gpuTextRef);
            TextView ramTv = deref(ramTextRef);

            if (cpuTv == null && gpuTv == null && ramTv == null) {
                // All views GC'd — stop the loop.
                refreshLoopRunning = false;
                handler.removeCallbacks(refreshRunnable);
                return;
            }

            if (!GameHubPrefs.isPerfMetricsEnabled()) return;

            if (cpuTv != null) {
                int cpu = readCpuUsage();
                cpuTv.setText(cpu >= 0 ? String.format(Locale.US, "CPU: %d%%", cpu) : "CPU: --");
            }

            if (gpuTv != null) {
                int gpu = readGpuUsage();
                gpuTv.setText(gpu >= 0 ? String.format(Locale.US, "GPU: %d%%", gpu) : "GPU: N/A");
            }

            if (ramTv != null) {
                ramTv.setText(readRamUsage(ramTv.getContext()));
            }
        } catch (Exception e) {
            GHLog.PERF.w("refreshMetrics failed", e);
        }
    }

    private static TextView deref(WeakReference<TextView> ref) {
        return ref != null ? ref.get() : null;
    }

    // --- CPU ---

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

    // --- GPU ---

    private static int readGpuUsage() {
        try {
            String path = resolveGpuSysfsPath();
            if (path == null) return -1;

            BufferedReader reader = new BufferedReader(new FileReader(path));
            String line = reader.readLine();
            reader.close();

            if (line == null) return -1;

            // Qualcomm: "busy total" format (e.g., "1234567 9876543").
            if (path.contains("kgsl")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    long busy = Long.parseLong(parts[0]);
                    long total = Long.parseLong(parts[1]);
                    if (total > 0) {
                        return (int) (busy * 100 / total);
                    }
                }
                return -1;
            }

            // Mali: single percentage value or "0.00" float.
            line = line.trim();
            if (line.contains(".")) {
                return (int) Float.parseFloat(line);
            }
            return Integer.parseInt(line);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String resolveGpuSysfsPath() {
        if (gpuPathResolved) return gpuSysfsPath;
        gpuPathResolved = true;

        // Qualcomm Adreno.
        String qualcomm = "/sys/class/kgsl/kgsl-3d0/gpubusy";
        if (new File(qualcomm).canRead()) {
            gpuSysfsPath = qualcomm;
            GHLog.PERF.d("GPU sysfs: Qualcomm (kgsl)");
            return gpuSysfsPath;
        }

        // Mali: search platform devices.
        try {
            File platform = new File("/sys/devices/platform");
            File[] children = platform.listFiles();
            if (children != null) {
                for (File child : children) {
                    File util = new File(child, "gpu/utilisation");
                    if (util.canRead()) {
                        gpuSysfsPath = util.getAbsolutePath();
                        GHLog.PERF.d("GPU sysfs: Mali (" + gpuSysfsPath + ")");
                        return gpuSysfsPath;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        GHLog.PERF.d("GPU sysfs: not available");
        return null;
    }

    // --- RAM ---

    private static String readRamUsage(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return "RAM: N/A";

            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);

            double totalGb = mi.totalMem / (1024.0 * 1024.0 * 1024.0);
            double availGb = mi.availMem / (1024.0 * 1024.0 * 1024.0);
            double usedGb = totalGb - availGb;
            int percent = (int) (usedGb * 100 / totalGb);

            return String.format(Locale.US, "RAM: %.1f / %.1f GB (%d%%)", usedGb, totalGb, percent);
        } catch (Exception e) {
            return "RAM: N/A";
        }
    }
}
