package hooman.morphe.patches.carlauncher.full

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val unlockFullPatch = bytecodePatch(
    name = "Unlock Full Version",
    description = "Unlocks the paid full version of AGAMA Car Launcher, so the pro widgets, theme " +
        "editor, and the other locked extras open up without buying it.",
) {
    compatibleWith(
        Compatibility(
            name = "AGAMA Car Launcher",
            packageName = "altergames.carlauncher",
            appIconColor = 0xF88828,
            targets = listOf(AppTarget("5.0.5")),
        ),
    )

    // The launcher re-checks the signing cert separately from the full/trial flag and turns the
    // shortcut icons into skulls on a re-signed build. Bundle the bypass so it can't be left off.
    dependsOn(disablePiracyDetectionPatch)

    execute {
        // isFull() is the one full/trial chokepoint: it compares a stored "FULL" pref against
        // fullCod(), and the billing flow only ever writes that pref, never reads it back. The TRIAL
        // flag and SettingsActivity (via the isTrial extra) both follow from it, so forcing it true
        // unlocks everything and stays unlocked, since the pref is read live each launch.
        val mainActivity = mutableClassDefByOrNull("Laltergames/carlauncher/MainActivity;")
            ?: throw PatchException(
                "Car Launcher: MainActivity not found — package layout changed.",
            )
        val isFull = mainActivity.methods.firstOrNull { method ->
            method.name == "isFull" &&
                method.returnType == "Z" &&
                method.parameterTypes.isEmpty()
        }
            ?: throw PatchException(
                "Car Launcher: MainActivity.isFull()Z not found — the full-version gate shape changed.",
            )
        isFull.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
