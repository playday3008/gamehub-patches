package app.revanced.patches.gamehub.ui.overlay

import app.revanced.patcher.fingerprint

/**
 * Matches SidebarPerformanceFragment.m0(Bundle)V — the initView method that
 * sets up all Performance tab UI elements (HUD toggles, sliders, etc.).
 */
internal val sidebarPerformanceFragmentFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/winemu/sidebar/SidebarPerformanceFragment;" &&
            method.name == "m0" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Landroid/os/Bundle;"
    }
}
