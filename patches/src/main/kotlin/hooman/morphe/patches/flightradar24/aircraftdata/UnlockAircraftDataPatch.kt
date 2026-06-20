package hooman.morphe.patches.flightradar24.aircraftdata

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val unlockAircraftDataPatch = bytecodePatch(
    name = "Unlock aircraft data",
    description = "Shows the squawk code and vertical speed in the flight detail panel instead of " +
        "the \"Unlock feature\" lock. Both values are already in the live feed the app receives, so " +
        "this only stops hiding them. The Gold map layers, flight history, and 3D view come from " +
        "Flightradar24's servers and still need a subscription.",
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
        // The detail panel and the playback panel both read UserFeatures.getMapInfoAircraft(): "full"
        // shows the squawk and vertical-speed rows, anything else swaps them for a tap-to-unlock lock.
        // The data is already parsed out of the per-aircraft feed message (FlightData.squawk /
        // verticalSpeed) for every flight regardless of tier, so the only thing the flag changes is
        // whether those rows render. Force the getter to report "full". UserFeatures is a Gson model,
        // so the class descriptor and getter name survive R8.
        val userFeatures = mutableClassDefByOrNull("Lcom/flightradar24free/models/account/UserFeatures;")
            ?: throw PatchException(
                "Flightradar24: UserFeatures not found. The account model package changed.",
            )

        val getMapInfoAircraft = userFeatures.methods.firstOrNull {
            it.name == "getMapInfoAircraft" &&
                it.returnType == "Ljava/lang/String;" &&
                it.parameterTypes.isEmpty()
        }
            ?: throw PatchException(
                "Flightradar24: UserFeatures.getMapInfoAircraft()Ljava/lang/String; not found. " +
                    "The aircraft-info gate shape changed.",
            )

        getMapInfoAircraft.addInstructions(
            0,
            """
                const-string v0, "full"
                return-object v0
            """,
        )
    }
}
