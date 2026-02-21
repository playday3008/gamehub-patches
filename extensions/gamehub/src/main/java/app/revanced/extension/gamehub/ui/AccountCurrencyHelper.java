package app.revanced.extension.gamehub.ui;

import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Replaces the hardcoded "Account (¥)" label with the real currency code
 * from the Steam PICS database (e.g. "Account (USD)").
 */
public final class AccountCurrencyHelper {
    private static String sCurrency = null;
    private static final List<WeakReference<TextView>> sPendingLabels = new ArrayList<>();

    /**
     * Called from SteamServiceImpl.C() for every game price conversion.
     * Captures the currency code from the PICS price data, and retroactively
     * updates any labels that were rendered before the currency was known.
     */
    public static void setCurrency(String currency) {
        if (currency == null || currency.isEmpty()) return;
        sCurrency = currency;

        Iterator<WeakReference<TextView>> it = sPendingLabels.iterator();
        while (it.hasNext()) {
            TextView tv = it.next().get();
            it.remove();
            if (tv != null) {
                String label = "Account (" + currency + ")";
                tv.post(() -> tv.setText(label));
            }
        }
    }

    /**
     * Called from display code — updates the account value title TextView
     * to show the real currency instead of hardcoded ¥. If the currency
     * isn't known yet, stores a weak reference for deferred update.
     */
    public static void updateLabel(TextView labelView) {
        if (labelView == null) return;
        if (sCurrency != null) {
            labelView.setText("Account (" + sCurrency + ")");
        } else {
            sPendingLabels.add(new WeakReference<>(labelView));
        }
    }
}
