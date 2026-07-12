package hooman.morphe.patches.tracked.pro

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// Tracked is React Native (Expo): the Pro logic is Hermes bytecode in
// assets/index.android.bundle, not the DEX. Two independent entitlement sources:
//   1. isProSubscriptionActive (RevenueCat "pro"/"pro_plus") -- ~40 call sites
//   2. useSubscriptionTier (server-fetched useQuery, defaults "free") -- separate
//      readers that never touch (1). CustomizeDashboardRoute compares tier==="free";
//      SessionEndStats builds isPro from tier==="pro"||"pro_plus" for Density and
//      Net Progression on the workout summary.
// Offsets shift between releases, so each edit anchors on a unique byte signature
// and refuses to patch unless it matches exactly once.
@Suppress("unused")
val unlockProPatch = rawResourcePatch(
    name = "Unlock Pro",
    description = "Unlocks Tracked's premium training tools without a subscription, like muscle " +
        "analytics, training programs, dashboard customization, and session density/net " +
        "progression. They run on the workout data already on your device, so they keep working " +
        "offline. The separate human-coaching marketplace still needs its own subscription.",
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

        val bytes = bundle.readBytes()

        // Gate 1: isProSubscriptionActive. Hermes lowers
        // `tier === "pro" || tier === "pro_plus"` into the run below; the first StrictEq
        // (the byte we overwrite) leaves r7, which is what the Promise resolves to. The embedded
        // string ids for "pro" (31262) and "pro_plus" (78734) make the run unique in the bundle.
        val signature = intArrayOf(
            0x3B, 0x08, 0x05, 0x00,             // LoadFromEnvironment r8, r5, 0
            0x90, 0x07, 0x1E, 0x7A,             // LoadConstString r7, 31262 ("pro")
            0x17, 0x07, 0x08, 0x07,             // StrictEq r7, r8, r7        <- patch target (offset 8)
            0xB0, 0x11, 0x07,                   // JmpTrue +17, r7
            0x3B, 0x08, 0x05, 0x00,             // LoadFromEnvironment r8, r5, 0
            0x91, 0x05, 0x8E, 0x33, 0x01, 0x00, // LoadConstStringLongIndex r5, 78734 ("pro_plus")
            0x17, 0x07, 0x08, 0x05,             // StrictEq r7, r8, r5
        ).map { it.toByte() }.toByteArray()

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

        // Gate 2a: useSubscriptionTier.data === "free" StrictEq sites. CustomizeDashboardRoute is
        // one; CustomizeDashboardButton / related UI uses a second GetByIdShort+.data+free run with
        // different registers. Force every unique GetByIdShort + free(42004) + StrictEq so isFree
        // never redirects to the paywall. Each StrictEq is 4 bytes: LoadConstFalse dest + Jmp +2.
        // Pattern is the 4-byte StrictEq after free, not the whole GetById (regs vary).
        // free string id 42004 = 0xA414 little-endian after LoadConstString (0x90).
        val freeStrictEqSites = mutableListOf<Int>()
        var scan = 0
        while (scan < bytes.size - 8) {
            // LoadConstString rX, 42004  => 90 rX 14 A4
            if (bytes[scan] == 0x90.toByte() &&
                bytes[scan + 2] == 0x14.toByte() &&
                bytes[scan + 3] == 0xA4.toByte() &&
                bytes[scan + 4] == 0x17.toByte()
            ) {
                // StrictEq rD, rA, rB at scan+4. Require a nearby GetByIdShort (0x44) before free.
                val lookback = bytes.copyOfRange((scan - 12).coerceAtLeast(0), scan)
                if (lookback.any { it == 0x44.toByte() }) {
                    freeStrictEqSites.add(scan + 4)
                }
            }
            scan++
        }
        if (freeStrictEqSites.isEmpty()) {
            throw PatchException(
                "No useSubscriptionTier free StrictEq sites found in $bundlePath. This patch " +
                    "targets Tracked 7.0.0 (Hermes bytecode v98); re-derive free-tier pins.",
            )
        }
        for (strictEqAt in freeStrictEqSites) {
            // StrictEq encoding: 17 dest left right — force dest false.
            val destReg = bytes[strictEqAt + 1].toInt() and 0xFF
            // LoadConstFalse rDest (0x96 r) is 2 bytes; Jmp +2 (0xAE 0x02) is 2 bytes.
            if (destReg > 0xFF) {
                throw PatchException("Tracked free StrictEq dest register out of range at $strictEqAt")
            }
            bytes[strictEqAt] = 0x96.toByte()
            bytes[strictEqAt + 1] = destReg.toByte()
            bytes[strictEqAt + 2] = 0xAE.toByte()
            bytes[strictEqAt + 3] = 0x02.toByte()
        }

        // Gate 2b: SessionEndStats isPro. Density and Net Progression on the workout summary both
        // read env slot isPro = (tier==="pro" || tier==="pro_plus") from useSubscriptionTier; free
        // users get a PRO badge and onPress navigates to the paywall. Force the first StrictEq true
        // so the existing JmpTrue always takes the is-pro store path. Unique 19-byte run around the
        // pro/pro_plus compare with registers r15/r6/r17.
        val sessionIsProSignature = intArrayOf(
            0x90, 0x0F, 0x1E, 0x7A,             // LoadConstString r15, 31262 ("pro")
            0x17, 0x06, 0x11, 0x0F,             // StrictEq r6, r17, r15     <- patch target (offset 4)
            0xB0, 0x0D, 0x06,                   // JmpTrue +13, r6
            0x91, 0x0F, 0x8E, 0x33, 0x01, 0x00, // LoadConstStringLongIndex r15, 78734 ("pro_plus")
            0x17, 0x06, 0x11, 0x0F,             // StrictEq r6, r17, r15
        ).map { it.toByte() }.toByteArray()

        val sessionMatch = bytes.findUnique(sessionIsProSignature)
            ?: throw PatchException(
                "Session-end isPro gate signature not found in $bundlePath. This patch targets " +
                    "Tracked 7.0.0 (Hermes bytecode v98); the bundle likely changed in a newer build " +
                    "and the signature must be re-derived.",
            )

        // Replace `StrictEq r6, r17, r15` with `LoadConstTrue r6` + `Jmp +2`; the following JmpTrue
        // then always stores isPro=true for Density/Net Progression.
        //   95 06  LoadConstTrue r6
        //   AE 02  Jmp +2
        val forceSessionTrue = intArrayOf(0x95, 0x06, 0xAE, 0x02).map { it.toByte() }
        val sessionPatchAt = sessionMatch + 4
        forceSessionTrue.forEachIndexed { i, b -> bytes[sessionPatchAt + i] = b }

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
