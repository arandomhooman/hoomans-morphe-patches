package hooman.morphe.patches.inshot.integrity

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

private fun ints(vararg b: Int): ByteArray = b.map { it.toByte() }.toByteArray()

// libisvideoengine.so runs two native integrity checks from JNI_OnLoad and kill(pid, 9)s itself when
// either fails, so the re-signed app dies the moment the Video editor loads its engine. Both ABIs are
// patched: the device picks one at runtime (arm64 on modern phones/tablets), and patching only the
// armeabi-v7a copy leaves the arm64 engine to still kill the process.
private class AbiKillSignature(
    val abi: String,
    val signature: ByteArray,
    val offset: Int,
    val replacement: ByteArray,
)

// armeabi-v7a (Thumb-2): both check results are combined, then `bne <skip kill>` jumps over the
// getpid()+kill(pid, 9) pair when they pass. Make that branch unconditional (04 d1 -> 04 e0) so the
// kill path is never entered.
private val armeabiV7a = AbiKillSignature(
    abi = "armeabi-v7a",
    signature = ints(
        0x28, 0x46, // mov   r0, r5
        0x21, 0x46, // mov   r1, r4
        0x01, 0xf0, 0x21, 0xf9, // bl    <second integrity check>
        0x00, 0x2e, // cmp   r6, #0
        0x18, 0xbf, // it    ne
        0x00, 0x28, // cmpne r0, #0
        0x04, 0xd1, // bne   <skip kill>        <- overwritten with unconditional branch
        0x32, 0xf0, 0xfa, 0xeb, // blx   getpid
        0x09, 0x21, // movs  r1, #9
        0x32, 0xf0, 0xfe, 0xeb, // blx   kill
    ),
    offset = 14,
    replacement = ints(0x04, 0xe0), // b <skip kill> (unconditional)
)

// arm64-v8a (AArch64): the two checks gate a `bl getpid; mov w1, #9; bl kill` run. NOP the `bl kill`
// (1f 20 03 d5) so kill() never runs no matter which check failed; getpid is harmless and execution
// falls through to the normal continuation.
private val arm64V8a = AbiKillSignature(
    abi = "arm64-v8a",
    signature = ints(
        0x54, 0x00, 0x00, 0x36, // tbz  w20, #0, <kill path>
        0x80, 0x00, 0x00, 0x37, // tbnz w0, #0, <continue>
        0xb3, 0x27, 0x01, 0x94, // bl   getpid
        0x21, 0x01, 0x80, 0x52, // mov  w1, #9
        0xb5, 0x27, 0x01, 0x94, // bl   kill                 <- overwritten with NOP
    ),
    offset = 16,
    replacement = ints(0x1f, 0x20, 0x03, 0xd5), // AArch64 NOP
)

private val abiSignatures = listOf(arm64V8a, armeabiV7a)

// Internal (no name): applied automatically as a dependency of Unlock Pro.
@Suppress("unused")
val patchVideoEngineKillPatch = rawResourcePatch(
    description = "Stops InShot's video engine from killing the re-signed app when the Video editor " +
        "loads. libisvideoengine.so runs native init checks from JNI_OnLoad and calls kill(pid, 9) on " +
        "a failed re-sign check; the kill path is neutralized in every ABI's copy so the engine " +
        "initializes whether the device runs the arm64-v8a or armeabi-v7a library.",
) {
    compatibleWith(
        Compatibility(
            name = "InShot",
            packageName = "com.camerasideas.instashot",
            appIconColor = 0xFF2558,
            targets = listOf(AppTarget("2.214.1539")),
        ),
    )

    execute {
        var patchedAny = false

        for (entry in abiSignatures) {
            val libPath = "lib/${entry.abi}/libisvideoengine.so"
            val lib = get(libPath)
            if (!lib.exists()) continue

            val bytes = lib.readBytes()
            val match = bytes.findUnique(entry.signature)
                ?: throw PatchException(
                    "InShot libisvideoengine kill-branch signature not found in $libPath. This patch " +
                        "targets InShot 2.214.1539; the ${entry.abi} library changed and its signature " +
                        "must be re-derived.",
                )

            entry.replacement.copyInto(bytes, match + entry.offset)
            lib.writeBytes(bytes)
            patchedAny = true
        }

        if (!patchedAny) {
            throw PatchException(
                "No lib/<abi>/libisvideoengine.so found in the APK. Apply this to the merged universal " +
                    "InShot APK (arm64-v8a and armeabi-v7a are supported).",
            )
        }
    }
}

private fun ByteArray.findUnique(pattern: ByteArray): Int? {
    var found: Int? = null
    val last = size - pattern.size
    outer@ for (i in 0..last) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        if (found != null) {
            throw PatchException("InShot libisvideoengine kill-branch signature is ambiguous.")
        }
        found = i
    }
    return found
}
