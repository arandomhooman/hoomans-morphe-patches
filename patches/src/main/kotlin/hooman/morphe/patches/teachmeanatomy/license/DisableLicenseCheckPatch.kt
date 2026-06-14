package hooman.morphe.patches.teachmeanatomy.license

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val disableLicenseCheckPatch = bytecodePatch(
    name = "Disable License Check",
    description = "Removes the PairIP Google Play license check that otherwise shows a paywall " +
        "and kills the app on any sideloaded (patched) install. Required for the patched app to " +
        "launch at all.",
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
        // Entry point: LicenseContentProvider.onCreate() and the static checkLicense() both call
        // initializeLicenseCheck(). Returning immediately skips the bind to Play's licensing
        // service entirely, so NOT_LICENSED is never received and no paywall/shutdown is queued.
        InitializeLicenseCheckFingerprint.method.addInstructions(0, "return-void")

        // Failsafe: neutralize the paywall+shutdown launcher directly, so even an unforeseen path
        // into the check cannot show the Play paywall or kill the process.
        StartPaywallActivityFingerprint.method.addInstructions(0, "return-void")
    }
}
