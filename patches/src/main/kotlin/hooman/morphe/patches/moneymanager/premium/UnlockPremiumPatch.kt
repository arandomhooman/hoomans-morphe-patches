package hooman.morphe.patches.moneymanager.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import hooman.morphe.patches.moneymanager.signature.bypassSignatureCheckPatch

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks Money Manager's premium and removes the ads: passcode lock, more accounts, " +
        "custom categories and themes, repeat transactions, advanced stats, and local backup all open. " +
        "PC/web sync and cloud backup stay tied to the server (it issues an expiry the app checks " +
        "online), so those keep needing a real subscription.",
) {
    compatibleWith(
        Compatibility(
            name = "Money Manager",
            packageName = "com.realbyteapps.moneymanagerfree",
            appIconColor = 0xEE3D1F,
            // versionName carries the " GF" (Google Free) flavor suffix; morphe-cli matches the full
            // string, so the target has to include it.
            targets = listOf(AppTarget("4.11.1 GF")),
        ),
    )

    // The app's License self-check blocks any re-signed build with an "application is not valid"
    // dialog at launch, so the unlock is useless without this companion. Bundled as a mandatory
    // dependency so users can't apply the unlock without it.
    dependsOn(bypassSignatureCheckPatch)

    execute {
        // Every on-device premium gate (and the ad code in RealbyteActivity / AdInterstitialSave) reads
        // Globals.Q or Globals.R. Both decide from the cached Globals.M int, which is seeded from the
        // "premium" pref; the app writes premium=false back on a failed retry, so patching the pref or M
        // wouldn't hold. Forcing the two getters to return true at entry ignores M and that retry-write,
        // so it stays unlocked across restarts. CloudUtil.q (the server expiry check) is downstream of
        // these and unaffected, which is why sync/cloud backup stay gated.
        val globals = mutableClassDefByOrNull("Lcom/realbyte/money/config/Globals;")
            ?: throw PatchException(
                "Money Manager: Globals not found — package layout changed.",
            )

        // R8 renamed the getters to single letters, so don't pin by name. Globals has ten static
        // (Context)Z methods; only Q and R read the "premium" pref literal, so that string plus the
        // shape selects exactly those two. Re-derive if this stops matching a clean pair.
        val premiumGates = globals.methods.filter { method ->
            AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "Z" &&
                method.parameterTypes == listOf("Landroid/content/Context;") &&
                method.implementation?.instructions?.any { instruction ->
                    (instruction as? ReferenceInstruction)?.reference
                        ?.let { it as? StringReference }
                        ?.string == "premium"
                } == true
        }

        if (premiumGates.size != 2) {
            throw PatchException(
                "Money Manager: expected exactly 2 static (Context)Z premium gates reading " +
                    "\"premium\" (Globals.Q and Globals.R), found ${premiumGates.size}. The gate " +
                    "shape changed; re-derive.",
            )
        }

        premiumGates.forEach { gate ->
            gate.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """,
            )
        }
    }
}
