package hooman.morphe.patches.dialer.callrecording

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val enableCallRecordingPatch = bytecodePatch(
    name = "Enable call recording",
    description = "Turns on the built-in call recorder in regions where Google normally hides it. " +
        "Recordings stay on your phone. Recording calls is regulated in many places, so check what is " +
        "allowed where you live before using it.",
) {
    compatibleWith(
        Compatibility(
            name = "Google Phone",
            packageName = "com.google.android.dialer",
            appIconColor = 0x1A73E8,
            targets = listOf(
                AppTarget("161.0.726587057"),
                AppTarget("161.0.726587057-downloadable"),
            ),
        ),
    )

    execute {
        // Google gates the in-call recorder on a client-side country allowlist. The CanRecord helper
        // resolves that to one boolean the record button reads; force it true so recording is always
        // offered. The recorder is fully on-device (recordings never leave the phone), so nothing
        // server-side needs to change. R8 renames the class; the CanRecord class tag is shared with a
        // sibling, so pin by the log line unique to the country gate, then force its single no-arg
        // boolean availability method true.
        val canRecord = classDefByStrings(
            "Call recording is disabled in the current country",
        ).singleOrNull()
            ?: throw PatchException(
                "Google Phone: CanRecord class not found or ambiguous. The call-recording gate changed.",
            )
        val mutableCanRecord = mutableClassDefBy(canRecord)

        val availability = mutableCanRecord.methods.filter { method ->
            method.returnType == "Z" && method.parameterTypes.isEmpty()
        }
        if (availability.size != 1) {
            throw PatchException(
                "Google Phone: expected one no-arg boolean availability method on CanRecord, found " +
                    "${availability.size}. Re-derive.",
            )
        }
        availability.single().addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
