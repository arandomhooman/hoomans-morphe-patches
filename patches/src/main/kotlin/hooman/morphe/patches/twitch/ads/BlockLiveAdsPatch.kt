package hooman.morphe.patches.twitch.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val blockLiveAdsPatch = bytecodePatch(
    name = "Block live ads",
    description = "Routes live streams through a free third-party proxy that strips the ads baked " +
        "into the stream, so the proxy has to be up for playback to work; turn this patch off if it " +
        "goes down. Mid-stream ads get dropped reliably, but a single preroll can still slip through " +
        "when you first open a channel and Twitch has flagged the shared proxy. VOD ads aren't covered.",
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
        // The live HLS URL is built in one lambda; its second instance field (b) holds the stream name.
        // Replace the whole body to return the equivalent Luminous proxy URL, so the player loads the
        // ad-free manifest the proxy serves. The proxy ignores token/sig, so the two lambda args go
        // unused. Read the field off the matched class, not a hardcoded name, to stay off the obfuscated name.
        val method = LiveManifestUrlBuilderFingerprint.method
        val streamNameField = "${method.definingClass}->b:Ljava/lang/String;"

        method.addInstructions(
            0,
            """
                new-instance v0, Ljava/lang/StringBuilder;
                invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
                const-string v1, "https://eu.luminous.dev/playlist/"
                invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                iget-object v1, p0, $streamNameField
                invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                const-string v1, ".m3u8%3Fallow_source%3Dtrue%26allow_audio_only%3Dtrue%26fast_bread%3Dtrue"
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
