package app.revanced.extension.gamehub.util;

import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/**
 * Shared device metrics readers for CPU, GPU, and RAM usage.
 *
 * <p>CPU usage is computed via {@link android.os.Process#getElapsedCpuTime()} deltas
 * (app-level, not system-wide) and cached for 500ms so multiple callers running
 * concurrent refresh loops don't corrupt each other's baselines.
 *
 * <p>GPU usage is read from sysfs (Qualcomm Adreno or ARM Mali) and returns -1 when
 * the kernel doesn't expose a utilisation node.
 *
 * <p>RAM usage uses {@link ActivityManager.MemoryInfo} for system-wide totals.
 */
public final class DeviceMetrics {

    private DeviceMetrics() {}

    // --- CPU (delta-based with 500ms cache) ---

    private static long prevCpuTime = -1;
    private static long prevWallTime = -1;
    private static int cachedCpuPercent = -1;
    private static long cachedCpuTimestamp = 0;
    private static final long CPU_CACHE_MS = 500;

    /**
     * Returns app CPU usage as a percentage (0-100), or -1 on the first call
     * (no baseline yet). Results are cached for 500ms so concurrent callers
     * share the same reading.
     */
    public static int readCpuUsage() {
        long now = SystemClock.elapsedRealtime();
        if (cachedCpuPercent >= 0 && (now - cachedCpuTimestamp) < CPU_CACHE_MS) {
            return cachedCpuPercent;
        }

        long cpuTime = android.os.Process.getElapsedCpuTime();

        if (prevCpuTime < 0) {
            prevCpuTime = cpuTime;
            prevWallTime = now;
            return -1;
        }

        long cpuDelta = cpuTime - prevCpuTime;
        long wallDelta = now - prevWallTime;

        prevCpuTime = cpuTime;
        prevWallTime = now;

        if (wallDelta <= 0) {
            cachedCpuPercent = 0;
        } else {
            int cores = Runtime.getRuntime().availableProcessors();
            cachedCpuPercent = Math.min((int) (cpuDelta * 100 / (wallDelta * cores)), 100);
        }
        cachedCpuTimestamp = now;
        return cachedCpuPercent;
    }

    // --- GPU (sysfs-based, path resolved once) ---

    private static String gpuSysfsPath;
    private static boolean gpuPathResolved = false;

    /**
     * Returns GPU usage as a percentage (0-100), or -1 if the kernel doesn't
     * expose a GPU utilisation node.
     */
    public static int readGpuUsage() {
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
        } catch (Exception ignored) {
        }

        GHLog.PERF.d("GPU sysfs: not available");
        return null;
    }

    // --- RAM ---

    /**
     * Returns a formatted RAM usage string like "RAM: 3.2 / 5.8 GB (55%)",
     * or "RAM: N/A" on failure.
     */
    public static String readRamUsage(Context ctx) {
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
