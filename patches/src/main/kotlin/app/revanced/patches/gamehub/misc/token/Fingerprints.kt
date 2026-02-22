package app.revanced.patches.gamehub.misc.token

import app.revanced.patcher.fingerprint
import app.revanced.patches.gamehub.EXTENSION_TOKEN_PROVIDER

internal const val TOKEN_PROVIDER_CLASS = EXTENSION_TOKEN_PROVIDER

internal val getTokenFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/common/user/UserManager;" && method.name == "getToken"
    }
}

internal val tokenProviderClinitFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == TOKEN_PROVIDER_CLASS && method.name == "<clinit>"
    }
}
