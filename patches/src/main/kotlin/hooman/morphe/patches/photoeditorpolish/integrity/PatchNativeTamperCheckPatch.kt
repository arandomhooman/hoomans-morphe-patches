package hooman.morphe.patches.photoeditorpolish.integrity

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// A per-ABI signature for libitcore's JNI_OnLoad tamper branch and the NOP that neutralizes it.
// `offset` is where the branch sits inside `signature`; `nop` is the architecture's NOP encoding
// (same length as the branch it replaces) so JNI_OnLoad always returns success.
private class AbiTamperSignature(
    val abi: String,
    val signature: ByteArray,
    val offset: Int,
    val nop: ByteArray,
)

private fun ints(vararg b: Int): ByteArray = b.map { it.toByte() }.toByteArray()

// armeabi-v7a (Thumb-2): r4 is preset to JNI_VERSION_1_6 (0x10006), GetEnv runs, then the integrity
// check sub() is called and `cbz r0, <fail>` jumps to the log+`r4 = -1` (JNI_ERR) path when it
// returns 0 (tampered). Anchor on the GetEnv-result test, the `bl <check>`, the `cbz`, and the start
// of the success path; overwrite the `cbz r0` (58 b1) with a Thumb NOP (00 bf).
private val armeabiV7a = AbiTamperSignature(
    abi = "armeabi-v7a",
    signature = ints(
        0x10, 0xb1, // cbz  r0, +..            (GetEnv ok?)
        0x4f, 0xf0, 0xff, 0x34, // mov.w r4, #-1            (JNI_ERR)
        0x03, 0xe0, // b    +..
        0x00, 0x98, // ldr  r0, [sp]           (env)
        0x00, 0xf0, 0x1d, 0xf8, // bl   <integrity check>
        0x58, 0xb1, // cbz  r0, <fail>         <- overwritten with NOP
        0x01, 0x98, // ldr  r0, [sp, #4]
        0x0c, 0x49, // ldr  r1, [pc, #..]
    ),
    offset = 14,
    nop = ints(0x00, 0xbf), // Thumb-2 NOP
)

// arm64-v8a (AArch64): w19 is preset to JNI_VERSION_1_6 (0x10006), GetEnv runs, then the integrity
// check sub() is called (`bl <check>`), its boolean result is tested (`tst w0, #0xff`) and
// `b.eq <fail>` branches to the log+`w19 = -1` (JNI_ERR) path when the check returns 0 (tampered).
// Anchor on the `bl <check>`, the `tst`, the `b.eq`, and the start of the success path; overwrite
// the `b.eq` (40 01 00 54) with an AArch64 NOP (1f 20 03 d5) so the success path is always taken.
private val arm64V8a = AbiTamperSignature(
    abi = "arm64-v8a",
    signature = ints(
        0x14, 0x00, 0x00, 0x94, // bl   <integrity check>
        0x1f, 0x1c, 0x00, 0x72, // tst  w0, #0xff
        0x40, 0x01, 0x00, 0x54, // b.eq <fail>             <- overwritten with NOP
        0x88, 0x16, 0x40, 0xf9, // ldr  x8, [x20, #0x28]   (success path)
        0xe9, 0x07, 0x40, 0xf9, // ldr  x9, [sp, #8]
    ),
    offset = 8,
    nop = ints(0x1f, 0x20, 0x03, 0xd5), // AArch64 NOP
)

// Known signatures per ABI. x86/x86_64 builds of libitcore.so exist in some bundles but their
// machine code differs again; they are not derived yet, so if one shows up we throw rather than
// ship it unpatched (the decoder would fail to load and the app would report itself corrupt).
private val abiSignatures = listOf(arm64V8a, armeabiV7a)

// Internal (no name): applied automatically as a dependency of Unlock Pro.
@Suppress("unused")
val patchNativeTamperCheckPatch = rawResourcePatch(
    description = "Defeats the native anti-tamper in libitcore.so so the re-signed build runs. " +
        "libitcore is the app's string/asset decoder; its JNI_OnLoad verifies the APK signing " +
        "certificate and returns JNI_ERR on a mismatch, so on any re-signed APK the library fails " +
        "to load. The DEX then treats the app as corrupt (\"File corrupted, reinstall from Google " +
        "Play\") and exits, and no obfuscated string ever decodes. The branch that acts on the " +
        "failed check is NOP-ed in every ABI's libitcore.so so JNI_OnLoad always reports success " +
        "and the decoder loads, whether the APK ships an arm64-v8a or armeabi-v7a native library.",
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
        var patchedAny = false

        for (entry in abiSignatures) {
            val libPath = "lib/${entry.abi}/libitcore.so"
            val lib = get(libPath)
            if (!lib.exists()) continue

            val bytes = lib.readBytes()
            val match = bytes.findUnique(entry.signature)
                ?: throw PatchException(
                    "libitcore JNI_OnLoad tamper-branch signature not found in $libPath. This patch " +
                        "targets Photo Editor Polish 1.763.262; the ${entry.abi} library changed and " +
                        "its signature must be re-derived.",
                )

            entry.nop.copyInto(bytes, match + entry.offset)
            lib.writeBytes(bytes)
            patchedAny = true
        }

        if (!patchedAny) {
            throw PatchException(
                "No lib/<abi>/libitcore.so found in the APK. Apply this to the merged universal APK " +
                    "(a native split holds the library; arm64-v8a and armeabi-v7a are supported).",
            )
        }
    }
}

// Returns the single start index of [pattern], or null if absent. Throws on more than one match,
// since an ambiguous signature is too weak to apply blindly.
private fun ByteArray.findUnique(pattern: ByteArray): Int? {
    var found: Int? = null
    val last = size - pattern.size
    outer@ for (i in 0..last) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        if (found != null) {
            throw PatchException("libitcore tamper-branch signature is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
