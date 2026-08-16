package hooman.morphe.patches.swiftkey

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val swiftKeyCompatibility = Compatibility(
    name = "Microsoft SwiftKey",
    packageName = "com.touchtype.swiftkey",
    appIconColor = 0x00A4EF,
    targets = listOf(AppTarget("9.13.13.5")),
)
