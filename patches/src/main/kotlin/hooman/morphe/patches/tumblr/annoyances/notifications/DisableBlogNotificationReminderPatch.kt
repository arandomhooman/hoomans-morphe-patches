package hooman.morphe.patches.tumblr.annoyances.notifications

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

// Ported from ReVanced's Tumblr patches.
@Suppress("unused")
val disableBlogNotificationReminderPatch = bytecodePatch(
    name = "Disable blog notification reminder",
    description = "Stops the reminder asking you to enable notifications for blogs you visit.",
) {
    compatibleWith(
        Compatibility(
            name = "Tumblr",
            packageName = "com.tumblr",
            appIconColor = 0x001935,
            targets = listOf(AppTarget("45.0.0.109")),
        ),
    )

    execute {
        // Force isEnabled() false so the BlogNotifyCtaDialog never qualifies to show.
        IsBlogNotifyEnabledFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )
    }
}
