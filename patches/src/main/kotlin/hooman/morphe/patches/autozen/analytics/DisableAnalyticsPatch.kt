package hooman.morphe.patches.autozen.analytics

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.autozen.checks.disableChecksPatch

@Suppress("unused")
val disableAnalyticsPatch = bytecodePatch(
    name = "Disable analytics",
    description = "Stops AutoZen from sending its own usage analytics. All app events, screen views, " +
        "and user properties flow through one tracker list; this empties it so nothing is reported.",
) {
    dependsOn(disableChecksPatch)

    compatibleWith(
        Compatibility(
            name = "AutoZen",
            packageName = "com.zenthek.autozen",
            appIconColor = 0x1B1B1B,
            targets = listOf(AppTarget("8.0.10")),
        ),
    )

    execute {
        // AppTrackerImpl fans every trackEvent/trackScreen/setUserProperty out over getListOfTrackers()
        // (Firebase + Sentry). Return an empty list so the facade has nothing to forward to. App packages
        // are not obfuscated, so pin by descriptor.
        val tracker = mutableClassDefByOrNull(
            "Lcom/zenthek/autozen/tracking/AppTrackerImpl;",
        ) ?: throw PatchException(
            "AutoZen: AppTrackerImpl not found. The analytics facade changed.",
        )
        val listOfTrackers = tracker.methods.firstOrNull { method ->
            method.name == "getListOfTrackers" &&
                method.returnType == "Ljava/util/List;" &&
                method.parameterTypes.isEmpty()
        } ?: throw PatchException(
            "AutoZen: AppTrackerImpl.getListOfTrackers() not found. Re-derive.",
        )
        listOfTrackers.addInstructions(
            0,
            """
                invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
                move-result-object v0
                return-object v0
            """,
        )
    }
}
