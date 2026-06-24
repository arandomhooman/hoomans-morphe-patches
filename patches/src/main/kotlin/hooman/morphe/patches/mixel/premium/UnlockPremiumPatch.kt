package hooman.morphe.patches.mixel.premium

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// Mixel is React Native (Expo, Hermes HBC98): the Premium logic is Hermes bytecode in
// assets/index.android.bundle, not the DEX. RevenueCat entitlements feed two independent client-side
// gates, and both have to move or only half the app unlocks.
//
// 1. Customer status. handleCustomerInfo reads entitlements.active.pro and derives a status string
//    ('lifetime' | 'subscriber' | 'free') stored in Redux; every Pro surface (paywall, feature gates,
//    badge) reads it through one hook, useCustomerStatus. The hook is `JNotEqual r0, r0, r1` over
//    `state.preferences.customerStatus` then `LoadConstString r0, 'free'` as the fallback: null/missing
//    status falls through to 'free'. Flipping the JNotEqual's first reg to r1 makes `r1 != r1` always
//    false, so the fallback never runs, and swapping the 'free' string id (0x4312) to 'lifetime'
//    (0xC4FA) hands every reader the top tier from a cold start, before RevenueCat ever syncs.
//
// 2. Recipe packs. Premium recipe packs are gated separately by an "entitled packs" selector that
//    returns all packs only if any of three inputs is truthy (admin user / owned packs / etc.),
//    otherwise the owned-plus-free set. Its first JmpTrue tests param 3; at that point r0 still holds
//    the global object (GetGlobalObject at the function entry, never clobbered before the jump), so
//    repointing the JmpTrue's register operand from r1 to r0 makes the test always true and the
//    selector returns the full pack list.
//
// Both edits are length-preserving byte swaps (the bundle is STORED in the APK, so same-length
// overwrites land cleanly). Offsets and string ids shift between releases, so anchor on the runs
// below and refuse to patch unless each matches exactly once. Expo Updates is disabled in this build
// (manifest expo.modules.updates.ENABLED=false), so no OTA-revert companion is needed.
@Suppress("unused")
val unlockPremiumPatch = rawResourcePatch(
    name = "Unlock Premium",
    description = "Unlocks Mixel's Premium tier without a subscription: the Pro feature gates open " +
        "and the premium recipe packs show as entitled. Both gates are read client-side from " +
        "RevenueCat's entitlements, so this lifts them on-device. Anything Mixel actually checks " +
        "server-side (if a later build moves a gate there) won't be covered.",
) {
    compatibleWith(
        Compatibility(
            name = "Mixel",
            packageName = "com.cfdrink",
            appIconColor = 0x5065A1,
            targets = listOf(AppTarget("1.19.11")),
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

        val bytes = bundle.readBytes()

        // useCustomerStatus (#29570). The run is, in order:
        //   BA 08 00 01     JNotEqual +8, r0, r1        <- byte 2 (r0 -> r1): make r1 != r1, never jump
        //   73 00 12 43     LoadConstString r0, 0x4312  <- bytes 6-7 ('free' -> 'lifetime' = 0xC4FA, LE)
        //   30 01           Ret r0
        //   39 03 01 02     Call2 (console.log noise after the return)
        //   68
        val statusHook = intArrayOf(
            0xBA, 0x08, 0x00, 0x01,
            0x73, 0x00, 0x12, 0x43,
            0x30, 0x01,
            0x39, 0x03, 0x01, 0x02,
            0x68,
        ).map { it.toByte() }.toByteArray()

        val statusMatch = bytes.findUnique(statusHook)
            ?: throw PatchException(
                "useCustomerStatus signature not found in $bundlePath. This patch targets Mixel " +
                    "1.19.11 (Hermes HBC98); the bundle likely changed in a newer build and the " +
                    "signature must be re-derived.",
            )
        // JNotEqual reg_a (byte 2): r0 (0x00) -> r1 (0x01) so the comparison is r1 != r1 (always false)
        // and the default load always runs.
        bytes[statusMatch + 2] = 0x01
        // LoadConstString string id (bytes 6-7, little-endian): 'free' (0x4312) -> 'lifetime' (0xC4FA).
        bytes[statusMatch + 6] = 0xFA.toByte()
        bytes[statusMatch + 7] = 0xC4.toByte()

        // "Getting entitled packs" selector (#25046). The run is, in order:
        //   53 01 02 03 01   Call3 (console.log of "Getting entitled packs...")
        //   6C 01 03         LoadParam r1, 3
        //   90 40 01         JmpTrue +64, r1   <- byte 10 (r1 -> r0): r0 is the global object (set at
        //                       fn entry, never clobbered here), always truthy, so this always jumps
        //                       to the `return globalPackIds` block and the selector yields every pack.
        //   6C 01 04         LoadParam r1, 4
        val packSelector = intArrayOf(
            0x53, 0x01, 0x02, 0x03, 0x01,
            0x6C, 0x01, 0x03,
            0x90, 0x40, 0x01,
            0x6C, 0x01, 0x04,
        ).map { it.toByte() }.toByteArray()

        val packMatch = bytes.findUnique(packSelector)
            ?: throw PatchException(
                "Pack-availability signature not found in $bundlePath. This patch targets Mixel " +
                    "1.19.11 (Hermes HBC98); the bundle likely changed in a newer build and the " +
                    "signature must be re-derived.",
            )
        // JmpTrue register operand (byte 10): r1 (0x01) -> r0 (0x00).
        bytes[packMatch + 10] = 0x00

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
            throw PatchException("Signature is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
