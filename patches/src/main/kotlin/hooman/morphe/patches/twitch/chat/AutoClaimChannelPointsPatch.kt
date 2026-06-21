package hooman.morphe.patches.twitch.chat

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

@Suppress("unused")
val autoClaimChannelPointsPatch = bytecodePatch(
    name = "Auto claim channel points",
    description = "Claims the channel points bonus chest for you automatically. Whenever Twitch makes " +
        "a bonus available, the claim fires on its own so you don't have to tap the chest.",
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
        // The state provider rebuilds the button's view state on every update. When the incoming state
        // (p1) carries an active claim (field d), a bonus is waiting. Fire the claim through the data
        // provider it already holds (field m, sk8.H(claimId, ChatModeMetadata)) before building the
        // state, passing the claim id (ActiveClaimModel.a) and null metadata. A null claim means no
        // bonus is up, so we skip. Re-claiming a spent bonus is a harmless server no-op, so this is safe
        // even though the method runs on every state update.
        val method = CommunityPointsStateProviderFingerprint.method
        method.addInstructionsWithLabels(
            0,
            """
                iget-object v0, p1, Lxg8;->d:Lnb;
                if-eqz v0, :skip
                iget-object v1, v0, Lnb;->a:Ljava/lang/String;
                iget-object v2, p0, Lkh8;->m:Lsk8;
                const/4 v3, 0x0
                invoke-interface {v2, v1, v3}, Lsk8;->H(Ljava/lang/String;Ltv/twitch/android/shared/one/chat/pub/ChatModeMetadata;)V
            """,
            ExternalLabel("skip", method.getInstruction(0)),
        )
    }
}
