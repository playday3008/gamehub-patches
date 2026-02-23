package app.revanced.patches.gamehub.ui.statusbar

import app.revanced.patcher.fingerprint

/**
 * Matches BatteryUtil.a(Context, ImageView)V — the same method as
 * batteryUtilFingerprint, but a separate fingerprint instance because
 * each patch must resolve its own fingerprint independently.
 */
internal val cpuUtilFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/common/utils/BatteryUtil;" &&
            method.name == "a" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == "Landroid/content/Context;" &&
            method.parameterTypes[1] == "Landroid/widget/ImageView;"
    }
}
