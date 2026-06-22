package hooman.morphe.patches.tumblr.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val enablePremiumUiPatch = bytecodePatch(
    name = "Enable premium UI",
    description = "Turns on the cosmetic Tumblr Premium look: the premium badge and the premium " +
        "settings entry. This only changes how the app looks on your device. It does not buy or unlock " +
        "anything on Tumblr's side, so ad-free, the badge on your blog, and anything the server checks " +
        "still need a real subscription. Use \"Remove ads\" for an actually ad-free feed.",
) {
    compatibleWith(
        Compatibility(
            name = "Tumblr",
            packageName = "com.tumblr",
            appIconColor = 0x001935,
            targets = listOf(AppTarget("45.0.0.109")),
        ),
    )

    execute {
        // UserInfo.C() reads the cached "pref_is_premium_user" flag that gates the premium badge and
        // premium settings entry. Force it true to turn the premium look on. UserInfo isn't obfuscated;
        // pin by descriptor and match C() by shape plus the "pref_is_premium_user" string it reads.
        val userInfo = mutableClassDefByOrNull("Lcom/tumblr/UserInfo;")
            ?: throw PatchException(
                "Tumblr: com.tumblr.UserInfo not found -- package layout changed.",
            )

        val premiumFlag = userInfo.methods.filter { method ->
            AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "Z" &&
                method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.any { instruction ->
                    (instruction as? ReferenceInstruction)?.reference
                        ?.let { it as? StringReference }
                        ?.string == "pref_is_premium_user"
                } == true
        }
        if (premiumFlag.size != 1) {
            throw PatchException(
                "Tumblr: expected exactly one static no-arg ()Z reading \"pref_is_premium_user\" " +
                    "(UserInfo.C()), found ${premiumFlag.size}. The premium flag shape changed; " +
                    "re-derive.",
            )
        }
        premiumFlag.single().addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
