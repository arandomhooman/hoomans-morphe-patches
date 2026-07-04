package hooman.morphe.patches.sofascore.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove Ads",
    description = "Stops the banner and interstitial ads SofaScore shows around scores and between " +
        "screens. Every ad site reads one decision method off the cached account, and the app's own " +
        "ad master switch caches that method's result, so forcing it to \"no ads\" turns them all off " +
        "with no login needed. The result holds across a sync because the switch re-reads the same " +
        "method. Ads that are served by the video/story SDK inside embedded content are not affected. " +
        "Re-signing breaks SofaScore's login (it only offers Google and Facebook sign-in, both of which " +
        "reject the new signature), but removing ads does not need an account so the patch still works.",
) {
    compatibleWith(
        Compatibility(
            name = "SofaScore",
            packageName = "com.sofascore.results",
            // SofaScore's launcher-icon blue (ic_launcher_background #374DF5).
            appIconColor = 0x374DF5,
            targets = listOf(AppTarget("26.06.23")),
        ),
    )

    execute {
        // Force the shouldShowAds derivation to return false at method entry. This is the single point
        // every ad consumer and the Application-level ad master switch reads through, and it is a pure
        // recompute (no cached snapshot beats it), so the suppression survives login, logout and sync.
        ShouldShowAdsFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )
    }
}
