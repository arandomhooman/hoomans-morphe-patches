package hooman.morphe.patches.finch.plus

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// Finch is a Flutter app, so the Plus entitlement logic is AOT-compiled into lib/arm64-v8a/libapp.so,
// not the DEX. Plus is a client-cached RevenueCat entitlement: CustomerInfo comes back from the
// server, but the on-device code maps it to a local state string and then ~83 caller files gate
// features by calling isUserSubscribed(). There is no separate cached "plus" boolean that skips that
// call, so the master chokepoint is isUserSubscribed() in
// finch/model/subscription_user/subscription_user.dart. Forcing it true lights every client gate at
// once.
//
// isUserSubscribed() first reads an overrideable bool? setting (an employee/debug override); if that
// is non-null it returns it, otherwise it runs an OR-chain over isSponsorshipActive /
// isUserSponsoredSubscriber / isUserInFreePreview / isUserPayingSubscriber / getEntitlement status.
// Forcing it true at the entry short-circuits the whole chain.
//
// We also force getUserSubscriptionState() to return "yearly". A few surfaces (the sponsored raffle,
// sponsor state, the Finch Plus settings screen, the settings screen, and the "active" badge) read
// the tier string directly via isUserPayingSubscriber() / getUserSubscriptionState() rather than
// through isUserSubscribed(), so this covers them too. isUserPayingSubscriber() does
// getUserSubscriptionState().startsWith("monthly"/"yearly"), so "yearly" flips it.
//
// Both overwrites are length preserving and built only from instructions the functions already emit
// (the true constant, the function's own frame teardown, and the function's own "yearly" load from
// the object pool). No new bl and no new pool materialization, which is the relocation class most
// likely to crash at runtime. The bytes are machine code, so offsets and the object-pool slot shift
// between releases; this is pinned to the 3.73.179 arm64 build and must be re-derived per version with
// Blutter.
@Suppress("unused")
val unlockPlusPatch = rawResourcePatch(
    name = "Unlock Plus",
    description = "Unlocks Finch Plus features without a subscription, including the Plus shop items, " +
        "extra themes and customization, seasonal event tiers, the monthly recap, and Plus insights. " +
        "It also clears the upgrade prompts. This is the arm64 build. Cloud backup and cross-device " +
        "sync run on Finch's own servers and still need the real subscription. " +
        "Re-signing breaks Google sign-in, so log in with email instead.",
) {
    compatibleWith(
        Compatibility(
            name = "Finch",
            packageName = "com.finch.finch",
            appIconColor = 0xBFC2D0,
            targets = listOf(AppTarget("3.73.179")),
        ),
    )

    execute {
        val libPath = "lib/arm64-v8a/libapp.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            throw PatchException(
                "$libPath not found in the APK. This targets the arm64 Finch 3.73.179 build; apk-pure " +
                    "often serves a v7a-only bundle, so apply this to a merged arm64 universal built " +
                    "from a Play Store .apks export or the xapk split bundle (it carries " +
                    "split_config.arm64_v8a.apk). Merge it to a universal with APKEditor m first.",
            )
        }

        val bytes = lib.readBytes()

        // isUserSubscribed() @ 0x13e099c. The signature spans the entry through the two object-pool
        // loads that are specific to this function (the <bool?> type args at pp+0x8680 and the
        // FinchSettingType override setting at pp+0xb198), so it can't collide with the other
        // finchSettingsManager getters that share the prologue. Unique in the 3.73.179 libapp.so:
        //   stp  x29, x30, [x15, #-0x10]!   ; EnterFrame                     (signature[0..4])
        //   mov  x29, x15
        //   sub  x15, x15, #0x20            ; AllocStack                     (overwrite starts here)
        //   ...stack-overflow check, finchSettingsManager init, getSetting setup...
        val isUserSubscribedSig = intArrayOf(
            0xfd, 0x79, 0xbf, 0xa9, // stp  x29, x30, [x15, #-0x10]!
            0xfd, 0x03, 0x0f, 0xaa, // mov  x29, x15
            0xef, 0x81, 0x00, 0xd1, // sub  x15, x15, #0x20
            0x50, 0x1f, 0x40, 0xf9, // ldr  x16, [x26, #0x38]   THR::stack_limit
            0xff, 0x01, 0x10, 0xeb, // cmp  x15, x16
            0x49, 0x0a, 0x00, 0x54, // b.ls #0x13e0af8
            0x40, 0x37, 0x40, 0xf9, // ldr  x0, [x26, #0x68]    field_table_values
            0x00, 0x3c, 0x52, 0xf9, // ldr  x0, [x0, #0x2478]
            0x70, 0x23, 0x40, 0xf9, // ldr  x16, [x27, #0x40]   Sentinel
            0x1f, 0x00, 0x10, 0x6b, // cmp  w0, w16
            0x81, 0x00, 0x00, 0x54, // b.ne #0x13e09d4
            0x62, 0x23, 0x40, 0x91, // add  x2, x27, #8, lsl #12
            0x42, 0xe4, 0x41, 0xf9, // ldr  x2, [x2, #0x3c8]    Field finchSettingsManager
            0xf0, 0x0f, 0x49, 0x94, // bl   InitLateFinalStaticFieldStub
            0x70, 0x23, 0x40, 0x91, // add  x16, x27, #8, lsl #12
            0x10, 0x42, 0x43, 0xf9, // ldr  x16, [x16, #0x680]  <bool?> type args (pp+0x8680)
        ).map { it.toByte() }.toByteArray()

        // Overwrite the AllocStack at signature offset 8 with a constant-true return. The teardown
        // restores SP from fp (mov x15, x29), so skipping the never-taken AllocStack balances the
        // frame. Everything after the ret is unreachable dead code.
        //   add  x0, x22, #0x20            ; true (x22 is the null register, +0x20 = true)
        //   mov  x15, x29                  ; LeaveFrame
        //   ldp  x29, x30, [x15], #0x10
        //   ret
        val isUserSubscribedOverwriteAt = 8
        val isUserSubscribedOverwrite = intArrayOf(
            0xc0, 0x82, 0x00, 0x91,
            0xef, 0x03, 0x1d, 0xaa,
            0xfd, 0x79, 0xc1, 0xa8,
            0xc0, 0x03, 0x5f, 0xd6,
        ).map { it.toByte() }.toByteArray()

        // getUserSubscriptionState() @ 0x13e08f0. Signature spans the entry through the call to
        // getAccountId and the load of the per-account settings key "8LLJTDVPH1" (pp+0x60540), which is
        // unique to this getter.
        //   stp  x29, x30, [x15, #-0x10]!   ; EnterFrame                     (signature[0..4])
        //   mov  x29, x15
        //   sub  x15, x15, #0x18            ; AllocStack                     (overwrite starts here)
        //   ...stack check, getAccountId, key load...
        val getStateSig = intArrayOf(
            0xfd, 0x79, 0xbf, 0xa9, // stp  x29, x30, [x15, #-0x10]!
            0xfd, 0x03, 0x0f, 0xaa, // mov  x29, x15
            0xef, 0x61, 0x00, 0xd1, // sub  x15, x15, #0x18
            0x50, 0x1f, 0x40, 0xf9, // ldr  x16, [x26, #0x38]   THR::stack_limit
            0xff, 0x01, 0x10, 0xeb, // cmp  x15, x16
            0x89, 0x04, 0x00, 0x54, // b.ls #0x13e0994
            0x46, 0x9c, 0x05, 0x94, // bl   getAccountId
            0x01, 0xf0, 0x5f, 0xf8, // ldur x1, [x0, #-1]
            0x21, 0x7c, 0x4c, 0xd3, // ubfx x1, x1, #0xc, #0x14
            0x70, 0x83, 0x41, 0x91, // add  x16, x27, #0x60, lsl #12
            0x10, 0xa2, 0x42, 0xf9, // ldr  x16, [x16, #0x540]  "8LLJTDVPH1" (pp+0x60540)
        ).map { it.toByte() }.toByteArray()

        // Overwrite the AllocStack at signature offset 8 with a constant-"yearly" return. The "yearly"
        // string lives in the object pool at pp+0x60548; this is the exact load the function already
        // uses on its own "yearly" path, so there is no relocation.
        //   add  x0, x27, #0x60, lsl #12   ; PP base (x27 is the object pool)
        //   ldr  x0, [x0, #0x548]          ; x0 = "yearly"
        //   mov  x15, x29                  ; LeaveFrame
        //   ldp  x29, x30, [x15], #0x10
        //   ret
        val getStateOverwriteAt = 8
        val getStateOverwrite = intArrayOf(
            0x60, 0x83, 0x41, 0x91,
            0x00, 0xa4, 0x42, 0xf9,
            0xef, 0x03, 0x1d, 0xaa,
            0xfd, 0x79, 0xc1, 0xa8,
            0xc0, 0x03, 0x5f, 0xd6,
        ).map { it.toByte() }.toByteArray()

        // Both are mandatory: same build, same version pin, so a missing or ambiguous signature means
        // the input isn't the expected 3.73.179 arm64 libapp.so. Fail loud rather than apply a partial
        // unlock.
        listOf(
            Triple("isUserSubscribed", isUserSubscribedSig, isUserSubscribedOverwrite to isUserSubscribedOverwriteAt),
            Triple("getUserSubscriptionState", getStateSig, getStateOverwrite to getStateOverwriteAt),
        ).forEach { (label, signature, overwriteSpec) ->
            val (overwrite, overwriteAt) = overwriteSpec
            val match = bytes.findUnique(signature)
                ?: throw PatchException(
                    "Finch Plus signature ($label) not found in $libPath. This patch targets Finch " +
                        "3.73.179 (arm64); a different build shifts these offsets and the object-pool " +
                        "slots, so the signature must be re-derived with Blutter.",
                )
            overwrite.forEachIndexed { i, b -> bytes[match + overwriteAt + i] = b }
        }

        lib.writeBytes(bytes)
    }
}

// Returns the single start index of [pattern], or null if absent. Throws on more than one match: an
// ambiguous machine-code signature is too weak to overwrite blindly.
private fun ByteArray.findUnique(pattern: ByteArray): Int? {
    var found: Int? = null
    val last = size - pattern.size
    outer@ for (i in 0..last) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        if (found != null) {
            throw PatchException("Finch Plus signature is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
