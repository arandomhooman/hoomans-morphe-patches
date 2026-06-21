package hooman.morphe.patches.wanderlog.pro

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import hooman.morphe.patches.wanderlog.ota.disableExpoUpdatesPatch

// Wanderlog is React Native (Expo): the Pro logic is Hermes bytecode (HBC96) in
// assets/index.android.bundle, not the DEX. Every entitlement read in the app funnels through one
// helper, getUserTierFromLatestSubscription(sub), which returns {userTier, grace}. isTrialOrProUser,
// shouldShowProBadge, shouldShowUpgradeOrResubscribeModal, canUserStartSubscription, isSubscribed_ and
// the place/AI/ad gates all compute from its userTier; nothing caches a separate pro flag that skips it.
//
// The catch is the entry guard. getUserTierFromLatestSubscription does `if (!sub) return {userTier:
// free}` before any date math, and on a free account the client-held subscription is null, so it
// short-circuits to free. Forcing the downstream isTrialOrProUser predicate true (the previous attempt)
// only flipped `!== free` checks; the `=== pro` checks (Pro badge, upgrade modal, caps) still read free
// off this helper and stayed locked.
//
// So patch the helper itself. Two of its return blocks build a `free` result: the null-subscription
// guard and the expired-subscription branch. Each does `GetById r,r 'UserTier'` then `GetById r,r
// 'free'`; rewriting the second GetById's string id from 'free' (19040 = 0x4a60) to 'pro' (15536 =
// 0x3cb0) makes the helper hand back userTier 'pro' for a free or lapsed account, and every gate that
// reads it opens. A real paying subscription still flows through the date math and is unaffected.
//
// The two GetById string ids are 16-bit operands, so each swap is two bytes in place (the bundle is
// STORED, so a same-length overwrite writes back cleanly). The bare `GetById ... 'free'` run is common,
// so anchor on the surrounding LoadFromEnvironment/UserTier/PutNewOwnById run and refuse to patch unless
// it's unique. Offsets and string ids shift between releases, so re-derive on a new build.
@Suppress("unused")
val unlockProPatch = rawResourcePatch(
    name = "Unlock Pro",
    description = "Lifts Wanderlog's free-tier caps without a subscription: unlimited places per trip, " +
        "the in-app feature gates unlocked, and no more upgrade nags. Your trips already sync on any " +
        "tier, so this surfaces them rather than faking data. Features that run on Wanderlog's own " +
        "servers still need the real subscription and may not work with this alone, including the AI " +
        "Trip Assistant (the query cap is lifted on-device, but the assistant itself is server-run), " +
        "offline guide downloads, and live flight-price tracking.",
) {
    // Wanderlog has Expo Updates on, so it would otherwise fetch a fresh unpatched bundle on a later
    // launch and revert this. Bundle the OTA-disable so it can't be omitted.
    dependsOn(disableExpoUpdatesPatch)

    compatibleWith(
        Compatibility(
            name = "Wanderlog",
            packageName = "com.wanderlog.android",
            appIconColor = 0xF75940,
            targets = listOf(AppTarget("2.208")),
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

        // getUserTierFromLatestSubscription's two `free` return blocks. The run is, in order:
        //   2e rr rr 02              LoadFromEnvironment r, r, 2     (the module env holding UserTier)
        //   37 rr rr cc 58 b7        GetById r, r, cache, str 46936 ("UserTier")
        //   37 rr rr cc 60 4a        GetById r, r, cache, str 19040 ("free")   <- swap 60 4a -> b0 3c
        //   40 dd rr b7 fc           PutNewOwnById r, r, str 64695 ("userTier")
        // The null-subscription guard writes the result into r1 (PutNewOwnById r1 = 40 01), the
        // expired-subscription branch into r0 (40 00). Both runs are unique in the bundle, so they pin
        // their respective blocks without colliding with any other `.free` read.
        val nullGuard = intArrayOf(
            0x2E, 0x02, 0x02, 0x02,
            0x37, 0x02, 0x02, 0x01, 0x58, 0xB7,
            0x37, 0x02, 0x02, 0x02, 0x60, 0x4A,
            0x40, 0x01, 0x02, 0xB7, 0xFC,
        ).map { it.toByte() }.toByteArray()
        val expiredBranch = intArrayOf(
            0x2E, 0x02, 0x02, 0x02,
            0x37, 0x02, 0x02, 0x01, 0x58, 0xB7,
            0x37, 0x02, 0x02, 0x02, 0x60, 0x4A,
            0x40, 0x00, 0x02, 0xB7, 0xFC,
        ).map { it.toByte() }.toByteArray()

        // 'free' (19040 = 0x4a60) -> 'pro' (15536 = 0x3cb0), little-endian, at signature offset 14.
        val freeStrIdAt = 14
        val proLo = 0xB0.toByte()
        val proHi = 0x3C.toByte()

        listOf("null-subscription guard" to nullGuard, "expired-subscription branch" to expiredBranch)
            .forEach { (label, signature) ->
                val match = bytes.findUnique(signature)
                    ?: throw PatchException(
                        "Pro-tier signature ($label) not found in $bundlePath. This patch targets " +
                            "Wanderlog 2.208 (Hermes bytecode HBC96); the bundle likely changed in a " +
                            "newer build and the signature must be re-derived.",
                    )
                bytes[match + freeStrIdAt] = proLo
                bytes[match + freeStrIdAt + 1] = proHi
            }

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
            throw PatchException("Pro-tier signature is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
