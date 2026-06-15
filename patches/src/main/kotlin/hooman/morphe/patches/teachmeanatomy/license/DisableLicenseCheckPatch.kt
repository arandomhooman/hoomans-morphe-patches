package hooman.morphe.patches.teachmeanatomy.license

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

// Internal (no name): applied automatically as a dependency of Unlock Premium.
@Suppress("unused")
val disableLicenseCheckPatch = bytecodePatch(
    description = "Removes the PairIP Google Play license check, which shows a paywall and " +
        "kills the app on a sideloaded (patched) install. Applied automatically with Unlock " +
        "Premium so the patched app can launch.",
) {
    compatibleWith(
        Compatibility(
            name = "Teach Me Anatomy",
            packageName = "com.atomengineapps.teachmeanatomy",
            appIconColor = 0x00A99D,
            targets = listOf(AppTarget("5.115")),
        ),
    )

    execute {
        // initializeLicenseCheck() is PairIP's entry point (from LicenseContentProvider and the
        // static checkLicense()); returning immediately skips the Play bind, so NOT_LICENSED never
        // fires and no paywall/shutdown is queued.
        InitializeLicenseCheckFingerprint.method.addInstructions(0, "return-void")
        // Failsafe: also neutralize the paywall+shutdown launcher.
        StartPaywallActivityFingerprint.method.addInstructions(0, "return-void")
    }
}
