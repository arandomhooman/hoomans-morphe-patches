package hooman.morphe.patches.tumblr.annoyances.screenshot

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.tumblr.featureflags.addFeatureFlagOverride
import hooman.morphe.patches.tumblr.featureflags.overrideFeatureFlagsPatch

@Suppress("unused")
val disableScreenshotSharePatch = bytecodePatch(
    name = "Disable screenshot sharing",
    description = "Stops the share sheet that pops up when you take a screenshot in the app.",
) {
    dependsOn(overrideFeatureFlagsPatch)

    compatibleWith(
        Compatibility(
            name = "Tumblr",
            packageName = "com.tumblr",
            appIconColor = 0x001935,
            targets = listOf(AppTarget("45.8.0.110")),
        ),
    )

    execute {
        // SCREENSHOT_INTERCEPTION ("Show share sheet when user takes a screenshot"). The flag is read
        // both when the screenshot detectors are registered and again inside the on-capture listener
        // before the share sheet is shown, so forcing it false suppresses the popup for both the API 34+
        // ScreenCaptureCallback path and the MediaStore observer path.
        addFeatureFlagOverride("screenshotInterception", "false")
    }
}
