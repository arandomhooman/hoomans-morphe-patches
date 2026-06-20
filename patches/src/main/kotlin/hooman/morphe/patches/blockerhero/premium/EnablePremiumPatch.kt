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
    description = "Unlocks BlockerHero's premium features without a subscription or Google sign-in, " +
        "like uninstall protection, focus mode, custom blocklists, daily and weekly time limits, " +
        "and more.",
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
        // Force the live premium getter e(key, default) true for KEY_IS_PREMIUM. Hooking the read
        // rather than the stored value survives any Play Billing re-sync, but isn't enough alone.
        val method = PrefsGetBooleanFingerprint.method
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

        // The feature gates read the cached isPremium off the PreferencesState snapshot, not the
        // getter above (that cache comes from the stored pref, false on a fresh install). Force it
        // on at the constructor: p2, the first boolean param, is isPremium.
        PrefsStateConstructorFingerprint.method.addInstructions(
            0,
            "const/4 p2, 0x1",
        )

        // Second gate: a Google login (l() = userId > 0) the re-signed app can't satisfy. Force it
        // true so the unlocked local features are usable.
        IsLoggedInFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )

        // Premium toggles still sync and 401, spamming an "Unauthenticated" toast. The toast helper
        // is shared, so suppress only auth-error messages and leave every other toast alone.
        val toast = ShowToastFingerprint.method
        toast.addInstructionsWithLabels(
            0,
            """
                if-eqz p1, :show
                const-string v0, "uthenticat"
                invoke-virtual {p1, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v0
                if-eqz v0, :show
                return-void
            """,
            ExternalLabel("show", toast.getInstruction(0)),
        )
    }
}
