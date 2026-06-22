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
    description = "Unlocks Teach Me Anatomy's premium features without a subscription, dropping the " +
        "upgrade banners and ads and opening up the locked articles, quizzes, and flashcards. Content " +
        "served from the server still needs a real account.",
) {
    // A sideloaded build can't launch with the license check active, so bundle it as a dependency.
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
        // Premium is the local pref "isProAccount" (app_preference). There's no read wrapper, so
        // force the one shared writer d(String, boolean) to always store true for that key, so no
        // purchase re-check, login response or logout can flip it back.
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

        // Seed it for a guest who never logs in (and so never triggers a write): call the now-forced
        // setter at the end of the prefs-manager constructor, after the field is assigned.
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
