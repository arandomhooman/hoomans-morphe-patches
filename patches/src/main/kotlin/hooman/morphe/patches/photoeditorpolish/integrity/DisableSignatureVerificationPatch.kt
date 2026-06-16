package hooman.morphe.patches.photoeditorpolish.integrity

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

// The app's original signing certificate as signatures[0].toCharsString() (lowercase hex of the
// cert DER). The verifier slices this string and compares the slices to baked-in hashes, so making
// the signature reader return this exact value passes every check on a re-signed build.
private const val ORIGINAL_CERT_CHARS =
    "3082032b30820213a003020102020474515bbe300d06092a864886f70d01010b0500304531153013" +
        "060355040a130c436f6c6c6167654d616b657231153013060355040b130c436f6c6c6167654d616b" +
        "6572311530130603550403130c436f6c6c6167654d616b65723020170d31363039323031303234" +
        "35355a180f32313136303832373130323435355a304531153013060355040a130c436f6c6c616765" +
        "4d616b657231153013060355040b130c436f6c6c6167654d616b6572311530130603550403130c43" +
        "6f6c6c6167654d616b657230820122300d06092a864886f70d01010105000382010f003082010a02" +
        "82010100e031e2318c96300ece5850ef246122d91ac7c99293c81afe8907363d0294799d3c24c0a7" +
        "6f9929792e3222eabc96bb132c54379678b046e0c76fa1bd6dd4b7604668e04f11fe2c6453e74bb5" +
        "cf5791ae4e2a2c5bd201163bedea7a8e68866c4be58f216b45c33d0ba953e53b3bd8c0736fb0ca5b" +
        "aab530d986b1cdcfa528df1e24473654cfabf70d2fee11ecc14636304bb610a3601dc8ad302dda2a" +
        "dd561bc2a4232f64520c7a1ec40f75430c2aa873bc11e3519411200db338a3f53b8cecbfebfe972a" +
        "46476209372ae0f55d7be7ffdb6bb85d4e0447c6ab17b470167efb9310adf89e349c9da86011e120" +
        "a433899ec1ef60f3dfdb92cbab4b3f6916a7b8470203010001a321301f301d0603551d0e04160414" +
        "88d6e768e04d53a7d8a6189d36c60bdd4ce29c29300d06092a864886f70d01010b05000382010100" +
        "ae6f63fe13c0d3bd7faeaa43c11f1ff24b6521be9cae055c695c9d3fd21a9f760bc3196ac27a09f1" +
        "76ad4e6b16ff11ba3dcd88decfd08ce0b7b25261f87bcec85e0326d75d9a4ef9a1d09fe0f2606537" +
        "6d2f63a5f6ad3cf7f684983cb7b2f12e0653d5a0e591507200389537ced7eb7cc006452f0986795b" +
        "884d826c089670a7ba30c6c3af7c21f8e4bc47b2d79f290ff36e93d5e14b5899e5a20553eb19b3a6" +
        "920d6e5117821bf7fc87a6efd41cb6ebe82761e9727e75ad1df76dfac56445865c454971a640224a" +
        "a18b29cc520993415cb7437a375e12f7b18efd6ebdd016550819198306c68cba4fd75a5bc2e31ea3" +
        "76c154cca69614d9c90588b28a32d8cf"

// Internal (no name): applied automatically as a dependency of Unlock Pro.
@Suppress("unused")
val disableSignatureVerificationPatch = bytecodePatch(
    description = "Neutralizes Photo Editor Polish's DEX signature verification so the re-signed " +
        "(patched) build runs. The app ships ~50 obfuscated copies of a verifier that reads its own " +
        "signing certificate, compares slices of it against baked-in hashes, and kills the process " +
        "(killProcess + System.exit) on a mismatch, both inside a self-contained check and inline at " +
        "many feature call sites. Re-signing trips every copy. Each copy carries the same plaintext " +
        "crash string, so they are found by it; the certificate reader is then made to return the " +
        "original certificate so every comparison passes, and the self-contained check is no-oped.",
) {
    compatibleWith(
        Compatibility(
            name = "Photo Editor Polish",
            packageName = "photo.editor.photoeditor.photoeditorpro",
            appIconColor = 0xF82888,
            targets = listOf(AppTarget("1.763.262")),
        ),
    )

    execute {
        // The verifier cluster (jadx: VerifyUtils*) is duplicated across many classes. Each has a
        // certificate reader b(Context) returning signatures[0].toCharsString(); the checks compare
        // its substrings to hardcoded hashes and kill on mismatch. Some classes also wrap the whole
        // read+compare+kill in a static c(Context). Every copy embeds this exact crash message, so
        // find them by it, force b(Context) to return the original certificate (so every substring
        // comparison passes), and no-op any c(Context) for good measure. Fail loudly if it's gone.
        val sentinel = "System.exit returned normally, while it was supposed to halt JVM."
        val verifierClasses = classDefByStrings(sentinel)
        if (verifierClasses.isEmpty()) {
            throw PatchException(
                "Photo Editor Polish: signature-verifier cluster not found (no class carries the " +
                    "kill sentinel). The anti-tamper layout has changed.",
            )
        }

        var spoofedReaders = 0
        verifierClasses.forEach { classDef ->
            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.methods
                .filter { method ->
                    AccessFlags.STATIC.isSet(method.accessFlags) &&
                        method.returnType == "Ljava/lang/String;" &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes.first().toString() == "Landroid/content/Context;"
                }
                .forEach { reader ->
                    reader.addInstructions(
                        0,
                        """
                            const-string v0, "$ORIGINAL_CERT_CHARS"
                            return-object v0
                        """,
                    )
                    spoofedReaders++
                }

            mutableClass.methods
                .filter { method ->
                    AccessFlags.STATIC.isSet(method.accessFlags) &&
                        method.returnType == "V" &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes.first().toString() == "Landroid/content/Context;"
                }
                .forEach { check -> check.addInstructions(0, "return-void") }
        }

        if (spoofedReaders == 0) {
            throw PatchException(
                "Photo Editor Polish: verifier cluster found but no certificate reader " +
                    "b(Context) to spoof. The check shape has changed.",
            )
        }
    }
}
