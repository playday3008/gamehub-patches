package app.revanced.patches.gamehub

// App metadata
internal const val GAMEHUB_PACKAGE = "com.xiaoji.egggame"
internal const val GAMEHUB_VERSION = "5.3.5"

// Extension class descriptors (smali format)
internal const val EXTENSION_PREFS = "Lapp/revanced/extension/gamehub/prefs/GameHubPrefs;"
internal const val EXTENSION_TOKEN_PROVIDER = "Lapp/revanced/extension/gamehub/token/TokenProvider;"
internal const val EXTENSION_BATTERY_HELPER = "Lapp/revanced/extension/gamehub/ui/BatteryHelper;"
internal const val EXTENSION_GAME_ID_HELPER = "Lapp/revanced/extension/gamehub/ui/GameIdHelper;"
internal const val EXTENSION_CURRENCY_HELPER = "Lapp/revanced/extension/gamehub/ui/AccountCurrencyHelper;"
internal const val EXTENSION_COMPAT_CACHE = "Lapp/revanced/extension/gamehub/ui/CompatibilityCache;"
internal const val EXTENSION_STEAM_CDN_HELPER = "Lapp/revanced/extension/gamehub/network/SteamCdnHelper;"

// Content-type constants for custom settings menu items
internal const val CONTENT_TYPE_SD_CARD_STORAGE = 0x18
internal const val CONTENT_TYPE_API = 0x1a
internal const val CONTENT_TYPE_LOG_REQUESTS = 0x1b
