package hooman.morphe.patches.iamsober.plus

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// I Am Sober is React Native/Hermes. Sober Plus gates route through the exported
// getHasActiveSoberPlusSubscription selector. The developer mock subscription path is gated behind
// Config.isTestMode, so production builds need the real selector forced directly.
@Suppress("unused")
val unlockSoberPlusPatch = rawResourcePatch(
    name = "Unlock Sober Plus",
    description = "Unlocks the client-side Sober Plus gates backed by the app's local RevenueCat " +
        "state, including locked-access settings, extra reaction types, skins, workbook and urge " +
        "logging entries, and the Sober Plus dashboard. Server-hosted services can still require a " +
        "real account subscription.",
) {
    compatibleWith(
        Compatibility(
            name = "I Am Sober",
            packageName = "com.thehungrywasp.iamsober",
            appIconColor = 0x6F63D9,
            targets = listOf(AppTarget("8.6.7")),
        ),
    )

    execute {
        val bundlePath = "assets/index.android.bundle"
        val bundle = get(bundlePath)
        if (!bundle.exists()) {
            throw PatchException(
                "$bundlePath not found in the APK. Apply this to a merged universal APK; the Hermes " +
                    "bundle lives in the base split.",
            )
        }

        val bytes = bundle.readBytes()

        // Result function for exported `getHasActiveSoberPlusSubscription`:
        //   createSelector(soberPlusSubscription, wellHubUser, (subscription, wellHub) => ...)
        //
        // The original function returns true for WellHub users or when
        // `user.soberPlusSubscription === SoberPlusSubscriptionState.IsActive`.
        val activeSubscriptionSelector = intArrayOf(
            0x6C, 0x01, 0x01, 0x6C, 0x00, 0x02, 0x90, 0x53, 0x00, 0x29, 0x00, 0x00,
            0x2E, 0x04, 0x00, 0x00, 0x2E, 0x03, 0x00, 0x01, 0x6E, 0x02, 0x01, 0x49,
            0x02, 0x03, 0x02, 0x76, 0x03, 0x53, 0x02, 0x04, 0x03, 0x02, 0x36, 0x02,
            0x02, 0x01, 0xBC, 0x53, 0x02, 0x02, 0x03, 0x01, 0x90, 0x29, 0x02, 0x2E,
            0x02, 0x00, 0x00, 0x2E, 0x04, 0x00, 0x01, 0x6E, 0x00, 0x02, 0x49, 0x00,
            0x04, 0x00, 0x53, 0x00, 0x02, 0x03, 0x00, 0x37, 0x00, 0x00, 0x02, 0x01,
            0xE6, 0x37, 0x00, 0x00, 0x03, 0xBC, 0xDD, 0x0F, 0x00, 0x01, 0x00, 0x5C,
            0x00, 0x79, 0x00, 0x5C, 0x00, 0x78, 0x00, 0x5C, 0x00,
        ).map { it.toByte() }.toByteArray()

        val match = bytes.findUnique(activeSubscriptionSelector, "Sober Plus active-subscription selector")
            ?: throw PatchException(
                "Sober Plus active-subscription selector not found in $bundlePath. This patch targets " +
                    "I Am Sober 8.6.7 (Hermes bytecode HBC96); the bundle likely changed and the " +
                    "signature must be re-derived.",
            )

        // Replace the first four bytes with `LoadConstTrue r0; Ret r0`. The remaining selector body is
        // unreachable, so the bundle stays the same length and every consumer sees active Plus.
        val forceTrue = intArrayOf(0x78, 0x00, 0x5C, 0x00).map { it.toByte() }
        forceTrue.forEachIndexed { i, b -> bytes[match + i] = b }

        bundle.writeBytes(bytes)
    }
}

// Returns the single start index of [pattern], or null if absent. Throws on more than one match;
// an ambiguous signature is too weak to apply blindly.
private fun ByteArray.findUnique(pattern: ByteArray, label: String): Int? {
    var found: Int? = null
    val last = size - pattern.size
    outer@ for (i in 0..last) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        if (found != null) {
            throw PatchException("$label is ambiguous.")
        }
        found = i
    }
    return found
}
