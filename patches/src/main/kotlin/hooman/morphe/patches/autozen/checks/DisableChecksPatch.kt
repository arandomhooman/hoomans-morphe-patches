package hooman.morphe.patches.autozen.checks

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

// Internal (no name): applied automatically as a dependency of the AutoZen patches. A re-signed
// sideload trips two independent boot blocks, both in DEX (there is no libpairipcore.so, so no native
// wall). This neutralizes both so the patched app can launch.
@Suppress("unused")
val disableChecksPatch = bytecodePatch(
    description = "Removes AutoZen's two re-sign boot blocks: the PairIP Google Play license check and " +
        "the Firebase App Check \"installed from Play\" check. Applied automatically with the AutoZen " +
        "patches so the patched app can open.",
) {
    compatibleWith(
        Compatibility(
            name = "AutoZen",
            packageName = "com.zenthek.autozen",
            appIconColor = 0x1B1B1B,
            targets = listOf(AppTarget("8.0.10")),
        ),
    )

    execute {
        // Block 1 - PairIP Play licensing. Application.attachBaseContext calls
        // LicenseClient.checkLicense(context); on a sideloaded install the LVL response is NOT_LICENSED
        // and it launches the paywall. PairIP keeps these names unobfuscated; pin by the service action.
        val licenseClass = classDefByStrings("com.android.vending.licensing.ILicensingService")
            .firstOrNull()
            ?: throw PatchException(
                "AutoZen: PairIP LicenseClient (ILicensingService) not found. The license layout changed.",
            )
        mutableClassDefBy(licenseClass).methods.firstOrNull { method ->
            method.name == "checkLicense" &&
                method.returnType == "V" &&
                method.parameterTypes == listOf("Landroid/content/Context;")
        }?.addInstructions(0, "return-void")
            ?: throw PatchException("AutoZen: PairIP LicenseClient.checkLicense(Context) not found.")

        // Block 2 - Firebase App Check installer gate. SplashScreenViewModel shows AppCheckFailedActivity
        // when the installer is not an allowed store; IsValidInstallerUseCase.execute() decides that.
        // The app packages are not obfuscated, so pin by descriptor and force it true.
        val installerCheck = mutableClassDefByOrNull(
            "Lcom/zenthek/domain/playstore/IsValidInstallerUseCase;",
        ) ?: throw PatchException(
            "AutoZen: IsValidInstallerUseCase not found. The install-source check moved.",
        )
        installerCheck.methods.firstOrNull { method ->
            method.name == "execute" &&
                method.returnType == "Z" &&
                method.parameterTypes.isEmpty()
        }?.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        ) ?: throw PatchException("AutoZen: IsValidInstallerUseCase.execute()Z not found.")
    }
}
