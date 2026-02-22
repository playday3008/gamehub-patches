package app.revanced.extension.gamehub.token;

import android.content.SharedPreferences;
import android.util.Log;

import com.blankj.utilcode.util.Utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides dynamic token resolution based on which patches are applied.
 *
 * <p>Decision tree:
 * <ul>
 *   <li>API Switch patched AND EmuReady selected → {@code "fake-token"}</li>
 *   <li>Login bypassed → token from refresh service (3-tier cache)</li>
 *   <li>Otherwise → original token (pass-through)</li>
 * </ul>
 *
 * <p>Flags are set at class-init time by bytecode injection from the respective patches.
 */
@SuppressWarnings("unused")
public class TokenProvider {

    /** Set to {@code true} by API Server Switch patch via {@code <clinit>} injection. */
    public static boolean apiSwitchPatched = false;

    /** Set to {@code true} by Bypass Login patch via {@code <clinit>} injection. */
    public static boolean loginBypassed = false;

    private static final String TOKEN_SERVICE_URL =
            "https://gamehub-lite-token-refresher.emuready.workers.dev/token";
    private static final String AUTH_HEADER_VALUE = "gamehub-internal-token-fetch-2025";
    private static final long CACHE_TTL_MS = 4 * 60 * 60 * 1000L; // 4 hours

    private static final String PREFS_NAME = "token_provider_pref";
    private static final String KEY_CACHED_TOKEN = "cached_token";
    private static final String KEY_CACHED_TOKEN_EXPIRY = "cached_token_expiry";

    // L1 cache: in-memory, lock-free via AtomicReference.
    private static final AtomicReference<CachedToken> l1Cache = new AtomicReference<>(null);

    private static final class CachedToken {
        final String token;
        final long expiryMs;

        CachedToken(String token, long expiryMs) {
            this.token = token;
            this.expiryMs = expiryMs;
        }

        boolean isValid() {
            return token != null && !token.isEmpty() && System.currentTimeMillis() < expiryMs;
        }
    }

    /**
     * Called from the patched {@code UserManager.getToken()} return path.
     *
     * @param originalToken the token that the original method would have returned
     * @return the effective token based on the active patch combination
     */
    private static final String TAG = "TokenProvider";

    public static String resolveToken(String originalToken) {
        Log.d(TAG, "resolveToken: apiSwitchPatched=" + apiSwitchPatched
                + " loginBypassed=" + loginBypassed
                + " isExternalAPI=" + isExternalAPI()
                + " originalToken=" + (originalToken == null ? "null" : originalToken.length() + " chars"));

        // EmuReady API accepts any token — use a lightweight fake.
        if (apiSwitchPatched && isExternalAPI()) {
            Log.d(TAG, "resolveToken → fake-token (EmuReady path)");
            return "fake-token";
        }

        // Login is bypassed but we're talking to the Original API — need a real token.
        if (loginBypassed) {
            String token = getServiceToken(originalToken);
            Log.d(TAG, "resolveToken → service token: " + (token == null ? "null" : token.length() + " chars"));
            return token;
        }

        // No login bypass — the user is genuinely logged in; pass through.
        Log.d(TAG, "resolveToken → original pass-through");
        return originalToken;
    }

    /**
     * Reads the EmuReady-API toggle directly from SharedPreferences,
     * duplicating the key from {@code GameHubPrefs} to avoid a class dependency
     * that could cause issues during early init.
     */
    private static boolean isExternalAPI() {
        try {
            SharedPreferences prefs = Utils.a()
                    .getSharedPreferences("steam_storage_pref", android.content.Context.MODE_PRIVATE);
            return prefs.getBoolean("use_external_api", true);
        } catch (Exception e) {
            return true;
        }
    }

    private static SharedPreferences getTokenPrefs() {
        return Utils.a().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
    }

