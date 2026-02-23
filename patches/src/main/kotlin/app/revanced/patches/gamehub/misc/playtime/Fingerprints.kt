package app.revanced.patches.gamehub.misc.playtime

import app.revanced.patcher.fingerprint

/**
 * Matches the `invokeSuspend` method of `GameLibraryRepository$loadRecentGameList$2`,
 * the coroutine that fetches playtime data from GameHub's server via
 * `heartbeat/game/getUserPlayTimeList`.
 */
internal val getUserPlayTimeListFingerprint = fingerprint {
    strings("heartbeat/game/getUserPlayTimeList")
}
