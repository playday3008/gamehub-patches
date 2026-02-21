package app.revanced.patches.gamehub.misc.analytics

import app.revanced.patcher.fingerprint

internal val umengAppFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/umeng/UmengApp;" && method.name == "b"
    }
}

internal val umengAppModuleFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/umeng/UmengApp;" && method.name == "a"
    }
}

internal val iUmengServiceImplAFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/umeng/service/IUmengServiceImpl;" && method.name == "a"
    }
}

internal val iUmengServiceImplBFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/umeng/service/IUmengServiceImpl;" && method.name == "b"
    }
}

internal val iUmengServiceImplCFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/umeng/service/IUmengServiceImpl;" && method.name == "c"
    }
}

internal val iUmengServiceImplDFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/umeng/service/IUmengServiceImpl;" && method.name == "d"
    }
}

internal val iUmengServiceImplEFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/umeng/service/IUmengServiceImpl;" && method.name == "e"
    }
}

internal val iUmengServiceImplFFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/umeng/service/IUmengServiceImpl;" && method.name == "f"
    }
}

internal val iUmengServiceImplOnEventFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/umeng/service/IUmengServiceImpl;" &&
            method.name == "onEvent" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Ljava/lang/String;"
    }
}
