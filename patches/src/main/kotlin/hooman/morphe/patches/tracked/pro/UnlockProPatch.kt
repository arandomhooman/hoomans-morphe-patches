package hooman.morphe.patches.tracked.pro

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// Tracked is React Native (Expo): the Pro logic is Hermes bytecode in
// assets/index.android.bundle, not the DEX. The async isProSubscriptionActive resolves true when the
// RevenueCat tier is "pro" or "pro_plus", and every Pro gate reads it (~40 sites), so forcing it true
// unlocks them all. Offsets shift between releases, so anchor on a byte signature around the
// "pro"/"pro_plus" comparison and refuse to patch unless it matches exactly once.
@Suppress("unused")
val unlockProPatch = rawResourcePatch(
    name = "Unlock Pro",
    description = "Unlocks Tracked's premium training tools without a subscription, like muscle " +
        "analytics and training programs. They run on the workout data already on your device, so " +
        "they keep working offline. The separate human-coaching marketplace still needs its own " +
        "subscription.",
) {
    compatibleWith(
        Compatibility(
            name = "Tracked",
            packageName = "com.tracked.mobile",
            appIconColor = 0x3FD080,
            targets = listOf(AppTarget("7.0.0")),
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

        // Hermes lowers `tier === "pro" || tier === "pro_plus"` into the run below; the first StrictEq
        // (the byte we overwrite) leaves r7, which is what the Promise resolves to. The embedded string
        // ids for "pro" (31262) and "pro_plus" (78734) make the run unique in the bundle.
        val signature = intArrayOf(
            0x3B, 0x08, 0x05, 0x00,             // LoadFromEnvironment r8, r5, 0
            0x90, 0x07, 0x1E, 0x7A,             // LoadConstString r7, 31262 ("pro")
            0x17, 0x07, 0x08, 0x07,             // StrictEq r7, r8, r7        <- patch target (offset 8)
            0xB0, 0x11, 0x07,                   // JmpTrue +17, r7
            0x3B, 0x08, 0x05, 0x00,             // LoadFromEnvironment r8, r5, 0
            0x91, 0x05, 0x8E, 0x33, 0x01, 0x00, // LoadConstStringLongIndex r5, 78734 ("pro_plus")
            0x17, 0x07, 0x08, 0x05,             // StrictEq r7, r8, r5
        ).map { it.toByte() }.toByteArray()

        val bytes = bundle.readBytes()
        val match = bytes.findUnique(signature)
            ?: throw PatchException(
                "Pro-gate signature not found in $bundlePath. This patch targets Tracked 7.0.0 " +
                    "(Hermes bytecode v98); the bundle likely changed in a newer build and the " +
                    "signature must be re-derived.",
            )

        // Replace `StrictEq r7, r8, r7` (4 bytes) with `LoadConstTrue r7` + `Jmp +2`: r7 is always true,
        // control falls into the existing JmpTrue and always takes the is-pro branch, so the Promise
        // resolves true.
        //   95 07  LoadConstTrue r7
        //   AE 02  Jmp +2  (falls through to the original JmpTrue)
        val forceTrue = intArrayOf(0x95, 0x07, 0xAE, 0x02).map { it.toByte() }
        val patchAt = match + 8
        forceTrue.forEachIndexed { i, b -> bytes[patchAt + i] = b }

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
            throw PatchException("Pro-gate signature is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
