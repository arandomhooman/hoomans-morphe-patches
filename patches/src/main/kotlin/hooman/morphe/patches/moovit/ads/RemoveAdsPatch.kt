package hooman.morphe.patches.moovit.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.moovit.maps.useMapsApiKeyPatch

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Removes the ads Moovit shows around the map and search and between screens.",
) {
    // Re-signing invalidates Moovit's bundled Maps key, so require a user-supplied one. The dependency
    // refuses to apply with a blank key, so a patched build can't end up with a dead map.
    dependsOn(useMapsApiKeyPatch)

    compatibleWith(
        Compatibility(
            name = "Moovit",
            packageName = "com.tranzmate",
            appIconColor = 0xFF6400,
            targets = listOf(AppTarget("5.194.0.1785")),
        ),
    )

    execute {
        // Return "" from the ad-unit resolver so every banner/interstitial/preload path sees "no ad
        // unit" and loads nothing. This is the one chokepoint they all funnel through.
        AdUnitResolverFingerprint.method.addInstructions(
            0,
            """
                const-string v0, ""
                return-object v0
            """,
        )
    }
}
