package hooman.morphe.patches.teachmeanatomy.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import hooman.morphe.patches.teachmeanatomy.license.disableLicenseCheckPatch

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks Teach Me Anatomy's Pro features without a subscription: no upgrade " +
        "banners or ads, plus the gated reference articles, quizzes, and flashcards. Content " +
        "you have already synced works offline. Features served from the server still need a " +
        "real account.",
) {
    // The PairIP license check kills any sideloaded build, so it must run whenever Pro is
    // unlocked. Pull it in as an internal dependency instead of a separate user-facing patch.
    dependsOn(disableLicenseCheckPatch)

    compatibleWith(
        Compatibility(
            name = "Teach Me Anatomy",
            packageName = "com.atomengineapps.teachmeanatomy",
            appIconColor = 0x00A99D,
            targets = listOf(AppTarget("5.115")),
        ),
    )

    execute {
        // Premium is a single local SharedPreferences boolean "isProAccount" in the
        // "app_preference" file. There is no read wrapper: ~25 gate sites read it directly via
        // appPreferences.getBoolean("isProAccount", false). The one shared write path is the
        // prefs manager's d(String, boolean) setter, so force it to always store true for that
        // key — then no purchase re-check, login response or logout can flip it back to false.
        val setter = PrefsPutBooleanFingerprint.method
        setter.addInstructionsWithLabels(
            0,
            """
                const-string v0, "isProAccount"
                invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :original
                const/4 p2, 0x1
            """,
            ExternalLabel("original", setter.getInstruction(0)),
        )

        // Forcing the setter only helps once isProAccount is written at least once. A guest who
        // never logs in never triggers that write, so seed the flag true in the prefs manager's
        // constructor — right before it returns, after the SharedPreferences field is assigned.
        // Routed through the (now-forced) setter on the same instance, so it stores true.
        val constructor = PrefsConstructorFingerprint.method
        constructor.addInstructions(
            constructor.instructions.lastIndex,
            """
                const-string v0, "isProAccount"
                const/4 v1, 0x1
                invoke-virtual {p0, v0, v1}, ${constructor.definingClass}->d(Ljava/lang/String;Z)V
            """,
        )
    }
}
