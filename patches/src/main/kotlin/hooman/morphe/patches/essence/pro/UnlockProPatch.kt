package hooman.morphe.patches.essence.pro

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import hooman.morphe.patches.essence.license.disableLicenseCheckPatch

// Essence is Flutter: the feature logic is AOT-compiled into libapp.so, so there's nothing in the
// DEX to fingerprint. Pro is one cached flag on RevenuePurchaseController: a GetX RxBool (the
// controller's field_1b) derived from the RevenueCat "Premium" entitlement. Every Pro gate reads
// that one flag, so forcing it on unlocks them together. Only the controller writes it, and only
// from RevenueCat's CustomerInfo, so a free account never overwrites it back.
//
// Three sites set the flag from a not-entitled result, all feeding the GetX `value=` setter:
//   checkPurchase()      - the init/sync path
//   restorePurchase()    - the "Restore Purchases" path
//   the CustomerInfo update listener - re-evaluated on every entitlement change
// checkPurchase/restorePurchase store Dart's canonical `false` (`add xN, NULL, #0x30`) directly; the
// listener selects it through `csel ..., eq` against `isActive == true`. In every case the `false`
// is `add xN, NULL, #0x30`, which differs from `true` (`add xN, NULL, #0x20`) by one byte. Flipping
// that byte makes the store/select yield true. Flipping the constant is safe; NOP-ing the branches
// that reach the false store would let a path dereference a null entitlement on a free account.
//
// Offsets shift between releases, so match a byte signature anchored on each flag store and refuse to
// patch if it isn't uniquely present.
@Suppress("unused")
val unlockProPatch = rawResourcePatch(
    name = "Unlock Pro",
    description = "Unlocks Essence's premium tools without a subscription, like the detailed " +
        "statistics and insights, custom themes, app lock, data export, and more. They run on your " +
        "device, so they work offline. The AI recovery coach and anything else Essence makes on its " +
        "servers still needs the real subscription.",
) {
    // A re-signed build fails PairIP's Play license check, which kills the app at launch. Bundle the
    // bypass as an internal dependency so it can't be omitted.
    dependsOn(disableLicenseCheckPatch)

    compatibleWith(
        Compatibility(
            name = "Essence",
            packageName = "com.appfactory.essence",
            appIconColor = 0x005030,
            targets = listOf(AppTarget("4.2.8")),
        ),
    )

    execute {
        val libPath = "lib/arm64-v8a/libapp.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            throw PatchException(
                "$libPath not found in the APK. Use the prebuilt universal APK from this patch's " +
                    "repository releases (the arm64 Flutter library must be present).",
            )
        }

        val bytes = lib.readBytes()

        // 1. checkPurchase(): the init/sync path. Anchored on the contiguous true-set then false-set
        //    of the same RxBool: `add x2,NULL,#0x20 (true) | bl value= | b | ldur x0,[fp,#-0x68] |
        //    ldur w1,[x0,#0x1b] (LoadField field_1b) | add x1,x1,HEAP | add x2,NULL,#0x30 (false)`.
        val checkPurchaseSig = intArrayOf(
            0xC2, 0x82, 0x00, 0x91, // add  x2, NULL, #0x20  (true store, the if-active branch)
            0xBC, 0x3F, 0xFE, 0x97, // bl   value=
            0x1E, 0x00, 0x00, 0x14, // b    (skip the false store)
            0xA0, 0x83, 0x59, 0xF8, // ldur x0, [fp, #-0x68]
            0x01, 0xB0, 0x41, 0xB8, // ldur w1, [x0, #0x1b]   (field_1b = Pro RxBool)
            0x21, 0x80, 0x1C, 0x8B, // add  x1, x1, HEAP, lsl #32
            0xC2, 0xC2, 0x00, 0x91, // add  x2, NULL, #0x30   (false store) <- flipped to true
        ).map { it.toByte() }.toByteArray()
        forceTrue(bytes, checkPurchaseSig, falseInsnOffset = 24, "checkPurchase")

        // 2. restorePurchase(): sets the flag false when "Restore Purchases" finds no entitlement.
        //    `ldur x0,[fp,#-0x78] | ldur w1,[x0,#0x1b] | add x1,x1,HEAP | add x2,NULL,#0x30 (false)`.
        val restoreSig = intArrayOf(
            0xA0, 0x83, 0x58, 0xF8, // ldur x0, [fp, #-0x78]
            0x01, 0xB0, 0x41, 0xB8, // ldur w1, [x0, #0x1b]   (field_1b = Pro RxBool)
            0x21, 0x80, 0x1C, 0x8B, // add  x1, x1, HEAP, lsl #32
            0xC2, 0xC2, 0x00, 0x91, // add  x2, NULL, #0x30   (false store) <- flipped to true
        ).map { it.toByte() }.toByteArray()
        forceTrue(bytes, restoreSig, falseInsnOffset = 12, "restorePurchase")

        // 3. CustomerInfo update listener: recomputes the flag on every entitlement change and would
        //    otherwise reset it. It selects the value via `csel x3, x16(true), x17(false), eq` from
        //    `isActive == true`; flipping the x17 `false` to `true` makes the select always true.
        //    `add x16,#0x20 | cmp w1,w16 | add x16,#0x20 | add x17,#0x30 (false) | csel x3,x16,x17,eq
        //     | stur x3,[fp,#-8]`.
        val listenerSig = intArrayOf(
            0xD0, 0x82, 0x00, 0x91, // add  x16, NULL, #0x20  (true)
            0x3F, 0x00, 0x10, 0x6B, // cmp  w1, w16           (isActive == true?)
            0xD0, 0x82, 0x00, 0x91, // add  x16, NULL, #0x20  (true)
            0xD1, 0xC2, 0x00, 0x91, // add  x17, NULL, #0x30  (false) <- flipped to true
            0x03, 0x02, 0x91, 0x9A, // csel x3, x16, x17, eq
            0xA3, 0x83, 0x1F, 0xF8, // stur x3, [fp, #-8]
        ).map { it.toByte() }.toByteArray()
        forceTrue(bytes, listenerSig, falseInsnOffset = 12, "listener")

        lib.writeBytes(bytes)
    }
}

