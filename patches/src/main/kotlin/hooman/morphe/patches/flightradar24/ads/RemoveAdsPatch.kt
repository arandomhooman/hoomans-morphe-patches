package hooman.morphe.patches.flightradar24.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Removes the ads in Flightradar24. The Gold map layers, flight history, and 3D " +
        "view are produced on Flightradar24's servers and still need a subscription.",
) {
    compatibleWith(
        Compatibility(
            name = "Flightradar24",
            packageName = "com.flightradar24free",
            appIconColor = 0x327CB5,
            targets = listOf(AppTarget("11.6.1")),
        ),
    )

    execute {
        // Every banner/interstitial decision funnels through this one accessor: it returns true when
        // ads should show. Force it false so no ad slot ever loads, including for a logged-out or
        // anonymous user (the unpatched method returns true when not logged in). The flag is recomputed
        // from the cached user on every call, so nothing here is reset by a login or a refresh.
        AdEnabledFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )
    }
}
