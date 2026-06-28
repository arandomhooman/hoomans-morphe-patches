package hooman.morphe.patches.tumblr.annoyances.tv

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.tumblr.featureflags.addFeatureFlagOverride
import hooman.morphe.patches.tumblr.featureflags.overrideFeatureFlagsPatch

@Suppress("unused")
val disableTumblrTvPatch = bytecodePatch(
    name = "Disable Tumblr TV",
    description = "Removes the Tumblr TV tab from the bottom navigation bar.",
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
        // TUMBLR_TV_MOBILE_NAV ("TumblrTV tab appears on the bottom nav bar").
        addFeatureFlagOverride("tumblrTvMobileNav", "false")
    }
}
