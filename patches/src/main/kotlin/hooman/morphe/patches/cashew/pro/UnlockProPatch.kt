package hooman.morphe.patches.cashew.pro

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// Cashew is a Flutter app, so the Pro logic is AOT-compiled into lib/arm64-v8a/libapp.so, not the DEX.
// Pro is a local flag: a purchase writes appStateSettings["purchaseID"] (null on a free install). The
// app funnels its upsell gates through one discrete helper, hidePremiumPopup() at 0x842444, which the
// premium popups (budgets/objectives/past-budgets and ~13 other call sites) read to decide whether to
// nag or allow the action. It returns true when premiumPopupEnabled is off OR purchaseID is non-null.
//
// hidePremiumPopup is a real code object here (bl #0x842444, not inlined), so we hit the function once
// and every caller follows. Near its top:
//   0x842478: bl   InitLateStaticFieldStub   ; finish loading premiumPopupEnabled
//   0x84247c: tbnz w0, #4, #0x8424c8         ; if NOT enabled, jump to the "return true" block
//   ...read appStateSettings["purchaseID"], non-null -> return true, else previewDemo check...
//   0x8424c8: r0 = true ; b #0x842524 (LeaveFrame; ret)
// With Dart true = NULL+0x20 / false = NULL+0x10, tbnz w0,#4 branches when premiumPopupEnabled is
// false. The overwrite below forces that branch unconditional; the prologue already ran and 0x8424c8
// flows into LeaveFrame/ret, so the frame stays balanced on the function's own return path.
//
// Machine-code offsets shift between releases, so this is pinned to the arm64 5.3.4 build (Dart 3.1.3)
// and must be re-derived with Blutter on a new version.
@Suppress("unused")
val unlockProPatch = rawResourcePatch(
    name = "Unlock Pro",
    description = "Unlocks Cashew's Pro features without a purchase: unlimited budgets and goals, the " +
        "full past-period budget history, the complete color picker, and no more upgrade popups. " +
        "Everything here is computed on-device. Google Drive cloud sync is gated separately and won't " +
        "unlock, and it relies on Google sign-in, which breaks once the app is re-signed.",
) {
    compatibleWith(
        Compatibility(
            name = "Cashew",
            packageName = "com.budget.tracker_app",
            appIconColor = 0xF6B858,
            targets = listOf(AppTarget("5.3.4")),
        ),
    )

    execute {
        val libPath = "lib/arm64-v8a/libapp.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            throw PatchException(
                "$libPath not found in the APK. This targets the arm64 Cashew 5.3.4 build; if the input " +
                    "is a split/xapk bundle, merge it to an arm64 universal with APKEditor m first so " +
                    "the arm64_v8a libapp.so is present.",
            )
        }

        // 16-byte window at the top of hidePremiumPopup(), unique in the 5.3.4 libapp.so:
        //   bl   InitLateStaticFieldStub   ; de c0 16 94   (signature[0..4], PC-relative but stable here)
        //   tbnz w0, #4, #0x8424c8         ; 60 02 20 37   (signature[4..8], the instruction we replace)
        //   ldr  x0, [THR, #0x68]          ; 40 37 40 f9   (signature[8..12])
        //   ldr  x0, [x0, #0x1d50]         ; 00 a8 4e f9   (signature[12..16], build-specific offset)
        val signature = intArrayOf(
            0xDE, 0xC0, 0x16, 0x94, // bl   InitLateStaticFieldStub
            0x60, 0x02, 0x20, 0x37, // tbnz w0, #4, #0x8424c8
            0x40, 0x37, 0x40, 0xF9, // ldr  x0, [THR, #0x68]
            0x00, 0xA8, 0x4E, 0xF9, // ldr  x0, [x0, #0x1d50]
        ).map { it.toByte() }.toByteArray()

        // Replace the tbnz at signature offset 4 with an unconditional b #0x8424c8 (the function's own
        // return-true block, 0x4c = 19 words ahead): word 0x14000013, little-endian 13 00 00 14.
        val overwriteOffset = 4
        val overwrite = intArrayOf(
            0x13, 0x00, 0x00, 0x14, // b #0x8424c8
        ).map { it.toByte() }.toByteArray()

        val bytes = lib.readBytes()
        val match = bytes.findUnique(signature)
            ?: throw PatchException(
                "hidePremiumPopup signature not found in $libPath. This patch targets Cashew 5.3.4 " +
                    "(arm64, Dart 3.1.3); a different build shifts these offsets and the signature must " +
                    "be re-derived with Blutter.",
            )

        overwrite.forEachIndexed { i, b -> bytes[match + overwriteOffset + i] = b }
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
            throw PatchException("hidePremiumPopup signature is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
