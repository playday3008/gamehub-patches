package app.revanced.patches.gamehub.network.cdn

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.gamehub.EXTENSION_STEAM_CDN_HELPER
import app.revanced.patches.gamehub.GAMEHUB_PACKAGE
import app.revanced.patches.gamehub.GAMEHUB_VERSION
import app.revanced.patches.gamehub.misc.extension.sharedGamehubExtensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val useSteamCdnPatch = bytecodePatch(
    name = "Use Steam CDN for assets",
    description = "Replaces the third-party bigeyes CDN with Steam's official CDN for game header images.",
) {
    dependsOn(sharedGamehubExtensionPatch)
    compatibleWith(GAMEHUB_PACKAGE(GAMEHUB_VERSION))

    execute {
        // --- 1. Patch SteamUrlHelper methods to resolve header URLs via extension ---
        // Both methods are instance: (int appId, String unused) → String
        // p0=this, p1=appId. Reuse p0 for the result since we return immediately.
        val earlyReturn = """
            invoke-static {p1}, $EXTENSION_STEAM_CDN_HELPER->resolveHeaderUrl(I)Ljava/lang/String;
            move-result-object p0
            return-object p0
        """

        steamUrlHelperHeaderFingerprint.method.addInstructions(0, earlyReturn)
        standaloneUrlHelperHeaderFingerprint.method.addInstructions(0, earlyReturn)

        // --- 2. Patch Glide model loader to handle relative steam/ paths ---
        // The API sometimes returns relative paths like "steam/apps/123/header.jpg?t=..."
        // without a domain prefix. The custom Glide loader only handles bigeyes.com URLs,
        // so these relative paths fall through to file loaders and fail.

        // 2a. Patch handles() to also accept "steam/" paths.
        resizeLoaderHandlesFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    const-string v0, "steam/"
                    invoke-virtual {p1, v0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :original
                    return v0
                """,
                ExternalLabel("original", getInstruction(0)),
            )
        }

        // 2b. Patch buildLoadData() to normalize relative paths and delegate to underlying loader.
        resizeLoaderBuildLoadDataFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {p1}, $EXTENSION_STEAM_CDN_HELPER->resolveImageUrl(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v0
                    if-eqz v0, :original
                    invoke-static {v0}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;
                    move-result-object v0
                    iget-object v1, p0, Lcom/xj/base/sdkconfig/ResizeQueryParamGlideModelLoader;->a:Lcom/bumptech/glide/load/model/ModelLoader;
                    invoke-interface {v1, v0, p2, p3, p4}, Lcom/bumptech/glide/load/model/ModelLoader;->buildLoadData(Ljava/lang/Object;IILcom/bumptech/glide/load/Options;)Lcom/bumptech/glide/load/model/ModelLoader${'$'}LoadData;
                    move-result-object v0
                    return-object v0
                """,
                ExternalLabel("original", getInstruction(0)),
            )
        }

        // --- 3. Patch GameDetailEntity getters to rewrite bigeyes URLs to Steam CDN ---
        // The API returns back_image and cover_image URLs pointing to the bigeyes CDN.
        // Normalizing at the getter ensures the game detail background loads from Steam CDN.
        val normalizeReturn = { fingerprint: app.revanced.patcher.Fingerprint ->
            fingerprint.method.apply {
                val returnIndices = instructions.withIndex()
                    .filter { it.value.opcode == Opcode.RETURN_OBJECT }
                    .map { it.index }
                    .reversed()

                for (index in returnIndices) {
                    val register = (getInstruction<OneRegisterInstruction>(index)).registerA
                    replaceInstruction(
                        index,
                        "invoke-static {v$register}, $EXTENSION_STEAM_CDN_HELPER->normalizeOrPass(Ljava/lang/String;)Ljava/lang/String;",
                    )
                    addInstructions(
                        index + 1,
                        """
                            move-result-object v$register
                            return-object v$register
                        """,
                    )
                }
            }
        }

        normalizeReturn(getBackImageFingerprint)
        normalizeReturn(getDetailCoverImageFingerprint)

        // --- 4. Patch getCoverImagePath() to normalize relative paths at the data source ---
        // Some Glide loads use Uri models instead of String models, bypassing the
        // String-based model loader patch. Normalizing at the getter ensures all
        // consumers get full URLs regardless of how they call Glide.
        normalizeReturn(getCoverImagePathFingerprint)
    }
}
