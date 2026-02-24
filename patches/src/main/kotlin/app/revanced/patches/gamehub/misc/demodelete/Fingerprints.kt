package app.revanced.patches.gamehub.misc.demodelete

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

internal val uninstallPcDemoGameFingerprint = fingerprint {
    returns("Ljava/lang/Object;")
    parameters("Ljava/lang/String;", "Lkotlin/coroutines/Continuation;")
    custom { method, classDef ->
        classDef.type == "Lcom/xj/game/UninstallGameHelper;" &&
            method.implementation?.instructions?.any { instruction ->
                instruction is ReferenceInstruction &&
                    instruction.reference.toString().contains("uninstallPcDemoGame")
            } == true
    }
}
