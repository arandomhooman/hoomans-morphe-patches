package hooman.morphe.patches.twitch.login

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val fixLoginPatch = bytecodePatch(
    name = "Fix login",
    description = "Fixes the \"This app version/OS is not currently supported\" error that blocks login " +
        "after the app has been patched. Twitch runs a Play Integrity check in the background and reports " +
        "the result to its login server; re-signing makes that check fail, so the server refuses the " +
        "login. This stops the app from sending that attestation, so it looks like a device without " +
        "Google Play, where login works normally. It does not change anything else about how you log in.",
) {
    compatibleWith(
        Compatibility(
            name = "Twitch",
            packageName = "tv.twitch.android.app",
            appIconColor = 0x9147FF,
            targets = listOf(AppTarget("30.7.2")),
        ),
    )

    execute {
        // The Play Integrity attestation flow lives in one dedicated class. Its orchestrator method runs
        // setup, requests a StandardIntegrity token, and POSTs it to /api/v1/android/attestation, which is
        // the only channel that tells the server the APK was re-signed. Login itself sends credentials
        // only, so suppressing the attestation makes a re-signed build indistinguishable from a device
        // without Google Play (which never attests and logs in fine).
        //
        // Pin the class by the analytics string it logs when setup begins (unique to this class), then
        // no-op the method that logs it: return immediately so nothing is built, requested, or sent. The
        // method is a suspend fn (returns Object) and its only caller keeps the result solely to compare
        // it against COROUTINE_SUSPENDED, so returning null completes it cleanly without needing the
        // build-specific Unit type.
        val attestClassDef = classDefByStrings("play_integrity_setup_begin").singleOrNull()
            ?: throw PatchException(
                "Twitch: Play Integrity attestation class (\"play_integrity_setup_begin\") not found or " +
                    "ambiguous. The integrity flow changed; re-derive.",
            )
        val attestClass = mutableClassDefBy(attestClassDef)

        val setupMethod = attestClass.methods.singleOrNull { method ->
            method.implementation?.instructions?.any { instruction ->
                ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string ==
                    "play_integrity_setup_begin"
            } == true
        } ?: throw PatchException(
            "Twitch: Play Integrity setup method (logging \"play_integrity_setup_begin\") not found " +
                "uniquely. Re-derive.",
        )

        setupMethod.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """,
        )
    }
}
