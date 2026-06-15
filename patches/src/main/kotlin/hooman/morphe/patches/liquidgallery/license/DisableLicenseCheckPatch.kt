package hooman.morphe.patches.liquidgallery.license

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.Method

@Suppress("unused")
// Internal patch (no name): not user-selectable on its own. It is pulled in automatically as a
// dependency of Unlock Pro, so the license bypass is always applied with the unlock.
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
        // PairIP's standard Play-licensing check (com.pairip.licensecheck.LicenseClient). On a
        // sideloaded build it gets NOT_LICENSED (or, on a Play-less device, no account) and PairIP
        // schedules a "graceful shutdown" + Play paywall, so the app exits before any UI. This is
        // the DEX-level, bypassable variant (no native libpairipcore.so VM here), same as the
        // Teach Me Anatomy patch.
        //
        // The class is pinned by the unique licensing-service interface string. PairIP keeps these
        // method names unobfuscated; pin by name + shape (this class has TWO public no-arg void
        // methods — connectToLicensingService and initializeLicenseCheck — so an accessFlags/shape
        // fingerprint alone would be ambiguous). Fail loudly if the layout changed.
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

        // Entry point — LicenseContentProvider.onCreate() does
        //   new LicenseClient(ctx).initializeLicenseCheck();
        // No-opping it skips the whole check; nothing in the app reads the license result.
        noOp("initializeLicenseCheck") { it.returnType == "V" && it.parameterTypes.isEmpty() }
        // Failsafe — the paywall + shutdown launcher, in case any path reaches it.
        noOp("startPaywallActivity") { it.returnType == "V" && it.parameterTypes.size == 1 }
    }
}
