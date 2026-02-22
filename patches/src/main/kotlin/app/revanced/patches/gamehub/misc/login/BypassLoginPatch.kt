package app.revanced.patches.gamehub.misc.login

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.stringOption
import app.revanced.patches.gamehub.misc.token.TOKEN_PROVIDER_CLASS
import app.revanced.patches.gamehub.misc.token.tokenProviderClinitFingerprint
import app.revanced.patches.gamehub.misc.token.tokenResolutionPatch
import app.revanced.patches.gamehub.misc.tokenexpiry.bypassTokenExpiryPatch
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstructionOrThrow
import app.revanced.util.indexOfFirstInstructionReversedOrThrow
import app.revanced.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val bypassLoginPatch = bytecodePatch(
    name = "Bypass login",
    description = "Bypasses the login requirement by spoofing user credentials.",
) {
    compatibleWith("com.xiaoji.egggame"("5.3.5"))

    dependsOn(bypassTokenExpiryPatch, tokenResolutionPatch)

    val username by stringOption(
        key = "username",
        default = "GHLite",
        title = "Username",
        description = "The username shown in the app profile. Maximum 8 characters.",
        required = true,
    ) { it != null && it.isNotEmpty() && it.length <= 8 }

    val nickname by stringOption(
        key = "nickname",
        default = "GameHub Lite",
        title = "Nickname",
        description = "The display nickname shown in the app. Maximum 32 characters.",
        required = true,
    ) { it != null && it.isNotEmpty() && it.length <= 32 }

    val avatarEmoji by stringOption(
        key = "avatarEmoji",
        default = "🎮",
        title = "Avatar emoji",
        description = "The emoji used as the avatar. Must be a single emoji.",
        required = true,
    ) {
        if (it == null) return@stringOption false
        val iterator = java.text.BreakIterator.getCharacterInstance()
        iterator.setText(it)
        var count = 0
        while (iterator.next() != java.text.BreakIterator.DONE) count++
        count == 1
    }

    execute {
        val emoji = avatarEmoji!!
        getAvatarFingerprint.method.returnEarly(emoji)
        getNicknameFingerprint.method.returnEarly(nickname!!)
        getUsernameFingerprint.method.returnEarly(username!!)
        getUidFingerprint.method.returnEarly(99999)
        isLoginFingerprint.method.returnEarly(true)

        // H: HomeLeftMenuDialog — remove the "User Center" menu item from the left drawer.
        // The item is constructed between a new-instance of $MenuItem and a List.add() call,
        // bookmarked by an SGET that loads the menu_user_center_normal drawable id.
        homeLeftMenuDialogFingerprint.method.apply {
            val sgetIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.SGET &&
                    getReference<FieldReference>()?.name == "menu_user_center_normal"
            }

            // Scan backward from sget to find the new-instance that starts this menu item block.
            val startIndex = indexOfFirstInstructionReversedOrThrow(sgetIndex) {
                opcode == Opcode.NEW_INSTANCE
            }

            // Scan forward from sget to find the List.add() that ends this menu item block.
            val endIndex = indexOfFirstInstructionOrThrow(sgetIndex) {
                opcode == Opcode.INVOKE_INTERFACE &&
                    getReference<MethodReference>()?.name == "add"
            }

            // Remove all instructions in the User Center block (backward to keep indices stable).
            for (i in endIndex downTo startIndex) {
                removeInstruction(i)
            }
        }

        // H: HomeLeftMenuDialog.l1() — avatar/username header click handler.
        // Strips the Intent creation + startActivity call so tapping the header
        // just dismisses the drawer instead of opening User Center.
        homeLeftMenuAvatarClickFingerprint.method.apply {
            val startActivityIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_VIRTUAL &&
                    getReference<MethodReference>()?.name == "startActivity"
            }
            val newInstanceIndex = indexOfFirstInstructionReversedOrThrow(startActivityIndex) {
                opcode == Opcode.NEW_INSTANCE &&
                    getReference<TypeReference>()?.type == "Landroid/content/Intent;"
            }
            for (i in startActivityIndex downTo newInstanceIndex) {
                removeInstruction(i)
            }
        }

        // Set TokenProvider.loginBypassed = true so the token resolution extension
        // knows to fetch from the token-refresh service instead of using the original token.
        tokenProviderClinitFingerprint.method.apply {
            val returnVoidIndex = indexOfFirstInstructionOrThrow { opcode == Opcode.RETURN_VOID }
            addInstructions(
                returnVoidIndex,
                """
                    const/4 v0, 0x1
                    sput-boolean v0, $TOKEN_PROVIDER_CLASS->loginBypassed:Z
                """,
            )
        }
    }
}
