package hooman.morphe.patches.photoeditorpolish.integrity

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// Internal (no name): applied automatically as a dependency of Unlock Pro.
@Suppress("unused")
val patchNativeTamperCheckPatch = rawResourcePatch(
    description = "Defeats the native anti-tamper in lib/armeabi-v7a/libitcore.so so the re-signed " +
        "build runs. libitcore is the app's string/asset decoder; its JNI_OnLoad verifies the APK " +
        "signing certificate and returns JNI_ERR on a mismatch, so on any re-signed APK the library " +
        "fails to load. The DEX then treats the app as corrupt (\"File corrupted, reinstall from " +
        "Google Play\") and exits, and no obfuscated string ever decodes. The branch that acts on " +
        "the failed check is NOP-ed so JNI_OnLoad always reports success and the decoder loads.",
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
        val libPath = "lib/armeabi-v7a/libitcore.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            throw PatchException(
                "$libPath not found in the APK. Apply this to the merged universal APK " +
                    "(the armeabi-v7a split holds the native library).",
            )
        }

        // JNI_OnLoad (Thumb-2): r4 is preset to JNI_VERSION_1_6 (0x10006), GetEnv runs, then the
        // integrity check sub() is called and `cbz r0, <fail>` jumps to the log+`r4 = -1` (JNI_ERR)
        // path when it returns 0 (tampered). Anchor on the surrounding bytes (the GetEnv-result test,
        // the `bl <check>`, the `cbz`, and the start of the success path) and overwrite the `cbz r0`
        // (58 b1) with a NOP (00 bf) so the tampered verdict is ignored and JNI_OnLoad returns 0x10006.
        // bytes: 10 b1 | 4f f0 ff 34 | 03 e0 | 00 98 | 00 f0 1d f8 | [58 b1] | 01 98 | 0c 49
        val signature = intArrayOf(
            0x10, 0xb1, // cbz  r0, +.. (GetEnv ok?)
            0x4f, 0xf0, 0xff, 0x34, // mov.w r4, #-1   (JNI_ERR)
            0x03, 0xe0, // b    +..
            0x00, 0x98, // ldr  r0, [sp]   (env)
            0x00, 0xf0, 0x1d, 0xf8, // bl   <integrity check>
            0x58, 0xb1, // cbz  r0, <fail> <- overwritten with NOP
            0x01, 0x98, // ldr  r0, [sp, #4]
            0x0c, 0x49, // ldr  r1, [pc, #..]
        ).map { it.toByte() }.toByteArray()

        val bytes = lib.readBytes()
        val match = bytes.findUnique(signature)
            ?: throw PatchException(
                "libitcore JNI_OnLoad tamper-branch signature not found in $libPath. This patch " +
                    "targets Photo Editor Polish 1.763.262 (armeabi-v7a); the library changed and " +
                    "the signature must be re-derived.",
            )

        // The `cbz r0` is at offset 14 in the signature; replace it with `nop` (00 bf).
        val cbzOffset = 14
        bytes[match + cbzOffset] = 0x00.toByte()
        bytes[match + cbzOffset + 1] = 0xbf.toByte()

        lib.writeBytes(bytes)
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
