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
// flips every one of those gates at once.
//
// It also neutralizes the forced-update wall. Collectr's servers hand the client a minimum required
// version; CollectrAppUpdater.showUpgradeDialogIfNeeded compares it against the installed version and,
// when the install is older, shows a non-dismissible "Update App Required" dialog. A re-signed patched
// build can't be updated through the Play Store, so once the server minimum moves past the pinned
// version the app becomes unusable (issue #200). We force that method to take its own early-return
// path so the dialog never shows, regardless of what minimum the server sends.
//
// The bytes are machine code, so offsets shift between releases and must be re-derived per version with
// Blutter; this is pinned to the 2.5.6 arm64 build (version_code 737).
@Suppress("unused")
val unlockPremiumPatch = rawResourcePatch(
    name = "Unlock Premium",
    description = "Unlocks Collectr's premium features without a subscription, like unlimited " +
        "collections, price alerts, and the advanced analytics, and stops the blocking \"Update App " +
        "Required\" popup that otherwise locks patched installs out. This is the arm64 build. Anything " +
        "Collectr serves from its own servers still needs the real subscription.",
) {
    compatibleWith(
        Compatibility(
            name = "Collectr",
            packageName = "com.collectrinc.collectr",
            appIconColor = 0x00204B,
            targets = listOf(AppTarget("2.5.6")),
        ),
    )

    execute {
        val libPath = "lib/arm64-v8a/libapp.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            throw PatchException(
                "$libPath not found in the APK. This targets the arm64 Collectr 2.5.6 build; apk-pure " +
                    "serves a v7a-only bundle, so apply this to a merged arm64 universal built from an " +
                    "arm64 XAPK (apkcombo lists one) or a Play Store .apks export (it carries " +
                    "split_config.arm64_v8a.apk). Merge it to a universal with APKEditor m first.",
            )
        }

        val bytes = lib.readBytes()

        // --- Force Membership.tier = pro -------------------------------------------------------------
        // 20-byte anchor inside Membership.fromJson (0x94abc0), right where firstWhere() picks the tier
        // enum from the parsed JSON and saves it to the local slot the constructor later stores into
        // tier. This window is unique in the 2.5.6 libapp.so:
        //   ldr  x4, [x27, #0xd0]     ; firstWhere call descriptor   (kept, signature[0..4])
        //   bl   firstWhere           ; pick the matching UserTier    (replaced)
        //   mov  x1, x0               ; x1 = picked tier              (replaced)
        //   ldur x0, [x29, #-0x10]    ; reload the context            (replaced, re-emitted below)
        //   stur x1, [x29, #-8]       ; save tier to the local slot   (kept, signature[16..20])
        val tierSignature = intArrayOf(
            0x64, 0x6b, 0x40, 0xf9, // ldr  x4, [x27, #0xd0]
            0x7b, 0x6e, 0xfd, 0x97, // bl   firstWhere
            0xe1, 0x03, 0x00, 0xaa, // mov  x1, x0
            0xa0, 0x03, 0x5f, 0xf8, // ldur x0, [x29, #-0x10]
            0xa1, 0x83, 0x1f, 0xf8, // stur x1, [x29, #-8]
        ).toByteArray()

        // Overwrite the 12 bytes at signature offset 4 (bl firstWhere, mov, ldur). The firstWhere result
        // is only ever consumed through the stur into [x29,#-8], so dropping the call is safe. We move
        // the context reload up so x0 is still live for the JSON read that follows, then materialize the
        // const pro UserTier the same way the app does everywhere else (the PP table at slot 0x15728,
        // confirmed as UserTier index 1 "pro"; free is 0x17a78, hidden is 0x2b448):
        //   ldur x0, [x29, #-0x10]    ; context reload moved up (x0 still needed at the next instruction)
        //   add  x1, x27, #0x15, lsl #12
        //   ldr  x1, [x1, #0x728]     ; x1 = const UserTier 'pro' (PP+0x15728)
        // The kept stur x1, [x29, #-8] then saves pro, so the constructor stores tier=pro. The membership
        // allocation and the other field stores are untouched, and the edit is length preserving (no new
        // bl; the dropped firstWhere is why an earlier build that dropped the allocate stub SIGSEGV'd).
        val tierOverwriteOffset = 4
        val tierOverwrite = intArrayOf(
            0xa0, 0x03, 0x5f, 0xf8, // ldur x0, [x29, #-0x10]
            0x61, 0x57, 0x40, 0x91, // add  x1, x27, #0x15, lsl #12
            0x21, 0x94, 0x43, 0xf9, // ldr  x1, [x1, #0x728]
        ).toByteArray()

        val tierMatch = bytes.findUnique(tierSignature)
            ?: throw PatchException(
                "Membership tier signature not found in $libPath. This patch targets Collectr 2.5.6 " +
                    "(arm64); a different build shifts these offsets and the signature must be " +
                    "re-derived with Blutter.",
            )
        tierOverwrite.forEachIndexed { i, b -> bytes[tierMatch + tierOverwriteOffset + i] = b }

        // --- Kill the forced "Update App Required" dialog --------------------------------------------
        // 24-byte anchor at the top of CollectrAppUpdater.showUpgradeDialogIfNeeded (0xb418b0), covering
        // its async null-check and the return-null tail that the null case already uses. This window is
        // unique in the 2.5.6 libapp.so (the b to ReturnAsyncNotFuture and the bl to PackageInfo
        // .fromPlatform pin it):
        //   ldur x0, [x29, #-0x20]    ; x0 = the server update payload   (kept)
        //   cmp  w0, NULL                                                (kept)
        //   b.ne #0xb418c4            ; if payload != null, run the version check  (replaced with NOP)
        //   mov  x0, NULL             ; return-null path                 (kept)
        //   b    ReturnAsyncNotFuture ; return                           (kept)
        //   bl   PackageInfo.fromPlatform                                (kept, anchor only)
        // NOP-ing the b.ne makes the method always fall through to its existing "no payload -> return"
        // path, so it never reaches the version comparison or showDialog. The one caller (main startup)
        // ignores the result, so returning early has no other effect. Length preserving; no new bl.
        val updateSignature = intArrayOf(
            0xa0, 0x03, 0x5e, 0xf8, // ldur x0, [x29, #-0x20]
            0x1f, 0x00, 0x16, 0x6b, // cmp  w0, NULL
            0x61, 0x00, 0x00, 0x54, // b.ne #0xb418c4
            0xe0, 0x03, 0x16, 0xaa, // mov  x0, NULL
            0xe0, 0x14, 0xeb, 0x17, // b    ReturnAsyncNotFuture
            0xe1, 0x0b, 0xf8, 0x97, // bl   PackageInfo.fromPlatform
        ).toByteArray()

        val updateBranchOffset = 8
        val nop = intArrayOf(0x1f, 0x20, 0x03, 0xd5).toByteArray() // nop

        val updateMatch = bytes.findUnique(updateSignature)
            ?: throw PatchException(
                "Force-update signature not found in $libPath. This patch targets Collectr 2.5.6 " +
                    "(arm64); a different build shifts these offsets and the signature must be " +
                    "re-derived with Blutter.",
            )
        nop.forEachIndexed { i, b -> bytes[updateMatch + updateBranchOffset + i] = b }

        lib.writeBytes(bytes)
    }
}

private fun IntArray.toByteArray(): ByteArray = map { it.toByte() }.toByteArray()

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
            throw PatchException("Collectr libapp.so signature is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
