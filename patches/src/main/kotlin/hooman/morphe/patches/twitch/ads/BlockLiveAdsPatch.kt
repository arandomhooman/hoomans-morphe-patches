package hooman.morphe.patches.twitch.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val blockLiveAdsPatch = bytecodePatch(
    name = "Block live ads",
    description = "Removes the ads stitched into live streams (SureStream) by routing the HLS " +
        "manifest request through the Luminous ad-block proxy (eu.luminous.dev) instead of Twitch's " +
        "own usher server. The proxy fetches a clean manifest, so the stream comes back without the " +
        "server-inserted ad segments. It relies on that third-party proxy staying up; if it goes " +
        "down, live streams stop loading until you remove the patch. This covers the stitched " +
        "live-stream ads only; VOD ads are not touched.",
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