    /**
     * 3-tier token resolution: L1 (memory) → L2 (SharedPreferences) → L3 (HTTP).
     * Falls back to stale cache → original token → {@code "fake-token"}.
     */
    private static String getServiceToken(String fallbackToken) {
        // L1: in-memory AtomicReference cache.
        CachedToken l1 = l1Cache.get();
        if (l1 != null && l1.isValid()) {
            Log.d(TAG, "getServiceToken → L1 hit");
            return l1.token;
        }
        Log.d(TAG, "getServiceToken: L1 miss");

        // L2: SharedPreferences persistent cache (survives app restarts).
        try {
            SharedPreferences prefs = getTokenPrefs();
            String cachedToken = prefs.getString(KEY_CACHED_TOKEN, null);
            long expiry = prefs.getLong(KEY_CACHED_TOKEN_EXPIRY, 0);
            long remaining = expiry - System.currentTimeMillis();
            Log.d(TAG, "getServiceToken: L2 cached=" + (cachedToken != null) + " remaining=" + remaining + "ms");
            if (cachedToken != null && !cachedToken.isEmpty() && remaining > 0) {
                l1Cache.set(new CachedToken(cachedToken, expiry));
                Log.d(TAG, "getServiceToken → L2 hit");
                return cachedToken;
            }
        } catch (Exception e) {
            Log.w(TAG, "getServiceToken: L2 error", e);
        }

        // L3: HTTP fetch from token-refresh service.
        try {
            Log.d(TAG, "getServiceToken: L3 fetching from " + TOKEN_SERVICE_URL);
            String freshToken = fetchTokenFromService();
            Log.d(TAG, "getServiceToken: L3 result=" + (freshToken == null ? "null" : freshToken.length() + " chars"));
            if (freshToken != null && !freshToken.isEmpty()) {
                long expiry = System.currentTimeMillis() + CACHE_TTL_MS;
                l1Cache.set(new CachedToken(freshToken, expiry));
                try {
                    getTokenPrefs().edit()
                            .putString(KEY_CACHED_TOKEN, freshToken)
                            .putLong(KEY_CACHED_TOKEN_EXPIRY, expiry)
                            .apply();
                } catch (Exception ignored) {
                }
                Log.d(TAG, "getServiceToken → L3 hit");
                return freshToken;
            }
        } catch (Exception e) {
            Log.w(TAG, "getServiceToken: L3 fetch failed", e);
        }

        // Fallback chain: stale L1 → stale L2 → original token → fake-token.
        CachedToken stale = l1Cache.get();
        if (stale != null && stale.token != null && !stale.token.isEmpty()) {
            Log.d(TAG, "getServiceToken → stale L1");
            return stale.token;
        }
        try {
            String stalePref = getTokenPrefs().getString(KEY_CACHED_TOKEN, null);
            if (stalePref != null && !stalePref.isEmpty()) {
                Log.d(TAG, "getServiceToken → stale L2");
                return stalePref;
            }
        } catch (Exception ignored) {
        }
        if (fallbackToken != null && !fallbackToken.isEmpty()) {
            Log.d(TAG, "getServiceToken → fallbackToken");
            return fallbackToken;
        }
        Log.w(TAG, "getServiceToken → last-resort fake-token");
        return "fake-token";
    }

    /**
     * Fetches a fresh token from the token-refresh Cloudflare Worker.
     * Uses {@link HttpURLConnection} (not OkHttp) to avoid circular dependency —
     * {@code getToken()} is called from within the OkHttp interceptor chain.
     */
    private static String fetchTokenFromService() throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(TOKEN_SERVICE_URL).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Worker-Auth", AUTH_HEADER_VALUE);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            int code = conn.getResponseCode();
            Log.d(TAG, "fetchTokenFromService: HTTP " + code);
            if (code != 200) return null;

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            String body = sb.toString();
            Log.d(TAG, "fetchTokenFromService: body=" + body.substring(0, Math.min(body.length(), 200)));
            return parseTokenFromJson(body);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Minimal JSON parser — extracts the {@code "token"} value from a JSON object
     * like {@code {"token":"abc123"}} without requiring Gson.
     */
    private static String parseTokenFromJson(String json) {
        if (json == null || json.isEmpty()) return null;

        int keyIdx = json.indexOf("\"token\"");
        if (keyIdx < 0) return null;

        int colonIdx = json.indexOf(':', keyIdx + 7);
        if (colonIdx < 0) return null;

        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return null;

        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) return null;

        return json.substring(startQuote + 1, endQuote);
    }
}
