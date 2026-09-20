package hooman.morphe.patches.twitch.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val blockLiveAdsPatch = bytecodePatch(
    name = "Block live ads",
    description = "Routes live streams through a free third-party manifest proxy in an ad-free " +
        "region. Playback depends on the proxy, and the shared exit can still occasionally receive " +
        "ads. VOD ads aren't covered.",
) {
    compatibleWith(
        Compatibility(
            name = "Twitch",
            packageName = "tv.twitch.android.app",
            appIconColor = 0x9147FF,
            targets = listOf(AppTarget("30.7.2")),
        ),
    )

    execute {
        // The live HLS URL is built in one lambda; its second instance field (b) holds the stream name.
        // Replace the whole body with PerfProd's live manifest URL. The proxy obtains its own token,
        // so the two lambda args go unused. Resolve the field from the matched class to survive R8 names.
        val method = LiveManifestUrlBuilderFingerprint.method
        val streamNameField = "${method.definingClass}->b:Ljava/lang/String;"

        method.addInstructions(
            0,
            """
                new-instance v0, Ljava/lang/StringBuilder;
                invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
                const-string v1, "https://lb-as.cdn-perfprod.com/live/"
                invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                iget-object v1, p0, $streamNameField
                invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                const-string v1, "?allow_source=true&allow_audio_only=true&fast_bread=true&type=any&player=twitchweb"
                invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                move-result-object v0
                invoke-static {v0}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;
                move-result-object v0
                return-object v0
            """,
        )
    }
}
