package app.revanced.patches.gamehub.ui.gamedetail

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.gamehub.GAMEHUB_PACKAGE
import app.revanced.patches.gamehub.GAMEHUB_VERSION

@Suppress("unused")
val hideRecommendedGamesPatch = bytecodePatch(
    name = "Hide recommended games",
    description = "Removes the recommended games section from the game detail view.",
) {
    compatibleWith(GAMEHUB_PACKAGE(GAMEHUB_VERSION))

    execute {
        // GameDetailVM.A(GameDetailEntity) builds the CardLineData that feeds the
        // recommendation adapter. Returning null makes the ConcatAdapter skip it.
        gameDetailRecommendFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """,
        )
    }
}
