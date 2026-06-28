package hooman.morphe.patches.tumblr.annoyances.inappupdate

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.tumblr.featureflags.addFeatureFlagOverride
import hooman.morphe.patches.tumblr.featureflags.overrideFeatureFlagsPatch

@Suppress("unused")
val disableInAppUpdatePatch = bytecodePatch(
    name = "Disable in-app update",
    description = "Turns off the in-app update check and the update prompt.",
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
        // IN_APP_UPDATE ("Enables In-App updates"): checked before the Play core AppUpdateManager call.
        addFeatureFlagOverride("inAppUpdate", "false")
    }
}
