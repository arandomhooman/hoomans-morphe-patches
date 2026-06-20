package hooman.morphe.patches.photoeditorpolish.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import hooman.morphe.patches.photoeditorpolish.integrity.disableSignatureVerificationPatch
import hooman.morphe.patches.photoeditorpolish.integrity.patchNativeTamperCheckPatch

@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
    description = "Unlocks the Pro features in Photo Editor Polish without a subscription and drops " +
        "the ads and upgrade prompts. The AI tools and cloud assets, which the developer runs on its " +
        "servers, stay locked.",
) {
    // A re-signed (patched) build can't launch with the app's anti-tamper active: a native check in
    // libitcore.so and a cluster of DEX signature checks each kill it. Bundle both bypasses so the
    // user sees one patch and can't apply Unlock Pro without the checks that make it runnable.
    dependsOn(disableSignatureVerificationPatch, patchNativeTamperCheckPatch)

    compatibleWith(
        Compatibility(
            name = "Photo Editor Polish",
            packageName = "photo.editor.photoeditor.photoeditorpro",
            appIconColor = 0xF82888,
            targets = listOf(AppTarget("1.763.262")),
        ),
    )

    execute {
        // The whole app funnels its "is the user Pro" decision through one static getter in the
        // prefs helper (jadx: C7046yf): i(Context) = getBoolean(subscribePro) || getBoolean(<sku>).
        // 216 call sites read it, and the free-user check a(Context) = !(h() || i()) calls it too,
        // so forcing it true flips Pro on app-wide and turns the free-user/ad path off. The only
        // writers are the Play Billing purchase/restore callbacks, which never fire on a free
        // account, so forcing the read is reset-proof. Strings are natively encrypted (libitcore),
        // so there's nothing to fingerprint on: pin the obfuscated class by name and pick the getter
        // by shape (the lone static (Context)Z that reads two booleans). Fail loudly if it moved.
        val prefsClass = mutableClassDefByOrNull("Lyf;")
            ?: throw PatchException(
                "Photo Editor Polish: prefs/billing helper Lyf; not found. The obfuscation map changed.",
            )

        val isProGetter = prefsClass.methods.singleOrNull { method ->
            AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "Z" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes.first().toString() == "Landroid/content/Context;" &&
                (method.implementation?.instructions?.count { instruction ->
                    instruction is ReferenceInstruction &&
                        (instruction.reference as? MethodReference)?.let { reference ->
                            reference.definingClass == "Landroid/content/SharedPreferences;" &&
                                reference.name == "getBoolean"
                        } == true
                } ?: 0) >= 2
        } ?: throw PatchException(
            "Photo Editor Polish: the Pro getter (static (Context)Z reading two booleans) was not " +
                "found uniquely in Lyf; (the premium-flag shape has changed).",
        )

        isProGetter.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
