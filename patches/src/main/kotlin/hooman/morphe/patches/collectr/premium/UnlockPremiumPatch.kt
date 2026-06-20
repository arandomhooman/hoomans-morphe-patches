package hooman.morphe.patches.collectr.premium

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// Collectr is a Flutter app, so the premium logic is AOT-compiled into lib/arm64-v8a/libapp.so rather
// than the DEX. Membership is a client-side UserTier enum (free/pro/hidden) that ~38 sites read as
// `membership.tier == pro` by loading the object field and comparing it to the const pro UserTier;
// there is no single tier getter to hook. The one place that value is produced is Membership.fromJson,
// which builds the membership the whole app reads. This patch forces fromJson to store tier=pro, which
// flips every one of those gates at once. The bytes are machine code, so offsets shift between
// releases and must be re-derived per version with Blutter; this is pinned to the 2.5.0 arm64 build.
@Suppress("unused")
val unlockPremiumPatch = rawResourcePatch(
    name = "Unlock Premium",
    description = "Unlocks Collectr's premium features without a subscription, like unlimited " +
        "collections, price alerts, and the advanced analytics. This is the arm64 build. Anything " +
        "Collectr serves from its own servers still needs the real subscription.",
) {
    compatibleWith(
        Compatibility(
            name = "Collectr",
            packageName = "com.collectrinc.collectr",
            appIconColor = 0x00204B,
            targets = listOf(AppTarget("2.5.0")),
        ),
    )

    execute {
        val libPath = "lib/arm64-v8a/libapp.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            throw PatchException(
                "$libPath not found in the APK. This targets the arm64 Collectr 2.5.0 build; apk-pure " +
                    "serves a v7a-only bundle, so apply this to a merged arm64 universal built from a " +
                    "Play Store .apks export (it carries split_config.arm64_v8a.apk). Merge it to a " +
                    "universal with APKEditor m first.",
            )
        }

        // 20-byte anchor inside Membership.fromJson, right where firstWhere() picks the tier enum from
        // the parsed JSON and saves it to the local slot the constructor later stores into tier. This
        // window is unique in the 2.5.0 libapp.so:
        //   ldr  x4, [x27, #0xc8]      ; firstWhere call descriptor   (kept, signature[0..4])
        //   bl   firstWhere           ; pick the matching UserTier    (replaced)
        //   mov  x1, x0               ; x1 = picked tier              (replaced)
        //   ldur x0, [x29, #-0x10]    ; reload the context            (replaced, re-emitted below)
        //   stur x1, [x29, #-8]       ; save tier to the local slot   (kept, signature[16..20])
        val signature = intArrayOf(
            0x64, 0x67, 0x40, 0xf9, // ldr  x4, [x27, #0xc8]
            0xe1, 0xd8, 0xfd, 0x97, // bl   firstWhere
            0xe1, 0x03, 0x00, 0xaa, // mov  x1, x0
            0xa0, 0x03, 0x5f, 0xf8, // ldur x0, [x29, #-0x10]
            0xa1, 0x83, 0x1f, 0xf8, // stur x1, [x29, #-8]
        ).map { it.toByte() }.toByteArray()

        // Overwrite the 12 bytes at signature offset 4 (bl firstWhere, mov, ldur). The firstWhere result
        // is only ever consumed through the stur into [x29,#-8], so dropping the call is safe. We move
        // the context reload up so x0 is still live for the JSON read that follows, then materialize the
        // const pro UserTier the same way the app does everywhere else (the PP table at slot 0x13548):
        //   ldur x0, [x29, #-0x10]    ; context reload moved up (x0 still needed at the next instruction)
        //   add  x1, x27, #0x13, lsl #12
        //   ldr  x1, [x1, #0x548]     ; x1 = const UserTier 'pro' (PP+0x13548)
        // The kept stur x1, [x29, #-8] then saves pro, so the constructor stores tier=pro. The membership
        // allocation and the other field stores are untouched, and the edit is length preserving.
        val overwriteOffset = 4
        val overwrite = intArrayOf(
            0xa0, 0x03, 0x5f, 0xf8, // ldur x0, [x29, #-0x10]
            0x61, 0x4f, 0x40, 0x91, // add  x1, x27, #0x13, lsl #12
            0x21, 0xa4, 0x42, 0xf9, // ldr  x1, [x1, #0x548]
        ).map { it.toByte() }.toByteArray()

        val bytes = lib.readBytes()
        val match = bytes.findUnique(signature)
            ?: throw PatchException(
                "Membership tier signature not found in $libPath. This patch targets Collectr 2.5.0 " +
                    "(arm64); a different build shifts these offsets and the signature must be " +
                    "re-derived with Blutter.",
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
            throw PatchException("Membership tier signature is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
