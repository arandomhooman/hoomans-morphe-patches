package hooman.morphe.patches.twitch.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideDisplayAdsPatch = bytecodePatch(
    name = "Hide display ads",
    description = "Hides the banner, overlay, and in-feed display ads Twitch shows around the app. " +
        "This doesn't touch the video ads in the stream itself.",
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
        // Return the no-ad singleton (Lgq; = nq.NoAd in this build) from the response parser so every
        // display/banner/in-feed unit resolves to "nothing to show". The 204 branch already returns
        // this exact value, so it's a path the downstream code handles cleanly. gq is a sibling of the
        // matched class and version-pinned along with it.
        DisplayAdResponseParserFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Lgq;->a:Lgq;
                return-object v0
            """,
        )
    }
}
