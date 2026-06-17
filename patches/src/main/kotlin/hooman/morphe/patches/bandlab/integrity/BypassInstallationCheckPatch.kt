package hooman.morphe.patches.bandlab.integrity

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

// Internal (no name): applied automatically as a dependency of Unlock Membership.
@Suppress("unused")
val bypassInstallationCheckPatch = bytecodePatch(
    description = "Stops BandLab's repackaging check from blocking the re-signed build. " +
        "ApkInstallationChecker.isInstallationCorrect checks the signing certificate's SHA-256 " +
        "against a baked-in set, and the base activity shows a \"BandLab was installed incorrectly\" " +
        "dialog and stops if it fails. Re-signing changes the certificate, so the check fails on " +
        "every patched build. Forcing it to report a correct install lets the app start normally.",
) {
    compatibleWith(
        Compatibility(
            name = "BandLab",
            packageName = "com.bandlab.bandlab",
            appIconColor = 0xF02010,
            targets = listOf(AppTarget("11.25.3")),
        ),
    )

    execute {
        // The checker class and method keep their real names (the installation-checker module is not
        // obfuscated). isInstallationCorrect is the single gate every activity calls in onCreate; the
        // cached b/c statics are read only inside it, so returning true at the top is enough.
        val checker = mutableClassDefByOrNull("Lcom/bandlab/installation/checker/ApkInstallationChecker;")
            ?: throw PatchException(
                "BandLab: ApkInstallationChecker not found. The install-check layout changed.",
            )
        val isInstallationCorrect = checker.methods.singleOrNull { method ->
            method.name == "isInstallationCorrect" &&
                method.returnType == "Z" &&
                method.parameterTypes.singleOrNull()?.toString() == "Landroid/content/Context;"
        } ?: throw PatchException(
            "BandLab: isInstallationCorrect(Context)Z not found in ApkInstallationChecker.",
        )
        isInstallationCorrect.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
    }
}
