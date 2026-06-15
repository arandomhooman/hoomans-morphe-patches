package hooman.morphe.patches.liquidgallery.license

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
        "sideloaded (patched) install and shuts the app down (\"Local install check failed due to " +
        "wrong installer\"). Applied automatically with Unlock Pro so the patched app can launch.",
) {
    compatibleWith(
        Compatibility(
            name = "Liquid Gallery",
            packageName = "com.soepic.photogallery.release",
            appIconColor = 0x6750A4,
            targets = listOf(AppTarget("2.0.14"), AppTarget("2.1.11")),
        ),
    )

    execute {
        // PairIP's DEX-level Play-licensing check (LicenseClient). On a sideloaded build it gets
        // NOT_LICENSED and schedules a shutdown + Play paywall, so the app exits before any UI.
        // PairIP keeps these names unobfuscated, but the class has two public no-arg void methods,
        // so pin by name + shape. (No native libpairipcore.so here, unlike pairip-VM apps.)
        val licenseClass = classDefByStrings("com.android.vending.licensing.ILicensingService")
            .firstOrNull()
            ?: throw PatchException(
                "Liquid Gallery: PairIP LicenseClient (ILicensingService) not found.",
            )
        val mutableLicenseClass = mutableClassDefBy(licenseClass)

        fun noOp(methodName: String, predicate: (Method) -> Boolean) {
            val method = mutableLicenseClass.methods.firstOrNull {
                it.name == methodName && predicate(it)
            } ?: throw PatchException(
                "Liquid Gallery: PairIP LicenseClient.$methodName() not found — " +
                    "license-check layout changed.",
            )
            method.addInstructions(0, "return-void")
        }

        // initializeLicenseCheck() is the entry point; startPaywallActivity() is the failsafe.
        noOp("initializeLicenseCheck") { it.returnType == "V" && it.parameterTypes.isEmpty() }
        noOp("startPaywallActivity") { it.returnType == "V" && it.parameterTypes.size == 1 }
    }
}
