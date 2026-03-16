package app.revanced.patches.gamehub.misc.credits

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableField.Companion.toMutable
import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.firstClassDef
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.gamehub.CONTENT_TYPE_CREDITS
import app.revanced.patches.gamehub.EXTENSION_CREDITS_HELPER
import app.revanced.patches.gamehub.EXTENSION_PREFS
import app.revanced.patches.gamehub.GAMEHUB_PACKAGE
import app.revanced.patches.gamehub.GAMEHUB_VERSION
import app.revanced.patches.gamehub.misc.extension.sharedGamehubExtensionPatch
import app.revanced.patches.gamehub.misc.settings.settingsMenuPatch
import app.revanced.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.value.ImmutableIntEncodedValue

// Recurring author pairs.
private val PLAYDAY = "PlayDay" to "https://github.com/playday3008"
private val PRODUCDEVITY = "Producdevity" to "https://github.com/Producdevity/gamehub-lite"
private val REVANCED = "ReVanced Contributors" to "https://github.com/revanced"

private const val ENTITY_CLASS = "Lcom/xj/landscape/launcher/data/model/entity/SettingItemEntity;"
private const val VIEW_MODEL_CLASS = "Lcom/xj/landscape/launcher/vm/SettingItemViewModel;"
private const val BTN_HOLDER_CLASS = "Lcom/xj/landscape/launcher/ui/setting/holder/SettingBtnHolder;"
private const val EXTENSION = EXTENSION_CREDITS_HELPER
private const val CREDITS_HEX = "0x1f"

private lateinit var creditsMethod: MutableMethod
private var creditsInsertionIndex = 0

@Suppress("unused")
val creditsPatch = bytecodePatch(
    name = "Credits",
    description = "Shows credits for patches and their authors.",
) {
    compatibleWith(GAMEHUB_PACKAGE(GAMEHUB_VERSION))
    dependsOn(settingsMenuPatch, sharedGamehubExtensionPatch)

    apply {
        // Add a named constant field to SettingItemEntity for the Credits content type.
        firstClassDef(ENTITY_CLASS).staticFields.add(
            ImmutableField(
                ENTITY_CLASS,
                "CONTENT_TYPE_CREDITS",
                "I",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value,
                ImmutableIntEncodedValue(CONTENT_TYPE_CREDITS),
                emptySet(),
                null,
            ).toMutable(),
        )

        // Intercept the click event for the Credits button.
        // SettingBtnHolder.w() is static: w(Binding, SettingItemEntity, FocusableConstraintLayout) -> Unit
        // At index 0: p0=binding, p1=entity, p2=clicked view (a View subclass).
        firstMethod { definingClass == BTN_HOLDER_CLASS && name == "w" }.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-virtual {p1}, $ENTITY_CLASS->getContentType()I
                    move-result v0
                    const/16 v1, $CREDITS_HEX
                    if-ne v0, v1, :not_credits
                    invoke-virtual {p2}, Landroid/view/View;->getContext()Landroid/content/Context;
                    move-result-object v0
                    invoke-static {v0}, $EXTENSION->showCreditsDialog(Landroid/content/Context;)V
                    sget-object v0, Lkotlin/Unit;->a:Lkotlin/Unit;
                    return-object v0
                    :not_credits
                    nop
                """,
            )
        }

        // Inject the Credits button at the END of SettingItemViewModel.l(), just before
        // the return statement. This ensures the Credits item is always last in the list,
        // regardless of how many addSteamSetting() calls other patches make.
        creditsMethod = firstMethod { definingClass == VIEW_MODEL_CLASS && name == "l" }
        val returnIndex = creditsMethod.indexOfFirstInstructionReversedOrThrow {
            opcode == Opcode.RETURN_OBJECT
        }

        // Inject the TYPE_BTN (3) list entry for Credits right before return.
        creditsMethod.addInstructions(
            returnIndex,
            """
                new-instance v0, $ENTITY_CLASS
                const/4 v1, 0x3
                const/16 v2, $CREDITS_HEX
                const/4 v3, 0x0
                const/4 v4, 0x1
                const/16 v5, 0x4
                const/4 v6, 0x0
                invoke-direct/range {v0 .. v6}, $ENTITY_CLASS-><init>(IILandroid/util/SparseArray;ZILkotlin/jvm/internal/DefaultConstructorMarker;)V
                invoke-interface {p0, v0}, Ljava/util/List;->add(Ljava/lang/Object;)Z
            """,
        )

        // Credit registrations are injected before the button creation, so they run
        // first at runtime (populating CreditsHelper's list before the dialog can be shown).
        creditsInsertionIndex = returnIndex

        // Project credits.
        addCredit(
            "GameHub Revanced",
            "PlayDay" to "https://github.com/playday3008/gamehub-patches",
        )
        addCredit(
            "GameHub Lite",
            "Producdevity" to "https://github.com/Producdevity/gamehub-lite",
        )

        // Credits for resource/generic patches that cannot depend on creditsPatch.
        addCredit("Change app name", PLAYDAY)
        addCredit("Custom network security", REVANCED)
        addCredit("Enable Android debugging", REVANCED)
        addCredit("Override certificate pinning", REVANCED)
        addCredit("Change package name", REVANCED)
        addCredit("File manager access", "MT Manager" to null, PLAYDAY, REVANCED)
    }
}

/**
 * Registers a credit entry that will be shown in the Credits dialog.
 *
 * Must be called from within a patch's apply block that depends on [creditsPatch].
 * Each call injects smali into [SettingItemViewModel.l] that calls
 * [CreditsHelper.addCredit] at runtime.
 *
 * @param feature the feature or patch name
 * @param authors one or more `"Author Name" to "https://url"` pairs
 *                (use empty string or `null` for no URL)
 */
context(_: BytecodePatchContext)
internal fun addCredit(
    feature: String,
    vararg authors: Pair<String, String?>,
) {
    // Build smali that creates a LinkedHashMap, puts each author, then calls addCredit.
    val putSmali = authors.joinToString("\n") { (name, url) ->
        """
            const-string v1, "$name"
            const-string v2, "${url ?: ""}"
            invoke-interface {v0, v1, v2}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        """
    }
    creditsMethod.addInstructions(
        creditsInsertionIndex,
        """
            new-instance v0, Ljava/util/LinkedHashMap;
            invoke-direct {v0}, Ljava/util/LinkedHashMap;-><init>()V
            $putSmali
            const-string v1, "$feature"
            invoke-static {v1, v0}, $EXTENSION->addCredit(Ljava/lang/String;Ljava/util/Map;)V
        """,
    )
    // 2 (new + init) + 3 per author (const, const, put) + 2 (const feature + invoke)
    creditsInsertionIndex += 4 + (authors.size * 3)
}
