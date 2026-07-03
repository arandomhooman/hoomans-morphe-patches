package hooman.morphe.patches.directchat.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks DirectChat's premium without the in-app purchase, so the chat-head styles and " +
        "themes, extra bubble options and the other paid settings all open. Premium is a local flag the " +
        "app seeds from its own purchase, so the unlock holds across restarts on a free account. A few " +
        "spots read the stored purchase flag straight from preferences, so a chat-head banner ad can still " +
        "show on a fresh install until premium has been set once.",
) {
    compatibleWith(
        Compatibility(
            name = "DirectChat",
            packageName = "net.uniquegem.directchat",
            appIconColor = 0x2196F3,
            targets = listOf(AppTarget("1.9.8")),
        ),
    )

    execute {
        // Every gated feature reads MainScreen.isApplicationPremium(), a static getter over the private
        // static boolean isPremium. isPremium is seeded from the local "premiumCheck" pref and only the
        // app's own purchase handlers ever write it true, with no server re-validation reading back into
        // the gate, so forcing the getter true unlocks premium and stays unlocked on a free account. The
        // package is unobfuscated, so pin the class by its real descriptor and the method by name plus
        // shape; also require the body to read a static boolean field so a future refactor that keeps the
        // name but changes the getter fails loudly instead of patching the wrong method.
        val mainScreen = mutableClassDefByOrNull("Lnet/uniquegem/directchat/FrontPage/MainScreen;")
            ?: throw PatchException(
                "DirectChat: net.uniquegem.directchat.FrontPage.MainScreen not found — package layout " +
                    "changed; re-derive the premium gate.",
            )

        val gate = mainScreen.methods.firstOrNull { method ->
            method.name == "isApplicationPremium" &&
                AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "Z" &&
                method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.any { instruction ->
                    instruction.opcode == Opcode.SGET_BOOLEAN &&
                        ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.type == "Z"
                } == true
        }
            ?: throw PatchException(
                "DirectChat: isApplicationPremium()Z reading a static boolean field not found on " +
                    "MainScreen — the premium gate shape changed; re-derive.",
            )

        gate.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
