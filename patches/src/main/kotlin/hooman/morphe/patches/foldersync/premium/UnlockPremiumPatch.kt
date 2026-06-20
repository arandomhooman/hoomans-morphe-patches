package hooman.morphe.patches.foldersync.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks FolderSync's premium without the in-app purchase and removes the ads, so " +
        "you get unlimited sync pairs and the advanced options.",
) {
    compatibleWith(
        Compatibility(
            name = "FolderSync",
            packageName = "dk.tacit.android.foldersync.lite",
            appIconColor = 0x406FB8,
            targets = listOf(AppTarget("4.9.3")),
        ),
    )

    execute {
        // Premium funnels through one read: getPremiumVersionPurchased() ("premium_version" pref, false
        // for a free account). Every VersionFeaturesConfig derives isPaidVersion/showAds from it, and the
        // only writer is the Play Billing result (never true for a non-owner), so forcing it true unlocks
        // premium and turns ads off app-wide, reset-proof.
        val prefs = mutableClassDefByOrNull("Ldk/tacit/foldersync/services/AppPreferenceManager;")
            ?: throw PatchException(
                "FolderSync: AppPreferenceManager not found — package layout changed.",
            )

        // The class isn't obfuscated, but pin the method by shape AND by the unique pref string it
        // reads so a future getter rename or reshuffle fails loudly instead of patching the wrong read.
        val getPremium = prefs.methods.firstOrNull { method ->
            method.name == "getPremiumVersionPurchased" &&
                method.returnType == "Z" &&
                method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.any { instruction ->
                    (instruction as? ReferenceInstruction)?.reference
                        ?.let { it as? StringReference }
                        ?.string == "premium_version"
                } == true
        }
            ?: throw PatchException(
                "FolderSync: getPremiumVersionPurchased()Z reading \"premium_version\" not found — " +
                    "the premium gate shape changed.",
            )

        getPremium.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
