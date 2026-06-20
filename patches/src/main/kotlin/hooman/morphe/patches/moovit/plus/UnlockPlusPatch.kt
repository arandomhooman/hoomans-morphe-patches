package hooman.morphe.patches.moovit.plus

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.moovit.maps.useMapsApiKeyPatch

@Suppress("unused")
val unlockPlusPatch = bytecodePatch(
    name = "Unlock Moovit+",
    description = "Unlocks the Moovit+ extras without paying, like the extra sort and time-of-travel " +
        "options and compare-on-map. Things Moovit runs on its servers, like transit ticketing, still " +
        "need the real subscription. Pair this with Remove ads for the ad-free part of Moovit+.",
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
        // Force the subscription check true so every Moovit+ feature package reads "subscribed" and
        // flips to active. The subscribed-skus set behind it is filled by a server/billing round-trip,
        // so this only opens the client-gated features, not anything the backend re-validates.
        SubscriptionStateFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
