package app.revanced.patches.gamehub.ui.gamedetail

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.gamehub.misc.extension.sharedGamehubExtensionPatch
import app.revanced.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val EXTENSION_CLASS =
    "Lapp/revanced/extension/gamehub/ui/CompatibilityCache;"

@Suppress("unused")
val compatibilityFallbackPatch = bytecodePatch(
    name = "Compatibility fallback",
    description = "Shows cached compatibility data in game details when the API fails.",
) {
    compatibleWith("com.xiaoji.egggame"("5.3.5"))
    dependsOn(sharedGamehubExtensionPatch)

    execute {
        // ── 1. Cache SimpleGameCompatibility during game list loading ──
        //
        // In SteamGameDataHandler.h(), after the compatibility map lookup:
        //   invoke-virtual {p2}, GameContext;->a()Ljava/util/Map;
        //   move-result-object v0
        //   invoke-interface {v0, v4}, Map;->get(Object)Object;
        //   move-result-object v0
        //   move-object v5, v0
        //   check-cast v5, SimpleGameCompatibility;       ← target
        //
        // After the check-cast, v4 = steamAppId (String), v5 = SimpleGameCompatibility|null.
        steamGameDataHandlerFingerprint.method.apply {
            val checkCastIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.CHECK_CAST &&
                    (this as? ReferenceInstruction)?.reference?.toString() ==
                    "Lcom/xj/common/service/bean/SimpleGameCompatibility;"
            }

            addInstruction(
                checkCastIndex + 1,
                "invoke-static {v4, v5}, $EXTENSION_CLASS->cache(Ljava/lang/String;Ljava/lang/Object;)V",
            )
        }

        // ── 2. Inject fallback in the game detail view ──
        //
        // GameDetailHeadViewHolder.A(GameDetailEntity, Z) has:
        //   [X+0]  invoke-virtual {v1}, getCst_data()...         ← replace with extension call
        //   [X+1]  move-result-object v8                         ← keep
        //   [X+2]  if-eqz v8, :cond_16                          ← keep (hides compat if null)
        //   [X+3]  invoke-virtual {v1}, getCst_data()...         ← replace with check-cast
        //   [X+4]  move-result-object v8                         ← replace with nop
        //   [X+5]  if-eqz v8, :cond_17                          ← keep
        //   [X+6]  invoke-virtual/range {p0..p1}, R(entity)Z     ← replace with const/4 v11, 1
        //   [X+7]  move-result v11                               ← replace with nop
        gameDetailCompatDisplayFingerprint.method.apply {
            // Find the first getCst_data() call.
            val firstCstDataIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_VIRTUAL &&
                    (this as? ReferenceInstruction)?.reference?.toString() ==
                    "Lcom/xj/common/service/bean/GameDetailEntity;->getCst_data()Lcom/xj/common/service/bean/GameCompatibilityParams;"
            }

            // Replace first getCst_data() with the extension fallback method.
            replaceInstruction(
                firstCstDataIndex,
                "invoke-static {v1}, $EXTENSION_CLASS->getOrBuildCompat(Ljava/lang/Object;)Ljava/lang/Object;",
            )

            // Find the second getCst_data() call (Kotlin smart-cast pattern).
            val secondCstDataIndex = indexOfFirstInstructionOrThrow(firstCstDataIndex + 1) {
                opcode == Opcode.INVOKE_VIRTUAL &&
                    (this as? ReferenceInstruction)?.reference?.toString() ==
                    "Lcom/xj/common/service/bean/GameDetailEntity;->getCst_data()Lcom/xj/common/service/bean/GameCompatibilityParams;"
            }

            // Replace with check-cast to narrow Object → GameCompatibilityParams for the verifier.
            replaceInstruction(
                secondCstDataIndex,
                "check-cast v8, Lcom/xj/common/service/bean/GameCompatibilityParams;",
            )
            // The move-result-object after the removed invoke is dead — replace with nop.
            replaceInstruction(secondCstDataIndex + 1, "nop")

            // ── 3. Bypass the R() visibility gate ──
            //
            // R(GameDetailEntity) checks game_startup_params for PC-emulator start types.
            // When the API fails those params are empty, so R() returns false and the
            // compatibility group is set to GONE. Since we already verified non-null
            // compatibility data above, the group should always be VISIBLE.
            val rCallIndex = indexOfFirstInstructionOrThrow(secondCstDataIndex) {
                (opcode == Opcode.INVOKE_VIRTUAL || opcode == Opcode.INVOKE_VIRTUAL_RANGE) &&
                    (this as? ReferenceInstruction)?.reference?.toString() ==
                    "Lcom/xj/landscape/launcher/holder/GameDetailHeadViewHolder;->R(Lcom/xj/common/service/bean/GameDetailEntity;)Z"
            }

            // Force the result to true (v11 = 1) so the compatibility group stays VISIBLE.
            replaceInstruction(rCallIndex, "const/4 v11, 0x1")
            replaceInstruction(rCallIndex + 1, "nop")
        }
    }
}
