package hooman.morphe.patches.tumblr.annoyances.adfree

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.tumblr.featureflags.addFeatureFlagOverride
import hooman.morphe.patches.tumblr.featureflags.overrideFeatureFlagsPatch

@Suppress("unused")
val disableAdFreeBannerPatch = bytecodePatch(
    name = "Disable ad-free banner",
    description = "Hides the banner with the frog that prompts you to buy Tumblr Ad-Free.",
) {
    dependsOn(overrideFeatureFlagsPatch)

    compatibleWith(
        Compatibility(
            name = "Tumblr",
            packageName = "com.tumblr",
            appIconColor = 0x001935,
            targets = listOf(AppTarget("45.0.0.109")),
        ),
    )

    execute {
        // AD_FREE_CTA_BANNER ("Whether or not to show ad free prompt").
        addFeatureFlagOverride("adFreeCtaBanner", "false")
    }
}
