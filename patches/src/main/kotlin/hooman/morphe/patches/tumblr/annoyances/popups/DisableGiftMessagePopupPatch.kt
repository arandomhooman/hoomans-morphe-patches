package hooman.morphe.patches.tumblr.annoyances.popups

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

// Ported from ReVanced's Tumblr patches.
@Suppress("unused")
val disableGiftMessagePopupPatch = bytecodePatch(
    name = "Disable gift message popup",
    description = "Stops the popup that suggests buying TumblrMart items for other people.",
) {
    compatibleWith(
        Compatibility(
            name = "Tumblr",
            packageName = "com.tumblr",
            appIconColor = 0x001935,
            targets = listOf(AppTarget("45.0.0.109")),
        ),
    )

    execute {
        // Return before the popup is built and shown. The method is void, so an early return-void is
        // enough to suppress it.
        ShowGiftMessagePopupFingerprint.method.addInstructions(0, "return-void")
    }
}