// Finds [signature] uniquely in [bytes] and flips the `add xN, NULL, #0x30` (`false`) at
// [falseInsnOffset] within the match to `add xN, NULL, #0x20` (`true`) by changing its single
// differing immediate byte. The destination register byte is left untouched, so this works for any
// register. Throws if the signature is missing/ambiguous, or if the anchored instruction isn't the
// expected `false` constant (so a layout change fails loudly instead of corrupting an instruction).
private fun forceTrue(
    bytes: ByteArray,
    signature: ByteArray,
    falseInsnOffset: Int,
    site: String,
) {
    val match = bytes.findUnique(signature)
        ?: throw PatchException(
            "Essence: Pro-flag store ($site) signature not found in libapp.so. This patch targets " +
                "Essence 4.2.8 (arm64); the AOT layout likely changed and the signature must be " +
                "re-derived.",
        )
    val insn = match + falseInsnOffset
    // `add xN, NULL, #0x30` little-endian is `<reg> C2 00 91`; `#0x20` (true) is `<reg> 82 00 91`.
    if (bytes[insn + 1] != 0xC2.toByte() || bytes[insn + 2] != 0x00.toByte() || bytes[insn + 3] != 0x91.toByte()) {
        throw PatchException("Essence: Pro-flag store ($site) is not the expected `false` constant.")
    }
    bytes[insn + 1] = 0x82.toByte()
}

// Returns the single start index of [pattern], or null if absent. Throws on more than one match.
private fun ByteArray.findUnique(pattern: ByteArray): Int? {
    var found: Int? = null
    val last = size - pattern.size
    outer@ for (i in 0..last) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        if (found != null) {
            throw PatchException("Essence: a Pro-flag store signature is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
