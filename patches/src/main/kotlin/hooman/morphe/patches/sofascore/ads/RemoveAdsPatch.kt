package hooman.morphe.patches.sofascore.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove Ads",
    description = "Stops the banner and interstitial ads SofaScore shows around scores and between " +
        "screens. Every ad site reads one decision method off the cached account, and the app's own " +
        "ad master switch caches that method's result, so forcing it to \"no ads\" turns them all off " +
        "with no login needed. The result holds across a sync because the switch re-reads the same " +
        "method. Ads that are served by the video/story SDK inside embedded content are not affected. " +
        "The patch also keeps Firebase notification registration compatible with the re-signed build. " +
        "Re-signing breaks SofaScore's login (it only offers Google and Facebook sign-in, both of which " +
        "reject the new signature), but removing ads does not need an account so the patch still works.",
) {
    compatibleWith(
        Compatibility(
            name = "SofaScore",
            packageName = "com.sofascore.results",
            // SofaScore's launcher-icon blue (ic_launcher_background #374DF5).
            appIconColor = 0x374DF5,
            targets = listOf(AppTarget("26.06.23")),
        ),
    )

    execute {
        // Force the shouldShowAds derivation to return false at method entry. This is the single point
        // every ad consumer and the Application-level ad master switch reads through, and it is a pure
        // recompute (no cached snapshot beats it), so the suppression survives login, logout and sync.
        ShouldShowAdsFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )

        // Firebase Installations rejects a re-signed package when its certificate header no longer
        // matches SofaScore's bundled Firebase project. Keep the stock cert (SHA-1, uppercase) while
        // preserving SofaScore's own project and API key; this is required for FCM registration.
        val installationConnection = FirebaseInstallationsConnectionFingerprint.method
        val instructions = installationConnection.implementation?.instructions
            ?: throw PatchException("SofaScore: Firebase Installations connection method has no implementation.")
        val certHeaderIndex = instructions.withIndex().singleOrNull { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference.let { reference ->
                (reference as? StringReference)?.string == "X-Android-Cert"
            }
        }?.index ?: throw PatchException(
            "SofaScore: Firebase X-Android-Cert header pin was not unique; re-derive the request builder.",
        )
        val certHeaderCallIndex = instructions.withIndex().firstOrNull { (index, instruction) ->
            index > certHeaderIndex && index <= certHeaderIndex + 3 &&
                (instruction as? ReferenceInstruction)?.reference.let { reference ->
                    val method = reference as? MethodReference
                    method?.name == "addRequestProperty" && method.returnType == "V"
                }
        }?.index ?: throw PatchException(
            "SofaScore: Firebase X-Android-Cert setter was not found after its header literal.",
        )
        val certHeaderRegister =
            (instructions[certHeaderIndex] as? OneRegisterInstruction)?.registerA
                ?: throw PatchException("SofaScore: Firebase certificate header literal has no register.")
        val certHeaderCall = instructions[certHeaderCallIndex] as? FiveRegisterInstruction
            ?: throw PatchException("SofaScore: Firebase certificate header setter uses an unsupported invoke shape.")
        if (certHeaderCall.registerCount != 3 || certHeaderCall.registerD != certHeaderRegister) {
            throw PatchException("SofaScore: Firebase certificate header arguments changed; re-derive the request builder.")
        }
        val certValueRegister = certHeaderCall.registerE
        installationConnection.addInstructions(
            certHeaderCallIndex,
            "const-string v$certValueRegister, \"E235776BC0AD3837B01359177C438658B1424D23\"",
        )
    }
}
