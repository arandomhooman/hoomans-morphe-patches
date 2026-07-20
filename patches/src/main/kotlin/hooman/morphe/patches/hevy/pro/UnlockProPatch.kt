package hooman.morphe.patches.hevy.pro

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// Hevy is React Native: the Pro logic is Hermes bytecode in assets/index.android.bundle, not the
// DEX. HevyProStore's isPro getter accepts either a paid subscription or its offline grace-period
// flag. Force that flag so the store stays in Hevy's supported offline-Pro state instead of making a
// free subscription look paid. Offsets and string ids shift between releases, so anchor on the
// grace-period getter's byte prologue and refuse to patch unless it matches exactly once.
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

        // Prologue of HevyProStore.isWithinProOfflineGracePeriod (#22128). This path is already
        // treated as Pro by the central isPro getter and is separately exposed as
        // isProFromGracePeriod, keeping consumers on a state the app was designed to handle.
        //   7c 03             LoadThisNS r3
        //   37 00 03 01 6699 GetById r0, r3, 1, 'subscription'
        //   37 00 00 02 e5be GetById r0, r0, 2, 'active_subscription'
        //   77 01             LoadConstNull r1
        //   0e 01 00 01       Eq r1, r0, r1
        val signature = intArrayOf(
            0x7C, 0x03, 0x37, 0x00, 0x03, 0x01, 0x66, 0x99, 0x37, 0x00, 0x00, 0x02,
            0xE5, 0xBE, 0x77, 0x01, 0x0E, 0x01, 0x00, 0x01, 0x76, 0x02, 0x76, 0x04,
            0x90, 0x09, 0x01, 0x37, 0x04, 0x00, 0x03, 0x3A,
        ).map { it.toByte() }.toByteArray()

        val bytes = bundle.readBytes()
        val match = bytes.findUnique(signature)
            ?: throw PatchException(
                "Offline-Pro grace getter not found in $bundlePath. This patch targets Hevy 3.1.0 (Hermes " +
                    "bytecode HBC96); the bundle likely changed in a newer build and the signature " +
                    "must be re-derived.",
            )

        // Keep subscription.is_pro untouched and force the supported offline grace-period source.
        // The bundle remains the same length.
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
            throw PatchException("Offline-Pro grace getter is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
