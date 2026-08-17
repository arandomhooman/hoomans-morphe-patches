package hooman.morphe.patches.cronometer.license

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

// Internal (no name): applied automatically as a dependency of Unlock Gold.
@Suppress("unused")
val disablePairipLicenseCheckPatch = bytecodePatch(
    description = "Removes the PairIP Google Play license check that Cronometer 4.57.4 added. On a " +
        "sideloaded (patched) install it returns NOT_LICENSED and shows the \"get this app from Google " +
        "Play\" screen, then closes the app before anything loads. Applied automatically with Unlock Gold " +
        "so the patched app can open.",
) {
    compatibleWith(
        Compatibility(
            name = "Cronometer",
            packageName = "com.cronometer.android.gold",
            appIconColor = 0xF26B21,
            targets = listOf(AppTarget("4.57.4")),
        ),
    )

    execute {
        // 4.57.4 wraps the app in PairIP (com.pairip.application.Application). attachBaseContext runs the
        // native VM setup, then SignatureCheck.verifyIntegrity, then LicenseClient.checkLicense(context).
        // The native VM startup and the signature check both tolerate our re-sign (the app reaches a
        // graceful LicenseActivity dialog, not a native crash), so the only thing blocking launch is the
        // DEX LVL check. Gold itself lives in libapp.so and is handled by the raw-resource patch; only a
        // few peripheral DEX methods are VM-virtualized and none are on this path, so no-opping the
        // license gate is safe. PairIP keeps these names unobfuscated; pin LicenseClient by the
        // licensing-service action string.
        val licenseClass = classDefByStrings("com.android.vending.licensing.ILicensingService")
            .firstOrNull()
            ?: throw PatchException(
                "Cronometer: PairIP LicenseClient (ILicensingService) not found. The license layout changed.",
            )
        val mutableLicenseClass = mutableClassDefBy(licenseClass)

        // checkLicense(Context) is the static entry the PairIP Application calls from attachBaseContext;
        // no-opping it stops the whole check before it can bind the licensing service or launch the
        // paywall. Required.
        val checkLicense = mutableLicenseClass.methods.firstOrNull { method ->
            method.name == "checkLicense" &&
                method.returnType == "V" &&
                method.parameterTypes == listOf("Landroid/content/Context;")
        } ?: throw PatchException(
            "Cronometer: PairIP LicenseClient.checkLicense(Context) not found. The license layout changed.",
        )
        checkLicense.addInstructions(0, "return-void")

        // Neuter the other kill paths as a failsafe (the instance init and the delayed shutdown). Present
        // in this build, but tolerate a layout where one is absent.
        for (name in listOf("initializeLicenseCheck", "scheduleAppShutdown")) {
            mutableLicenseClass.methods.firstOrNull {
                it.name == name && it.returnType == "V" && it.parameterTypes.isEmpty()
            }?.addInstructions(0, "return-void")
        }

        // Insurance: no-op the PairIP signature integrity check so a release signed with a key that is
        // not on PairIP's allowlist still passes. It currently passes for our debug key, so best-effort.
        mutableClassDefByOrNull("Lcom/pairip/SignatureCheck;")?.let { sig ->
            sig.methods.firstOrNull {
                it.name == "verifyIntegrity" &&
                    it.returnType == "V" &&
                    it.parameterTypes == listOf("Landroid/content/Context;")
            }?.addInstructions(0, "return-void")
        }
    }
}
