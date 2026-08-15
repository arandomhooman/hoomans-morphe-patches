package hooman.morphe.patches.moneta.license

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.Method

// Internal (no name): applied automatically as a dependency of Unlock Plus.
@Suppress("unused")
val disableLicenseCheckPatch = bytecodePatch(
    description = "Removes the PairIP Google Play license check that opens a Play paywall and " +
        "shuts down a re-signed install. Applied automatically with Unlock Plus so the patched " +
        "app can launch.",
) {
    compatibleWith(
        Compatibility(
            name = "Moneta",
            packageName = "currency.converter.moneta",
            appIconColor = 0x4BBA17,
            targets = listOf(AppTarget("1.2.2")),
        ),
    )

    execute {
        // Both Application.attachBaseContext() and LicenseContentProvider.onCreate() enter the same
        // DEX-level PairIP LicenseClient method. Moneta has no libpairipcore.so, so stopping that
        // method prevents NOT_LICENSED without a native VM bypass.
        val licenseClass = classDefByStrings(
            "com.android.vending.licensing.ILicensingService",
        ).singleOrNull {
            it.type == "Lcom/pairip/licensecheck/LicenseClient;"
        } ?: throw PatchException(
            "Moneta: PairIP LicenseClient (ILicensingService) not found uniquely.",
        )
        val mutableLicenseClass = mutableClassDefBy(licenseClass)

        fun noOp(methodName: String, predicate: (Method) -> Boolean) {
            val method = mutableLicenseClass.methods.singleOrNull {
                it.name == methodName && predicate(it)
            } ?: throw PatchException(
                "Moneta: PairIP LicenseClient.$methodName() not found uniquely. " +
                    "The license-check layout changed.",
            )
            method.addInstructions(0, "return-void")
        }

        noOp("initializeLicenseCheck") {
            it.returnType == "V" && it.parameterTypes.isEmpty()
        }
        noOp("scheduleAppShutdown") {
            it.returnType == "V" && it.parameterTypes.isEmpty()
        }
    }
}
