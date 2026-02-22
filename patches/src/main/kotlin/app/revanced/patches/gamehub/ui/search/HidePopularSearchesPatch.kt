package app.revanced.patches.gamehub.ui.search

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.gamehub.GAMEHUB_PACKAGE
import app.revanced.patches.gamehub.GAMEHUB_VERSION

@Suppress("unused")
val hidePopularSearchesPatch = bytecodePatch(
    name = "Hide popular searches",
    description = "Removes the popular game recommendations from the search view.",
) {
    compatibleWith(GAMEHUB_PACKAGE(GAMEHUB_VERSION))

    execute {
        getSearchRecommendFingerprint.method.addInstructions(
            0,
            """
                new-instance v0, Lcom/xj/landscape/launcher/data/model/entity/SearchEntity;
                invoke-direct {v0}, Ljava/lang/Object;-><init>()V
                invoke-static {}, Lkotlin/collections/CollectionsKt;->p()Ljava/util/List;
                move-result-object p0
                iput-object p0, v0, Lcom/xj/landscape/launcher/data/model/entity/SearchEntity;->classGroup:Ljava/util/List;
                return-object v0
            """,
        )
    }
}
