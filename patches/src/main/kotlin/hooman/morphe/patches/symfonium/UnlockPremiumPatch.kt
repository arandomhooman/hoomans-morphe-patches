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
        "trial, so this also prevents the expired-beta screen. The license is checked locally and the " +
        "unlock does not require an account. Connecting Plex, " +
        "Jellyfin and other media servers is untouched.",
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
        // soon" string; its defining class holds both the premium gate and the beta-expiry status.
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

        // Native startup also writes a beta status into the license manager. Zero is the normal
        // status; a nonzero value can replace the requested screen with ExpiredBeta or reject an
        // otherwise ready provider. Clear only that live status read.
        BetaExpiryStatusFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )
    }
}
