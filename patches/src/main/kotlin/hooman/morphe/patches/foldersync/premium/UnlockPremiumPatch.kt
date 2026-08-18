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
            targets = listOf(AppTarget("4.9.3"), AppTarget("4.12.0")),
        ),
    )

    execute {
        // The real gate is VersionFeaturesConfig. Every premium/ad consumer reads one off the
        // AppLiteVersionFeatures StateFlow: field b = isPaidVersion (unlocks premium), a() = showAds
        // (returns false once paid), c/d = the account and sync-pair limits (already Int.MAX in the
        // lite build). The config is seeded from getPremiumVersionPurchased(), but a billing
        // coroutine rebuilds it live: after the Play check fails for a non-owner it calls
        // setPremiumVersionPurchased(false), which pushes a fresh PreferenceState into the flow that
        // AppLiteVersionFeatures collects and turns back into a config with isPaidVersion = false.
        // So forcing only the pref getter is overwritten a moment later. Force the field at its one
        // write point instead: the 4-arg data-class constructor. Every config the app builds --
        // initial seed and every rebuild -- then stores isPaidVersion = true, so premium stays on and
        // ads stay off regardless of the billing result, and the limits carry through untouched.
        val config = mutableClassDefByOrNull("Ldk/tacit/foldersync/services/VersionFeaturesConfig;")
            ?: throw PatchException(
                "FolderSync: VersionFeaturesConfig not found — package layout changed.",
            )

        val configCtor = config.methods.firstOrNull { method ->
            method.name == "<init>" &&
                method.returnType == "V" &&
                method.parameterTypes.map { it.toString() } == listOf(
                    "Ldk/tacit/foldersync/services/VersionIdentifier;", "Z", "I", "I",
                )
        }
            ?: throw PatchException(
                "FolderSync: VersionFeaturesConfig(VersionIdentifier, Z, I, I) constructor not found " +
                    "— the version-features shape changed.",
            )

        // p2 is the isPaidVersion argument; overwrite it before the constructor's iput-boolean stores
        // it into field b. p2 is used only for that store, so this is safe.
        configCtor.addInstructions(0, "const/4 p2, 0x1")

        // Belt and suspenders: keep the pref getter forced too. It seeds the initial config, backs
        // the settings "premium" row, and feeds any direct reader that skips VersionFeaturesConfig.
        // Premium funnels through this read ("premium_version" pref, false for a free account) and the
        // only writer is the Play Billing result (never true for a non-owner).
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
