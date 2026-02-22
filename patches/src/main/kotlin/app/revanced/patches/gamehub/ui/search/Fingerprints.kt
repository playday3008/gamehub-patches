package app.revanced.patches.gamehub.ui.search

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

internal val getHotTrendingKeywordsFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/landscape/launcher/data/repository/SearchGameRepositoryV4;" &&
            method.implementation?.instructions?.any { instruction ->
                instruction is ReferenceInstruction &&
                    instruction.reference.toString().contains("getHotTrendingKeywords")
            } == true
    }
}

internal val getSearchRecommendFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/landscape/launcher/data/repository/SearchGameRepositoryV4;" &&
            method.implementation?.instructions?.any { instruction ->
                instruction is ReferenceInstruction &&
                    instruction.reference.toString().contains("getSearchRecommend")
            } == true
    }
}
