package hooman.morphe.patches.hevy.pro

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// Hevy is React Native: the Pro logic is Hermes bytecode in assets/index.android.bundle, not the
// DEX. The central isPro getter on HevyProStore (Function #22123 in 3.1.0) returns
// `this.subscription.is_pro || this.isWithinProOfflineGracePeriod` on the production path, and it is
// the single read site for is_pro, so forcing it true unlocks every client-side Pro gate. Offsets
// and string ids shift between releases, so anchor on the function's byte prologue and refuse to
// patch unless it matches exactly once.
@Suppress("unused")
val unlockProPatch = rawResourcePatch(
    name = "Unlock Pro",
    description = "Unlocks Hevy's Pro features without a subscription, like unlimited routines and " +
        "advanced workout analytics. They run on the workout data on your device. Anything Hevy " +
        "serves only to subscribed accounts can still stay locked.",
) {
    compatibleWith(
        Compatibility(
            name = "Hevy",
            packageName = "com.hevy",
            appIconColor = 0x101012,
            targets = listOf(AppTarget("3.1.0")),
        ),
    )

    execute {
        val bundlePath = "assets/index.android.bundle"
        val bundle = get(bundlePath)
        if (!bundle.exists()) {
            throw PatchException(
                "$bundlePath not found in the APK. Apply this to a merged universal APK (the Hermes " +
                    "bundle lives in the base split; merge an xapk with APKEditor m first).",
            )
        }

        // Prologue of the isPro getter (#22123). It starts `LoadThisNS r0` (7c 00) and runs the dev
        // override check before the production `subscription.is_pro` read. 16 bytes is already unique
        // in the 16.5 MB bundle; 32 is used for margin.
        //   7c 00          LoadThisNS r0
        //   29 01 01       GetEnvironment r1, 1
        //   2e 05 01 00    LoadFromEnvironment r5, r1, 0
        //   2e 02 01 01    LoadFromEnvironment r2, r1, 1
        //   6e 04 0b       LoadConstUInt8 r4, 11
        //   49 03 02 04    GetByVal r3, r2, r4
        //   76 02          LoadConstUndefined r2
        //   53 03 05 02 03 Call2 r3, r5, r2, r3
        //   37 03 03 01 d1 GetById r3, r3, 1, 'isProStatusOverrideEnabled'
        val signature = intArrayOf(
            0x7C, 0x00, 0x29, 0x01, 0x01, 0x2E, 0x05, 0x01, 0x00, 0x2E, 0x02, 0x01,
            0x01, 0x6E, 0x04, 0x0B, 0x49, 0x03, 0x02, 0x04, 0x76, 0x02, 0x53, 0x03,
            0x05, 0x02, 0x03, 0x37, 0x03, 0x03, 0x01, 0xD1,
        ).map { it.toByte() }.toByteArray()

        val bytes = bundle.readBytes()
        val match = bytes.findUnique(signature)
            ?: throw PatchException(
                "Pro-gate prologue not found in $bundlePath. This patch targets Hevy 3.1.0 (Hermes " +
                    "bytecode HBC96); the bundle likely changed in a newer build and the signature " +
                    "must be re-derived.",
            )

        // Overwrite the first 4 bytes with `LoadConstTrue r0; Ret r0`. The rest of the getter body is
        // unreachable, so the bundle stays the same length and every consumer sees Pro active.
        //   78 00  LoadConstTrue r0
        //   5C 00  Ret r0
        val forceTrue = intArrayOf(0x78, 0x00, 0x5C, 0x00).map { it.toByte() }
        forceTrue.forEachIndexed { i, b -> bytes[match + i] = b }

        bundle.writeBytes(bytes)
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
            throw PatchException("Pro-gate prologue is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
