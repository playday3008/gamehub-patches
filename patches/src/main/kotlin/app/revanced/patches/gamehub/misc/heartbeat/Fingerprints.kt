package app.revanced.patches.gamehub.misc.heartbeat

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

internal val startHeartbeatTimeFingerprint = fingerprint {
    returns("V")
    parameters()
    custom { method, classDef ->
        classDef.type == "Lcom/xj/winemu/utils/WineGameUsageTracker;" &&
            method.implementation?.instructions?.any { instruction ->
                instruction is ReferenceInstruction &&
                    instruction.reference.toString().contains("startHeartbeatTime")
            } == true
    }
}

internal val updateHeartbeatTimeFingerprint = fingerprint {
    returns("V")
    parameters()
    custom { method, classDef ->
        classDef.type == "Lcom/xj/winemu/utils/WineGameUsageTracker;" &&
            method.implementation?.instructions?.any { instruction ->
                instruction is ReferenceInstruction &&
                    instruction.reference.toString().contains("updateHeartbeatTime")
            } == true
    }
}

internal val endHeartbeatTimeFingerprint = fingerprint {
    returns("V")
    parameters()
    custom { method, classDef ->
        classDef.type == "Lcom/xj/winemu/utils/WineGameUsageTracker;" &&
            method.implementation?.instructions?.any { instruction ->
                instruction is ReferenceInstruction &&
                    instruction.reference.toString().contains("endHeartbeatTime")
            } == true
    }
}

internal val checkCanStartSteamGameFingerprint = fingerprint {
    returns("Ljava/lang/Object;")
    parameters("Ljava/lang/String;", "Lkotlin/coroutines/Continuation;")
    custom { method, classDef ->
        classDef.type == "Lcom/xj/landscape/launcher/launcher/strategy/SteamGameByPcEmuLaunchStrategy;" &&
            method.implementation?.instructions?.any { instruction ->
                instruction is ReferenceInstruction &&
                    instruction.reference.toString().contains("checkCanStartSteamGame")
            } == true
    }
}
