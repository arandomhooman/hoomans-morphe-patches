package hooman.morphe.patches.cronometer.gold

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// Cronometer is Flutter: logic is AOT-compiled into libapp.so, so there's nothing in the DEX to
// fingerprint. Gold is gated on one cached override (Blutter's field_63) on the User singleton, set
// from the account payload. NOP-ing the tbnz in its setter makes the bool always store true, which
// unlocks every Gold gate at once. Offsets shift between releases, so match a byte signature
// anchored on the field_63 store and refuse to patch if it isn't uniquely present.
@Suppress("unused")
val unlockGoldPatch = rawResourcePatch(
    name = "Unlock Gold",
    description = "Unlocks Cronometer Gold without a subscription, like custom charts, advanced " +
        "reports, the fasting tracker, custom biometrics, an ad-free view, and more. Anything " +
        "Cronometer works out on its own servers still needs Gold.",
) {
    compatibleWith(
        Compatibility(
            name = "Cronometer",
            packageName = "com.cronometer.android.gold",
            appIconColor = 0xF26B21,
            targets = listOf(AppTarget("4.56.0")),
        ),
    )

    execute {
        val libPath = "lib/arm64-v8a/libapp.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            throw PatchException(
                "$libPath not found in the APK. Use the prebuilt universal APK from " +
                    "this patch's repository releases. If the APK embeds another APK " +
                    "(e.g. a Wear OS companion at res/raw/*.apk), Morphe Manager can " +
                    "misdetect it as a split bundle and merge away the native libraries. " +
                    "The release APK is built to avoid this.",
            )
        }

        // tbnz w0,#4,+0xc | add x2,NULL,#0x20 (true) | b +8 | add x2,NULL,#0x30 (false)
        // | ldur x0,[fp,#-0x18] | ldur x1,[fp,#-8] | stur w2,[x0,#0x63]  (field_63 store)
        val signature = intArrayOf(
            0x60, 0x00, 0x20, 0x37, // tbnz w0, #4, +0xc   <- patched to NOP
            0xC2, 0x82, 0x00, 0x91, // add  x2, NULL, #0x20  (true)
            0x02, 0x00, 0x00, 0x14, // b    +0x8
            0xC2, 0xC2, 0x00, 0x91, // add  x2, NULL, #0x30  (false)
            0xA0, 0x83, 0x5E, 0xF8, // ldur x0, [fp, #-0x18]
            0xA1, 0x83, 0x5F, 0xF8, // ldur x1, [fp, #-8]
            0x02, 0x30, 0x06, 0xB8, // stur w2, [x0, #0x63]  (User.field_63 = gold override)
        ).map { it.toByte() }.toByteArray()

        val bytes = lib.readBytes()
        val match = bytes.findUnique(signature)
            ?: throw PatchException(
                "Gold-override signature not found in $libPath. This patch targets " +
                    "Cronometer 4.56.0 (arm64); the AOT layout likely changed in a newer " +
                    "build and the signature must be re-derived.",
            )

        // Overwrite the leading tbnz with nop (0xD503201F) so the bool always takes the true branch.
        val nop = intArrayOf(0x1F, 0x20, 0x03, 0xD5).map { it.toByte() }
        nop.forEachIndexed { i, b -> bytes[match + i] = b }

        lib.writeBytes(bytes)
    }
}

// Returns the single start index of [pattern], or null if absent. Throws on more than one match;
// an ambiguous signature is too weak to apply blindly.
private fun ByteArray.findUnique(pattern: ByteArray): Int? {
    var found: Int? = null
    val last = size - pattern.size
    outer@ for (i in 0..last) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        if (found != null) {
            throw PatchException("Gold-override signature is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
