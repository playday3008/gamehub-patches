package app.revanced.patches.gamehub.ui.accountvalue

import app.revanced.patcher.fingerprint

/**
 * Matches SteamServiceImpl.C(SteamPicsAppPrice) → SteamGamePriceEntity.
 * This method converts PICS price data into the display entity for every game.
 */
internal val picsAppPriceConverterFingerprint = fingerprint {
    custom { method, classDef ->
        method.returnType == "Lcom/xj/common/bean/SteamGamePriceEntity;" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Lcom/xj/standalone/steam/data/db/tables/apps/SteamPicsAppPrice;"
    }
}

/**
 * Matches SteamUserInfoViewHolder.z(SteamAccount).
 * This method binds the Steam account data to the home screen user info card.
 */
internal val steamUserInfoBindFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/landscape/launcher/ui/steam/SteamUserInfoViewHolder;" &&
            method.name == "z" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Lcom/xj/common/bean/SteamAccount;"
    }
}

/**
 * Matches SteamPersonalInfoFragment.V0(fragment, Float) → Unit.
 * This static method updates the account value display in the personal info screen.
 */
internal val personalInfoAccountValueFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/winemu/ui/gamelibrary/steam/ui/SteamPersonalInfoFragment;" &&
            method.name == "V0" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[1] == "Ljava/lang/Float;"
    }
}
