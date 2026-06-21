package hooman.morphe.patches.moneymanager.signature

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

// Internal (no name): applied automatically as a dependency of Unlock Premium.
//
// Money Manager carries its own signing-certificate self-check in com.realbyte.money.config.License.
// License.a(Context) reads getPackageInfo().signatures, runs each signature's hashCode through a
// scramble table, and builds a fingerprint string. License.b(String) rebuilds the official
// fingerprint from baked-in character indices and returns whether the read fingerprint matches.
// Main reads License.b(License.a(this)) on launch and, when it is false, shows a hard CommonDialog
// ("This application is not valid. If the problem persists, please send us an email.", the
// signatureFailed string) instead of starting. Re-signing changes signatures[0].hashCode(), so the
// fingerprint no longer matches and the dialog blocks every patched build. (The Firebase
// "Requests from this client are blocked" log seen at launch is a separate, non-fatal side effect of
// the cert-restricted API key, not this gate.)
//
// Forcing License.b to return true makes the comparison pass regardless of the signing cert, so the
// launch gate proceeds. The intro check (com.realbyte.money.ui.intro) inlines the same fingerprint
// compare but with inverted logic - it only blocks when the signature IS official yet the package
// name is unexpected - so a re-signed build (non-official signature) already falls through it and
// needs no change.
@Suppress("unused")
val bypassSignatureCheckPatch = bytecodePatch(
    description = "Makes Money Manager's signing-certificate self-check accept the re-signed (patched) " +
        "build. License.b compares a fingerprint of the app's signing certificate against a baked-in " +
        "value and Main shows a blocking \"application is not valid\" dialog when it fails; re-signing " +
        "always fails it. Forcing the check to pass lets the patched app start. Applied automatically " +
        "with Unlock Premium.",
) {
    compatibleWith(
        Compatibility(
            name = "Money Manager",
            packageName = "com.realbyteapps.moneymanagerfree",
            appIconColor = 0xEE3D1F,
            targets = listOf(AppTarget("4.11.1 GF")),
        ),
    )

    execute {
        // License keeps its real name (config package isn't fully obfuscated). The verifier is the
        // single static (String)Z method: a(Context) returns the cert fingerprint, b(String) compares
        // it. Pin by that shape rather than the bare "b" name so an R8 reshuffle doesn't silently move
        // the patch onto the wrong method.
        val license = mutableClassDefByOrNull("Lcom/realbyte/money/config/License;")
            ?: throw PatchException(
                "Money Manager: License class not found. The signature-check layout changed.",
            )

        val verifier = license.methods.singleOrNull { method ->
            AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "Z" &&
                method.parameterTypes == listOf("Ljava/lang/String;")
        } ?: throw PatchException(
            "Money Manager: expected exactly one static (String)Z verifier in License " +
                "(License.b). The signature-check shape changed; re-derive.",
        )

        verifier.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
