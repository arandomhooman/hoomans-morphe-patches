package hooman.morphe.patches.inshot.integrity

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

private fun ints(vararg b: Int): ByteArray = b.map { it.toByte() }.toByteArray()

// InShot's video engine runs two init-time integrity checks and kills its own process if either
// fails. The branch below skips the getpid()+kill(pid, 9) pair while leaving the rest of JNI_OnLoad
// and native registration intact.
private val videoEngineKillBranch = ints(
    0x28, 0x46, // mov  r0, r5
    0x21, 0x46, // mov  r1, r4
    0x01, 0xf0, 0x21, 0xf9, // bl   <second integrity check>
    0x00, 0x2e, // cmp  r6, #0
    0x18, 0xbf, // it   ne
    0x00, 0x28, // cmpne r0, #0
    0x04, 0xd1, // bne  <skip kill>          <- overwritten with unconditional branch
    0x32, 0xf0, 0xfa, 0xeb, // blx  getpid
    0x09, 0x21, // movs r1, #9
    0x32, 0xf0, 0xfe, 0xeb, // blx  kill
)

private const val BRANCH_OFFSET = 14
private val skipKill = ints(0x04, 0xe0)

// Internal (no name): applied automatically as a dependency of Unlock Pro.
@Suppress("unused")
val patchVideoEngineKillPatch = rawResourcePatch(
    description = "Stops InShot's video engine from killing the re-signed app when the Video picker " +
        "loads. libisvideoengine.so runs native init checks from JNI_OnLoad and calls kill(pid, 9) on " +
        "a failed re-sign check. The patch changes that conditional branch into an unconditional skip " +
        "over the kill path while keeping the rest of the video engine initialization intact.",
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
        val libPath = "lib/armeabi-v7a/libisvideoengine.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            throw PatchException(
                "No $libPath found in the APK. Apply this to the merged universal InShot APK; " +
                    "2.214.1539 ships the video engine in the armeabi-v7a split.",
            )
        }

        val bytes = lib.readBytes()
        val match = bytes.findUnique(videoEngineKillBranch)
            ?: throw PatchException(
                "InShot libisvideoengine kill-branch signature not found. This patch targets " +
                    "InShot 2.214.1539; re-derive the native init check before applying it here.",
            )

        skipKill.copyInto(bytes, match + BRANCH_OFFSET)
        lib.writeBytes(bytes)
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
