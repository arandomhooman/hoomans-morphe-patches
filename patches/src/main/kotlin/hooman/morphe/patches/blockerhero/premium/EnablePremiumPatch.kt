package hooman.morphe.patches.blockerhero.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

@Suppress("unused")
val enablePremiumPatch = bytecodePatch(
    name = "Enable Premium",
    description = "Unlocks the premium features (uninstall protection, focus mode, custom " +
        "blocklists, daily/weekly time limits, block-on-restart, block recent-apps screen, " +
        "etc.) without a subscription or Google login.",
) {
    compatibleWith(
        Compatibility(
            name = "BlockerHero",
            packageName = "com.blockerhero",
            appIconColor = 0x2D6CDF,
            targets = listOf(AppTarget("1.5.0")),
        ),
    )

    execute {
        val method = PrefsGetBooleanFingerprint.method

        // Premium is a local SharedPreferences boolean stored under the key
        // "com.blockerhero.KEY_IS_PREMIUM" (assembled by g()) and read everywhere through
        // this single getter as e(g(), false). Forcing the *read* — rather than the stored
        // value — to return true unlocks every premium gate at once and is immune to any
        // Play Billing re-sync that would otherwise write the stored flag back to false.
        method.addInstructionsWithLabels(
            0,
            """
                const-string v0, "com.blockerhero.KEY_IS_PREMIUM"
                invoke-virtual {p1, v0}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :original
                const/4 v0, 0x1
                return v0
            """,
            ExternalLabel("original", method.getInstruction(0)),
        )

        // Premium features are gated a *second* time behind a Google login. The prefs
        // manager's isLoggedIn check `l()` returns `userId > 0`; forcing it true bypasses
        // the "Login required." prompt (which the re-signed app could never satisfy via
        // Google sign-in anyway) so the unlocked local features are actually usable.
        IsLoggedInFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
