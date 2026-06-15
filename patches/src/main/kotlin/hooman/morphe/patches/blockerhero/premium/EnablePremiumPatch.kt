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
    description = "Unlocks BlockerHero's premium features without a subscription or Google " +
        "sign-in: uninstall protection, focus mode, custom blocklists, daily and weekly time " +
        "limits, block-on-restart, and blocking the recent-apps screen.",
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
        // "com.blockerhero.KEY_IS_PREMIUM" (assembled by g()) and read through this getter
        // as e(g(), false). Forcing the *read* — rather than the stored value — to return
        // true is immune to any Play Billing re-sync that would write the stored flag back
        // to false. This flips the "Premium" badge and the few sites that call e(g())
        // *live* (e.g. focus mode's "one timer in free version" check). It is NOT enough on
        // its own: the blocklist/app caps read a *cached* PreferencesState.isPremium, forced
        // separately below.
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

        // The actual feature gates (keyword/app blocklist 10-item cap, focus mode, the
        // Premium screen) read the cached `isPremium` field off the PreferencesState
        // snapshot, NOT a live e() call — and that cache is populated from the stored pref,
        // which is false on a fresh/billing-less install, so the e() hook above leaves the
        // gates closed. Force the state's isPremium field on at its constructor so every
        // snapshot reports premium. The 2nd ctor param (p2, first boolean) is isPremium.
        PrefsStateConstructorFingerprint.method.addInstructions(
            0,
            "const/4 p2, 0x1",
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

        // With premium + login forced on, premium toggles still try to sync to the server,
        // which 401s (no real token) and shows an "Unauthenticated." toast every time. The
        // toast helper Q(Context, message) is shared app-wide, so rather than disable it
        // wholesale, skip it only for auth-error messages — every other toast still shows.
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
