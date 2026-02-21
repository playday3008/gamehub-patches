package app.revanced.patches.gamehub.ui.search

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val hideHotSearchesPatch = bytecodePatch(
    name = "Hide hot searches",
    description = "Removes the hot searches section from the search view.",
) {
    compatibleWith("com.xiaoji.egggame"("5.3.5"))

    execute {
        getHotTrendingKeywordsFingerprint.method.addInstructions(
            0,
            """
                new-instance v0, Ljava/util/ArrayList;
                invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
                return-object v0
            """,
        )
    }
}
