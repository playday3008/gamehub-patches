package app.revanced.extension.gamehub.prefs;

import android.content.SharedPreferences;
import android.os.Environment;

import app.revanced.extension.gamehub.token.TokenProvider;
import app.revanced.extension.gamehub.util.GHLog;
import com.blankj.utilcode.util.Utils;

@SuppressWarnings("unused")
public class GameHubPrefs {

    // Settings content-type constants (must match values used in patch injection).
    public static final int CONTENT_TYPE_SD_CARD_STORAGE = 0x18;
    public static final int CONTENT_TYPE_API = 0x1a;

    private static final String PREFS_NAME = "steam_storage_pref";
    private static final String KEY_EXTERNAL_API = "use_external_api";
    private static final String KEY_CUSTOM_STORAGE = "use_custom_storage";
    private static final String KEY_STORAGE_PATH = "steam_storage_path";
    private static final String KEY_LAST_API_SOURCE = "last_api_source";

    private static volatile boolean startupCheckDone = false;

    private static SharedPreferences getPrefs() {
        return Utils.a().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
    }

    public static boolean isExternalAPI() {
        return getPrefs().getBoolean(KEY_EXTERNAL_API, true);
    }

    public static void toggleAPI() {
        SharedPreferences prefs = getPrefs();
        prefs.edit().putBoolean(KEY_EXTERNAL_API, !prefs.getBoolean(KEY_EXTERNAL_API, true)).apply();
    }

    /**
     * Clears cached component data and tokens so the app re-downloads from the new API source.
     * Must be called whenever the API source changes (toggle or startup mismatch detection).
     */
    private static void clearComponentAndTokenCaches() {
        try {
            android.content.Context ctx = Utils.a();
            int mode = android.content.Context.MODE_PRIVATE;
            // Clear component catalogs (populated per-API, version codes may collide across sources).
            ctx.getSharedPreferences("sp_winemu_all_components12", mode).edit().clear().apply();
            ctx.getSharedPreferences("sp_winemu_all_containers", mode).edit().clear().apply();
            ctx.getSharedPreferences("sp_winemu_all_imageFs", mode).edit().clear().apply();
            // Clear global component metadata (dxvk, vkd3d, imagefs, steam_client, general_component).
            ctx.getSharedPreferences("pc_g_setting", mode).edit().clear().apply();
            // Flush token caches (in-memory L1 + SharedPreferences L2).
            TokenProvider.clearCache();
            GHLog.PREFS.d("Clearing component and token caches for API source change");
        } catch (Exception e) {
            GHLog.PREFS.w("clearComponentAndTokenCaches failed", e);
        }
    }

    public static boolean isCustomStorageEnabled() {
        return getPrefs().getBoolean(KEY_CUSTOM_STORAGE, false);
    }

    public static void toggleStorageLocation() {
        SharedPreferences prefs = getPrefs();
        prefs.edit().putBoolean(KEY_CUSTOM_STORAGE, !prefs.getBoolean(KEY_CUSTOM_STORAGE, false)).apply();
    }

    public static String getCustomStoragePath() {
        return getPrefs().getString(KEY_STORAGE_PATH, "");
    }

    /**
     * Returns the persisted enabled state for a custom settings toggle.
     * Called from SettingItemViewModel.l() to initialise each switch's visual state.
     *
     * @param contentType the content-type constant of the toggle item
     * @return true if the toggle should be shown as ON
     */
    public static boolean isSettingEnabled(int contentType) {
        if (contentType == CONTENT_TYPE_SD_CARD_STORAGE) return isCustomStorageEnabled();
        if (contentType == CONTENT_TYPE_API) return isExternalAPI();
        return false;
    }

