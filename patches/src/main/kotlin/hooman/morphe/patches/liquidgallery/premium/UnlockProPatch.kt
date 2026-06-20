package hooman.morphe.patches.liquidgallery.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.liquidgallery.license.disableLicenseCheckPatch

@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
    description = "Unlocks all of Liquid Gallery's Pro features without a purchase.",
) {
    // A sideloaded build can't launch with the license check active, so bundle it as a dependency.
    dependsOn(disableLicenseCheckPatch)

    compatibleWith(
        Compatibility(
            name = "Liquid Gallery",
            packageName = "com.soepic.photogallery.release",
            appIconColor = 0x6750A4,
            targets = listOf(AppTarget("2.0.14"), AppTarget("2.1.11")),
        ),
    )

    execute {
        // Pro is the local pref "is_premium_user" (gallery_prefs), read through one getter
        // isPremiumUser()Z; the exposed StateFlow is seeded from that same getter, so forcing it
        // true covers both. The only writer is the RevenueCat purchase/restore callback, which never
        // runs on a free account, so forcing the read is reset-proof. Pin by the key string, match
        // the getter by shape (no-arg, returns Z); fail loudly if it changed.
        val getter = classDefByStrings("is_premium_user")
            .firstNotNullOfOrNull { classDef ->
                mutableClassDefBy(classDef).methods.firstOrNull { method ->
                    method.name == "isPremiumUser" &&
                        method.returnType == "Z" &&
                        method.parameterTypes.isEmpty()
                }
            }
            ?: throw PatchException(
                "Liquid Gallery: no-arg isPremiumUser()Z getter reading \"is_premium_user\" not " +
                    "found — the premium-flag shape has changed.",
            )

        getter.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
