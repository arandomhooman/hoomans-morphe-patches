package hooman.morphe.patches.twitch.chat

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val showDeletedMessagesPatch = bytecodePatch(
    name = "Show deleted messages",
    description = "Keeps deleted or moderated chat messages readable instead of replacing them with " +
        "\"<message deleted>\". The message turns into a tappable spoiler: tap it to reveal the original " +
        "text. Normally only moderators can do this; the patch grants that access to everyone.",
) {
    compatibleWith(
        Compatibility(
            name = "Twitch",
            packageName = "tv.twitch.android.app",
            appIconColor = 0x9147FF,
            targets = listOf(AppTarget("29.9.1")),
        ),
    )

    execute {
        // The deleted-message span gates its reveal on a boolean "has mod access" field (field c here),
        // set from the constructor's third arg. Force it true at the end of the constructor so the span
        // behaves as if the viewer is a mod: the message stays tappable and tapping reveals the original.
        val method = DeletedMessageSpanCtorFingerprint.method
        val definingClass = DeletedMessageSpanCtorFingerprint.classDef.type
        // The constructor has no spare locals, so reuse p3 (the now-stored boolean arg) as scratch
        // instead of a v-register the patcher would have to remap onto p0 (this).
        method.addInstructions(
            method.implementation!!.instructions.lastIndex,
            """
                const/4 p3, 0x1
                iput-boolean p3, p0, $definingClass->c:Z
            """,
        )
    }
}