    /**
     * Returns the persisted switch value for our custom settings types, or defaultValue for others.
     * Called from SettingSwitchHolder.u() after CloudGameSettingDataHelper.j() returns a value,
     * overriding it for content types 0x18 and 0x1a with our SharedPreferences values.
     *
     * @param contentType  the entity's content-type
     * @param defaultValue the value returned by CloudGameSettingDataHelper for this type
     * @return the effective switch value to display
     */
    public static boolean getInitialSwitchValue(int contentType, boolean defaultValue) {
        if (contentType == CONTENT_TYPE_SD_CARD_STORAGE) return isCustomStorageEnabled();
        if (contentType == CONTENT_TYPE_API) return isExternalAPI();
        return defaultValue;
    }

    /**
     * Translates an internal path to the SD card path if custom storage is enabled.
     *
     * @param originalPath the original install path from SteamDownloadInfoHelper
     * @return the effective path (SD card or original)
     */
    public static String getEffectiveStoragePath(String originalPath) {
        if (!isCustomStorageEnabled()) return originalPath;
        if (originalPath == null || originalPath.isEmpty()) return originalPath;

        String customPath = getCustomStoragePath();
        if (customPath == null || customPath.isEmpty()) return originalPath;

        java.io.File customDir = new java.io.File(customPath);
        if (!customDir.exists() || !customDir.isDirectory()) return originalPath;

        if (originalPath.startsWith(customPath)) return originalPath;

        int steamIdx = originalPath.indexOf("/files/Steam");
        if (steamIdx < 0) return originalPath;

        return customPath + originalPath.substring(steamIdx);
    }

    private static final String EMUREADY_URL = "https://gamehub-lite-api.emuready.workers.dev/";

    /**
     * Returns the effective API URL: EmuReady when not using the official API, original otherwise.
     * On first call each app launch, checks whether the API source changed since last run
     * and clears stale component/token caches if so.
     */
    public static String getEffectiveApiUrl(String officialUrl) {
        if (!startupCheckDone) {
            startupCheckDone = true;
            try {
                SharedPreferences prefs = getPrefs();
                boolean currentSource = isExternalAPI();
                // KEY_LAST_API_SOURCE defaults to true (EmuReady) if absent, matching isExternalAPI() default.
                boolean lastSource = prefs.getBoolean(KEY_LAST_API_SOURCE, true);
                if (!prefs.contains(KEY_LAST_API_SOURCE) || currentSource != lastSource) {
                    GHLog.PREFS.d("API source mismatch on startup (current=" + currentSource
                            + ", last=" + lastSource + ") — clearing caches");
                    clearComponentAndTokenCaches();
                    prefs.edit().putBoolean(KEY_LAST_API_SOURCE, currentSource).apply();
                }
            } catch (Exception e) {
                GHLog.PREFS.w("Startup API source check failed", e);
            }
        }
        return isExternalAPI() ? EMUREADY_URL : officialUrl;
    }

    /**
     * Returns the display name for a custom settings item, or null if the content type
     * is not one of our registered items (falls through to the app's own switch).
     * Called from SettingItemEntity.getContentName() via replaceInstruction.
     */
    public static String getCustomSettingName(int contentType) {
        if (contentType == CONTENT_TYPE_SD_CARD_STORAGE) return "SD Card Storage";
        if (contentType == CONTENT_TYPE_API) return "EmuReady API";
        return null;
    }

    /**
     * Handles a settings-switch toggle for the two Steam-related content types.
     * Called from SettingSwitchHolder.w() just before CommFocusSwitchBtn.b() sets the
     * visual state. Returning false here causes the switch to stay/revert to OFF;
     * returning true confirms it goes ON.
     *
     * @param contentType   the content-type constant of the toggle item
     * @param proposedState the state the user is trying to set (true=ON, false=OFF)
     * @return the actual state to apply visually
     */
    public static boolean handleSettingToggle(int contentType, boolean proposedState) {
        if (contentType == CONTENT_TYPE_SD_CARD_STORAGE) {
            if (proposedState) {
                String path = autoDetectSDCardStorage();
                if (path == null) {
                    android.widget.Toast.makeText(
                            Utils.a(), "No SD card found", android.widget.Toast.LENGTH_SHORT
                    ).show();
                    return false; // revert visual state immediately
                }
                getPrefs().edit().putBoolean(KEY_CUSTOM_STORAGE, true).apply();
                return true;
            } else {
                useInternalStorage();
                return false;
            }
        } else if (contentType == CONTENT_TYPE_API) {
            toggleAPI();
            clearComponentAndTokenCaches();
            getPrefs().edit().putBoolean(KEY_LAST_API_SOURCE, proposedState).apply();
            String msg = proposedState
                    ? "Switched to EmuReady API — restart to refresh components"
                    : "Switched to Official API — restart to refresh components";
            android.widget.Toast.makeText(Utils.a(), msg, android.widget.Toast.LENGTH_SHORT).show();
            return proposedState;
        }
        return proposedState;
    }

