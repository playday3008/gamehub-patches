package app.revanced.patches.gamehub.ui.search

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.gamehub.GAMEHUB_PACKAGE
import app.revanced.patches.gamehub.GAMEHUB_VERSION
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

@Suppress("unused")
val hideHotSearchesPatch = bytecodePatch(
    name = "Hide hot searches",
    description = "Removes the hot searches section from the search view.",
) {
    compatibleWith(GAMEHUB_PACKAGE(GAMEHUB_VERSION))

    apply {
        firstMethod {
            definingClass == "Lcom/xj/landscape/launcher/data/repository/SearchGameRepositoryV4;" &&
                implementation?.instructions?.any { instruction ->
                    instruction is ReferenceInstruction &&
                        instruction.reference.toString().contains("getHotTrendingKeywords")
                } == true
        }.addInstructions(
            0,
            """
                new-instance v0, Ljava/util/ArrayList;
                invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
                return-object v0
            """,
        )
    }
}
