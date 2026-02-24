package app.revanced.patches.gamehub.misc.ota

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import app.revanced.patches.gamehub.GAMEHUB_PACKAGE
import app.revanced.patches.gamehub.GAMEHUB_VERSION
import app.revanced.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private val otaCleanupResourcePatch = resourcePatch {
    apply {
        val libDir = get("lib")
        if (libDir.exists() && libDir.isDirectory) {
            libDir.listFiles()?.forEach { archDir ->
                if (archDir.isDirectory) {
                    listOf("libJieLiUsbOta.so", "libjl_ota_auth.so").forEach { lib ->
                        if (archDir.resolve(lib).exists()) delete("lib/${archDir.name}/$lib")
                    }
                }
            }
        }
    }
}

@Suppress("unused")
val disableOtaUpdatesPatch = bytecodePatch(
    name = "Disable OTA updates",
    description = "Blocks OTA update server URL.",
) {
    compatibleWith(GAMEHUB_PACKAGE(GAMEHUB_VERSION))

    dependsOn(otaCleanupResourcePatch)

    apply {
        firstMethod("https://www.xiaoji.com/firmware/update/x1/").apply {
            val urlIndex = indexOfFirstInstructionOrThrow {
                (this as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)
                    ?.reference?.let { it is StringReference && it.string.startsWith("https://www.xiaoji.com") } == true
            }
            // Override the URL string with empty string so OTA calls fail silently
            addInstruction(urlIndex + 1, "const-string p1, \"http://127.0.0.1\"")
        }
    }
}