    /**
     * Adds standard browser-like HTTP headers to an okhttp3.Request.Builder.
     * Needed so Cloudflare Worker endpoints accept requests from the app.
     *
     * @param builder an okhttp3.Request.Builder instance
     * @return the same builder with headers added
     */
    @SuppressWarnings("JavaReflectionMemberAccess")
    public static Object addCompatibilityHeaders(Object builder) {
        try {
            java.lang.reflect.Method addHeader = builder.getClass()
                    .getMethod("addHeader", String.class, String.class);
            addHeader.invoke(builder, "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
            addHeader.invoke(builder, "Accept", "application/json, text/plain, */*");
            addHeader.invoke(builder, "Accept-Language", "en-US,en;q=0.9");
            addHeader.invoke(builder, "Connection", "keep-alive");
        } catch (Exception e) {
            GHLog.PREFS.w("addCompatibilityHeaders failed", e);
        }
        return builder;
    }

    /**
     * Returns the number of bytes available on the effective storage location.
     * Uses the custom storage path when enabled, otherwise falls back to external storage.
     * Called from DownloadGameSizeInfoDialog to show correct free space.
     */
    public static long getAvailableStorage() {
        String path = isCustomStorageEnabled() ? getCustomStoragePath() : null;
        java.io.File dir = (path != null && !path.isEmpty())
                ? new java.io.File(path)
                : android.os.Environment.getExternalStorageDirectory();
        if (!dir.exists()) dir = android.os.Environment.getExternalStorageDirectory();
        android.os.StatFs sf = new android.os.StatFs(dir.getAbsolutePath());
        return sf.getAvailableBlocksLong() * sf.getBlockSizeLong();
    }

    /**
     * Sets the custom storage path directly (used by StorageBroadcastReceiver).
     */
    public static void setStoragePath(String path) {
        getPrefs().edit().putString(KEY_STORAGE_PATH, path).apply();
    }

    /**
     * Reverts to internal/default storage by disabling custom storage.
     */
    public static void useInternalStorage() {
        getPrefs().edit().putBoolean(KEY_CUSTOM_STORAGE, false).apply();
    }

    /**
     * Scans external storage volumes for a writable /GHL folder and saves the path.
     *
     * @return the detected storage root path, or null if none was found.
     */
    public static String autoDetectSDCardStorage() {
        try {
            android.content.Context ctx = Utils.a();
            java.io.File[] externalDirs = ctx.getExternalFilesDirs(null);
            for (java.io.File dir : externalDirs) {
                if (dir == null) continue;
                // Find the storage root (up to Android/data)
                String path = dir.getAbsolutePath();
                int androidIdx = path.indexOf("/Android/data");
                if (androidIdx < 0) continue;
                String storageRoot = path.substring(0, androidIdx);
                java.io.File ghlDir = new java.io.File(storageRoot, "GHL");
                if (ghlDir.exists() && ghlDir.isDirectory() && ghlDir.canWrite()) {
                    getPrefs().edit().putString(KEY_STORAGE_PATH, storageRoot).apply();
                    return storageRoot;
                }
            }
        } catch (Exception e) {
            GHLog.PREFS.w("autoDetectSDCardStorage failed", e);
        }
        return null;
    }
}
