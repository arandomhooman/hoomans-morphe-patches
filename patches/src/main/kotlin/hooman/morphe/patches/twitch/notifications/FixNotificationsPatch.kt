package hooman.morphe.patches.twitch.notifications

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val STOCK_CERT_SHA1 = "8C68C13822723A2B1FA844BED340031BEB1F9463"

@Suppress("unused")
val fixNotificationsPatch = bytecodePatch(
    name = "Fix notifications",
    description = "Fixes push notifications after patching. Firebase rejects device registration " +
        "because the patched APK has a different signing certificate; this sends Twitch's original " +
        "certificate fingerprint only with Firebase Installations requests so registration can finish.",
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
        val connectionBuilder = FirebaseInstallationsConnectionFingerprint.method
        val instructions = connectionBuilder.instructions
        val certHeaderIndexes = instructions.withIndex().filter { (_, instruction) ->
            ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string ==
                "X-Android-Cert"
        }.map { it.index }
        val certHeaderIndex = certHeaderIndexes.singleOrNull()
            ?: throw PatchException(
                "Twitch notifications: X-Android-Cert header was not found uniquely.",
            )
        val headerCallIndex = instructions.indices.firstOrNull { index ->
            if (index <= certHeaderIndex) return@firstOrNull false
            val reference = (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
            reference?.definingClass == "Ljava/net/URLConnection;" &&
                reference.name == "addRequestProperty" &&
                reference.parameterTypes == listOf("Ljava/lang/String;", "Ljava/lang/String;")
        } ?: throw PatchException(
            "Twitch notifications: X-Android-Cert request call was not found.",
        )
        val call = instructions[headerCallIndex] as? FiveRegisterInstruction
            ?: throw PatchException(
                "Twitch notifications: X-Android-Cert request call changed instruction shape.",
            )
        if (call.registerCount != 3 || call.registerE > 0xff) {
            throw PatchException(
                "Twitch notifications: X-Android-Cert value register changed shape.",
            )
        }

        // Replace only the computed value passed to this header. PackageManager and every other
        // signature consumer still see the APK's real Morphe certificate.
        connectionBuilder.addInstructions(
            headerCallIndex,
            "const-string v${call.registerE}, \"$STOCK_CERT_SHA1\"",
        )
    }
}
