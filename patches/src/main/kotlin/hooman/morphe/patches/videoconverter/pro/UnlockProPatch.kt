package hooman.morphe.patches.videoconverter.pro

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// Video Converter (Remux) is a Flutter app, so the Pro entitlement logic is AOT-compiled into
// lib/arm64-v8a/libapp.so, not the DEX. Pro is a client-side RevenueCat entitlement
// (subscriptions_unlock_everything_pro): CustomerInfo comes back from the server, but the on-device
// Dart code maps it to a bool that the feature gates read. There is one place that bool is produced.
//
// The master getter reads the pro_override setting (an explicit client override, pp+0x12568) and an
// entitlement field, then returns a bool. 24 call sites across the app gate on it (4K output,
// resolution/bitrate, batch conversion, watermark removal). Forcing it true at the entry lights every
// one of those gates at once. We anchor on the load of the pro_override pool entry because that load
// site is unique in the 3.2.2 libapp.so, so the prologue can't collide with another Dart getter that
// shares the same frame shape.
//
// The overwrite is length preserving and built only from bytes this function already emits: the true
// constant it returns on its own true path (add x0, x22, #0x20; x22 is the null register, +0x20 =
// true) and the function's own LeaveFrame. No new bl and no object-pool materialization, which is the
// relocation class most likely to crash at runtime (the Collectr build hand-materialized a pool
// pointer and SIGSEGV'd). The bytes are machine code, so offsets and the pool slot shift between
// releases; this is pinned to the 3.2.2 arm64 build (Dart/Flutter 3.10.4, snapshot 1ce86630...) and
// must be re-derived per version with Blutter.
@Suppress("unused")
val unlockProPatch = rawResourcePatch(
    name = "Unlock Pro",
    description = "Unlocks Video Converter's Pro features without a subscription: 4K output, the full " +
        "resolution and bitrate range, batch conversion, and watermark removal. Everything runs on the " +
        "device's own FFmpeg encoder, so the unlocked settings produce real output. This is the arm64 " +
        "build. The app has no ads, so there is nothing extra to remove.",
) {
    compatibleWith(
        Compatibility(
            name = "Video Converter",
            packageName = "app.remux.video.converter",
            appIconColor = 0x1D414B,
            targets = listOf(AppTarget("3.2.2")),
        ),
    )

    execute {
        val libPath = "lib/arm64-v8a/libapp.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            throw PatchException(
                "$libPath not found in the APK. This targets the arm64 Video Converter 3.2.2 build; " +
                    "apk-pure often serves a v7a-only bundle, so apply this to a merged arm64 universal " +
                    "built from the xapk split bundle (it carries split_config.arm64_v8a.apk) or a Play " +
                    "Store .apks export. Merge it to a universal with APKEditor m first.",
            )
        }

        val bytes = lib.readBytes()

        // The pro getter @ 0x819a04. The signature spans the entry through the load of the pro_override
        // setting from the object pool (pp+0x12568), which is the only load of that pool slot in the
        // 3.2.2 libapp.so, so it pins this getter uniquely:
        //   stp  x29, x30, [x15, #-0x10]!   ; EnterFrame                     (signature[0..4])
        //   mov  x29, x15
        //   sub  x15, x15, #0x78            ; AllocStack                     (overwrite starts here)
        //   stur x1, [x29, #-0x68]          ; save this
        //   ...stack-overflow check, two setup calls...
        //   add  x2, x27, #0x12, lsl #12
        //   ldr  x2, [x2, #0x568]           ; x2 = "pro_override" (pp+0x12568, unique)
        val signature = intArrayOf(
            0xfd, 0x79, 0xbf, 0xa9, // stp  x29, x30, [x15, #-0x10]!
            0xfd, 0x03, 0x0f, 0xaa, // mov  x29, x15
            0xef, 0xe1, 0x01, 0xd1, // sub  x15, x15, #0x78
            0xa1, 0x83, 0x19, 0xf8, // stur x1, [x29, #-0x68]
            0x50, 0x27, 0x40, 0xf9, // ldr  x16, [x26, #0x48]   THR::stack_limit
            0xff, 0x01, 0x10, 0xeb, // cmp  x15, x16
            0xe9, 0x05, 0x00, 0x54, // b.ls #0x819ad8
            0x18, 0xac, 0xf0, 0x97, // bl   #0x444a80
            0xe1, 0x03, 0x00, 0xaa, // mov  x1, x0
            0x71, 0xab, 0xf0, 0x97, // bl   #0x4447ec
            0xe1, 0x03, 0x00, 0xaa, // mov  x1, x0
            0x62, 0x4b, 0x40, 0x91, // add  x2, x27, #0x12, lsl #12
            0x42, 0xb4, 0x42, 0xf9, // ldr  x2, [x2, #0x568]    "pro_override" (pp+0x12568)
        ).map { it.toByte() }.toByteArray()

        // Overwrite the AllocStack at signature offset 8 with a constant-true return. The teardown
        // restores SP from fp (mov x15, x29), so skipping the never-taken AllocStack balances the
        // frame, and everything after the ret is unreachable dead code.
        //   add  x0, x22, #0x20            ; true (x22 is the null register, +0x20 = true)
        //   mov  x15, x29                  ; LeaveFrame
        //   ldp  x29, x30, [x15], #0x10
        //   ret
        // This is the exact true-return + LeaveFrame the function already emits on its own true path
        // (0x819a94 / 0x819acc), so there is no new pool load and no relocation. The ELF maps .text at
        // file offset == vaddr in this build, so the bytes land at file offset 0x819a0c.
        val overwriteOffset = 8
        val overwrite = intArrayOf(
            0xc0, 0x82, 0x00, 0x91, // add  x0, x22, #0x20   ; true
            0xef, 0x03, 0x1d, 0xaa, // mov  x15, x29
            0xfd, 0x79, 0xc1, 0xa8, // ldp  x29, x30, [x15], #0x10
            0xc0, 0x03, 0x5f, 0xd6, // ret
        ).map { it.toByte() }.toByteArray()

        val match = bytes.findUnique(signature)
            ?: throw PatchException(
                "Pro getter signature not found in $libPath. This patch targets Video Converter 3.2.2 " +
                    "(arm64); a different build shifts these offsets and the object-pool slot, so the " +
                    "signature must be re-derived with Blutter.",
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
            throw PatchException("Pro getter signature is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
