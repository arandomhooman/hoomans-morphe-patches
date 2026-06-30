package hooman.morphe.patches.symfonium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Turns on Symfonium's paid features. The build ships as a public beta with a built-in " +
        "trial: a license value the app keeps locally decides whether the paid UI is open, and two " +
        "timers (the beta cutoff and the trial countdown) lock the app once they pass. This forces the " +
        "license read to \"licensed\" so the paid screens open, drops the \"trial expires on ...\" line " +
        "from settings, and stops the cutoff from triggering by cutting off the trusted-time lookup the " +
        "timers rely on, which leaves the app in its offline state where neither timer fires. The " +
        "license is checked on the client (a local Play purchase verify), so the unlock holds without " +
        "an account. Connecting Plex, Jellyfin and other media servers is untouched.",
) {
    compatibleWith(
        Compatibility(
            name = "Symfonium",
            packageName = "app.symfonik.music.player",
            // The red Symfonium "S".
            appIconColor = 0xE53935,
            targets = listOf(AppTarget("14.1.0")),
        ),
    )

    execute {
        // The license manager (nh3). Pinned through the status method that carries the unique "Probably
        // soon" string; its defining class holds both the premium gate and the trial-status builder.
        val licenseManager = LicenseStatusFingerprint.classDef

        // The premium gate. Features read this boolean at ~25 sites; it returns true when the manager's
        // license value equals the licensed sentinel (42). It is the only ()Z on the manager, and the
        // only one comparing against 42, so match on that literal. Force it true and every gated screen
        // and setting opens regardless of the live license value.
        val gate = licenseManager.methods.filter { method ->
            method.returnType == "Z" &&
                method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.any { instruction ->
                    (instruction as? WideLiteralInstruction)?.wideLiteral == 42L
                } == true
        }
        if (gate.size != 1) {
            throw PatchException(
                "Symfonium: expected exactly 1 license-gate ()Z method (compares the license value to " +
                    "42) on the manager, found ${gate.size}. The gate shape changed; re-derive.",
            )
        }
        gate.single().addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )

        // The settings trial-status builder (nh3.r). It returns the formatted "trial expires on ..."
        // string, or null when the account is licensed. Return null so the trial row is hidden and the
        // app presents as licensed.
        LicenseStatusFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """,
        )

        // The SNTP fetch (er2.d). It is the sole source of the trusted wall-clock time that the beta
        // cutoff and the trial countdown compare against, and its one caller swallows any failure and
        // leaves the app on its "no trusted time" path, which never reports the build as expired. Make
        // the fetch throw so that path is permanent; the timers can no longer fire.
        NetworkTimeFingerprint.method.addInstructions(
            0,
            """
                new-instance v0, Ljava/lang/RuntimeException;
                invoke-direct {v0}, Ljava/lang/RuntimeException;-><init>()V
                throw v0
            """,
        )
    }
}
