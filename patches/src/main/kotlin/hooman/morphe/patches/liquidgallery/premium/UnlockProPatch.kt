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
    description = "Unlocks Liquid Gallery Pro without a purchase. Pro is a single local flag " +
        "with no server-side check, so switching it on enables the Pro features the app gates " +
        "on device.",
) {
    // The PairIP license check shuts down any sideloaded build, so it must run whenever Pro is
    // unlocked. Pull it in as an internal dependency instead of a separate user-facing patch.
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
        // Pro is a local SharedPreferences boolean: key "is_premium_user" in the "gallery_prefs"
        // file, owned by com.soepic.photogallery.utils.SharedPreferencesManager. Every gate reads
        // it through one chokepoint getter `isPremiumUser()Z` (`getBoolean("is_premium_user",
        // false)`), and the exposed `isPremiumUser` StateFlow is *seeded* from that same getter at
        // construction — so forcing the getter true makes both the initial flow value and every
        // direct read report Pro. RevenueCat validates the subscription server-side, but the only
        // writer (setPremiumUser, called solely from the RevenueCat purchase/restore callback in
        // gk/a.java) never runs on a free account, so forcing the *read* is reset-proof.
        //
        // Pin the class by its unique premium-pref key string (survives class renames) and the
        // getter by name + shape (no-arg, returns Z). Fail loudly if the shape changed.
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
