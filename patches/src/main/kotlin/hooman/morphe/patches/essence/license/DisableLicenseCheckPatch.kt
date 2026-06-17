package hooman.morphe.patches.essence.license

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.Method

// Internal (no name): applied automatically as a dependency of Unlock Pro.
@Suppress("unused")
val disableLicenseCheckPatch = bytecodePatch(
    description = "Removes the PairIP Google Play license check that returns NOT_LICENSED on a " +
        "sideloaded (patched) install and shuts the app down before any UI loads. Applied " +
        "automatically with Unlock Pro so the patched app can launch.",
) {
    compatibleWith(
        Compatibility(
            name = "Essence",
            packageName = "com.appfactory.essence",
            appIconColor = 0x005030,
            targets = listOf(AppTarget("4.2.8")),
        ),
    )

    execute {
        // PairIP's DEX licensecheck (LicenseClient). LicenseContentProvider.onCreate() builds a
        // LicenseClient and calls initializeLicenseCheck() at process start; on a re-signed build the
        // installer/license check fails and the failure path launches LicenseActivity, which calls
        // System.exit. No native libpairipcore.so here, so a DEX no-op is enough.
        // PairIP keeps these names unobfuscated; pin the class by the licensing-service action string.
        val licenseClass = classDefByStrings("com.android.vending.licensing.ILicensingService")
            .firstOrNull()
            ?: throw PatchException(
                "Essence: PairIP LicenseClient (ILicensingService) not found.",
            )
        val mutableLicenseClass = mutableClassDefBy(licenseClass)

        fun noOp(methodName: String, predicate: (Method) -> Boolean) {
            val method = mutableLicenseClass.methods.firstOrNull {
                it.name == methodName && predicate(it)
            } ?: throw PatchException(
                "Essence: PairIP LicenseClient.$methodName() not found. The license-check layout changed.",
            )
            method.addInstructions(0, "return-void")
        }

        // initializeLicenseCheck() is the single entry both startup paths funnel through; no-opping
        // it stops the check before it can reach the shutdown. scheduleAppShutdown() is the delayed
        // kill, neutered as a failsafe.
        noOp("initializeLicenseCheck") { it.returnType == "V" && it.parameterTypes.isEmpty() }
        noOp("scheduleAppShutdown") { it.returnType == "V" && it.parameterTypes.isEmpty() }
    }
}
