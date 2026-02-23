package app.revanced.patches.gamehub.misc.heartbeat

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.gamehub.GAMEHUB_PACKAGE
import app.revanced.patches.gamehub.GAMEHUB_VERSION
import app.revanced.util.returnEarly

@Suppress("unused")
val disableHeartbeatPatch = bytecodePatch(
    name = "Disable heartbeat",
    description = "Disables game usage heartbeat requests sent to the server " +
        "(heartbeat/game/start, update, end).",
) {
    compatibleWith(GAMEHUB_PACKAGE(GAMEHUB_VERSION))

    execute {
        // WineGameUsageTracker periodic heartbeats.
        startHeartbeatTimeFingerprint.method.returnEarly()
        updateHeartbeatTimeFingerprint.method.returnEarly()
        endHeartbeatTimeFingerprint.method.returnEarly()

        // SteamGameByPcEmuLaunchStrategy.checkCanStartSteamGame — server permission check
        // that also hits heartbeat/game/start. Return Pair(true, "") to always allow launch.
        checkCanStartSteamGameFingerprint.method.addInstructions(
            0,
            """
                new-instance v0, Lkotlin/Pair;
                sget-object v1, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                const-string v2, ""
                invoke-direct {v0, v1, v2}, Lkotlin/Pair;-><init>(Ljava/lang/Object;Ljava/lang/Object;)V
                return-object v0
            """,
        )
    }
}
