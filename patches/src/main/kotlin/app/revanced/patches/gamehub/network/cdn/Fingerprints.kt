package app.revanced.patches.gamehub.network.cdn

import app.revanced.patcher.fingerprint

/**
 * Matches `SteamUrlHelper.b(int, String)` in `com.xj.common.utils`.
 * This method builds header image URLs using the bigeyes CDN.
 * 5 call sites reference this class.
 */
internal val steamUrlHelperHeaderFingerprint = fingerprint {
    returns("Ljava/lang/String;")
    parameters("I", "Ljava/lang/String;")
    strings("https://cdn-library-logo-global.bigeyes.com/steam/apps/")
    custom { method, classDef ->
        classDef.type == "Lcom/xj/common/utils/SteamUrlHelper;"
    }
}

/**
 * Matches `SteamUrlHelper.e(int, String)` in `com.xj.standalone.steam.wrapper.utils`.
 * Standalone copy of the same header-URL builder.
 * 1 call site references this class.
 */
internal val standaloneUrlHelperHeaderFingerprint = fingerprint {
    returns("Ljava/lang/String;")
    parameters("I", "Ljava/lang/String;")
    strings("https://cdn-library-logo-global.bigeyes.com/steam/apps/")
    custom { method, classDef ->
        classDef.type == "Lcom/xj/standalone/steam/wrapper/utils/SteamUrlHelper;"
    }
}

/**
 * Matches `CardItemData.getCoverImagePath()` which returns the cover image URL
 * from either `game_cover_image` or `content_img` fields.
 */
internal val getCoverImagePathFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/common/service/bean/CardItemData;" &&
            method.name == "getCoverImagePath"
    }
}

/**
 * Matches `GameDetailEntity.getBack_image()` which returns the background
 * image URL for the game detail screen.
 */
internal val getBackImageFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/common/service/bean/GameDetailEntity;" &&
            method.name == "getBack_image"
    }
}

/**
 * Matches `GameDetailEntity.getCover_image()` which returns the cover
 * image URL used as fallback on the game detail screen.
 */
internal val getDetailCoverImageFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/common/service/bean/GameDetailEntity;" &&
            method.name == "getCover_image"
    }
}

/**
 * Matches `ResizeQueryParamGlideModelLoader.a(String, int, int, Options)` — the
 * `buildLoadData` implementation that only handles bigeyes.com URLs.
 */
internal val resizeLoaderBuildLoadDataFingerprint = fingerprint {
    strings("bigeyes.com")
    custom { method, classDef ->
        classDef.type == "Lcom/xj/base/sdkconfig/ResizeQueryParamGlideModelLoader;"
    }
}

/**
 * Matches `ResizeQueryParamGlideModelLoader.b(String)` — the `handles` implementation
 * that only returns true for strings starting with "http".
 */
internal val resizeLoaderHandlesFingerprint = fingerprint {
    strings("http")
    custom { method, classDef ->
        classDef.type == "Lcom/xj/base/sdkconfig/ResizeQueryParamGlideModelLoader;" &&
            method.returnType == "Z" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Ljava/lang/String;"
    }
}
