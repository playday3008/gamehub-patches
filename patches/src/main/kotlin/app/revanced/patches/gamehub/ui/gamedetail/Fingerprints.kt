package app.revanced.patches.gamehub.ui.gamedetail

import app.revanced.patcher.fingerprint

/**
 * Matches GameDetailActivity.onCreate(Bundle).
 */
internal val gameDetailOnCreateFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/landscape/launcher/ui/gamedetail/GameDetailActivity;" &&
            method.name == "onCreate"
    }
}

/**
 * Matches SteamGameDataHandler.h(SteamGame, GameContext) → SteamGameEntity.
 * This method loads SimpleGameCompatibility from the GameContext compatibility map
 * and passes it to the various entity builders (e/d/f/g).
 */
internal val steamGameDataHandlerFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/game/ui/vm/handler/SteamGameDataHandler;" &&
            method.name == "h" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == "Lcom/xj/common/bean/SteamGame;" &&
            method.parameterTypes[1] == "Lcom/xj/game/ui/vm/handler/SteamGameDataHandler${'$'}GameContext;"
    }
}

/**
 * Matches GameDetailHeadViewHolder.A(GameDetailEntity, boolean) → void.
 * This method populates the game detail header including the compatibility section
 * (layoutCompatibility group, tvCompatibilityTitle, tvCompatibilityName, ivCompatibilityIcon).
 */
internal val gameDetailCompatDisplayFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/landscape/launcher/holder/GameDetailHeadViewHolder;" &&
            method.name == "A" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == "Lcom/xj/common/service/bean/GameDetailEntity;" &&
            method.parameterTypes[1] == "Z"
    }
}
