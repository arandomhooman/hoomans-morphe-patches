package hooman.morphe.patches.finch.guardian

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// Finch is a Flutter app, so the Guardian logic is AOT-compiled into lib/arm64-v8a/libapp.so, not the
// DEX. "Guardian" is the giver side of sponsorship (you are sponsoring someone else's Plus), and it is
// reported by two source-of-truth getters in finch/model/guardian/state.dart that every guardian
// surface reads through:
//
//   getUserGuardianStatus() @ 0x1349148 -> GuardianStatus enum (active / alumnus / null)
//   getUserGuardianTier()   @ 0x1349298 -> GuardianTier   enum (ally / protector / champion /
//                                                                superhero / null)
//
// Both call finchSettingsManager.getSetting(FinchSettingType) and convert the returned string to an
// enum. For a free user the setting is absent, so each returns null and no guardian state shows. (The
// app's own GUARDIAN .env override is a different FinchSettingType than these read, so it does not
// drive these getters.)
//
// We overwrite each getter with a three-instruction leaf that returns the desired enum object straight
// from the object pool and returns. The overwrite starts at the function entry: we skip the EnterFrame
// entirely, so x30 (the return address) is never clobbered and the trailing ret is balanced. The
// instructions are ones the binary already emits (a pool load and a ret), so there is no new bl and no
// new pool materialization, which is the relocation class most likely to crash at runtime.
//
//   getUserGuardianTier()   -> Champion tier  (pp+0x3a1f0, Obj!GuardianTier   "champion")
//   getUserGuardianStatus() -> Active  status (pp+0x3a218, Obj!GuardianStatus "active")
//
// The pool objects were confirmed in Blutter's pp.txt:
//   [pp+0x3a1f0] Obj!GuardianTier   { off_8: int(0x2), off_10: "champion" }
//   [pp+0x3a218] Obj!GuardianStatus { off_8: int(0x0), off_10: "active"   }
//
// The bytes are machine code, so the addresses and the object-pool slots shift between releases; this
// is pinned to the 3.73.181 arm64 build and must be re-derived per version with Blutter.
@Suppress("unused")
val unlockGuardianPatch = rawResourcePatch(
    name = "Unlock Guardian",
    description = "Unlocks Guardian sponsor status without sponsoring anyone, showing you as an active " +
        "Champion-tier guardian across the app's guardian surfaces. This is the arm64 build. " +
        "Re-signing breaks Google sign-in, so log in with email instead.",
) {
    compatibleWith(
        Compatibility(
            name = "Finch",
            packageName = "com.finch.finch",
            appIconColor = 0xBFC2D0,
            targets = listOf(AppTarget("3.73.181")),
        ),
    )

    execute {
        val libPath = "lib/arm64-v8a/libapp.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            throw PatchException(
                "$libPath not found in the APK. This targets the arm64 Finch 3.73.181 build; apk-pure " +
                    "often serves a v7a-only bundle, so apply this to a merged arm64 universal built " +
                    "from a Play Store .apks export or the xapk split bundle (it carries " +
                    "split_config.arm64_v8a.apk). Merge it to a universal with APKEditor m first.",
            )
        }

        val bytes = lib.readBytes()

        // getUserGuardianStatus() @ 0x1349148. Bytes 0..51 are the finchSettingsManager late-static-init
        // boilerplate shared with isUserSubscribed and ~dozens of other getters, so the signature spans
        // through the FinchSettingType pool load that is specific to the status setting (10 ca 40 f9,
        // ldr x16, [x16, #..] -> pp+0xc190). Unique in the 3.73.181 libapp.so.
        //   stp  x29, x30, [x15, #-0x10]!   ; EnterFrame              (signature[0..4]; overwrite here)
        //   mov  x29, x15
        //   ...stack-overflow check, finchSettingsManager init, FinchSettingType load...
        val statusSig = intArrayOf(
            0xfd, 0x79, 0xbf, 0xa9, 0xfd, 0x03, 0x0f, 0xaa,
            0xef, 0x61, 0x00, 0xd1, 0x50, 0x1f, 0x40, 0xf9,
            0xff, 0x01, 0x10, 0xeb, 0xa9, 0x02, 0x00, 0x54,
            0x40, 0x37, 0x40, 0xf9, 0x00, 0x3c, 0x52, 0xf9,
            0x70, 0x23, 0x40, 0xf9, 0x1f, 0x00, 0x10, 0x6b,
            0x81, 0x00, 0x00, 0x54, 0x62, 0x23, 0x40, 0x91,
            0x42, 0xe4, 0x41, 0xf9, 0xcd, 0x70, 0x4b, 0x94,
            0x70, 0xff, 0x41, 0xf9, 0xe0, 0xc1, 0x00, 0xa9,
            0x70, 0x33, 0x40, 0x91, 0x10, 0xca, 0x40, 0xf9,
        ).map { it.toByte() }.toByteArray()

        // Overwrite the entry with a constant return of the Active GuardianStatus at pp+0x3a218.
        //   add  x0, x27, #0x3a, lsl #12   ; PP base region (x27 is the object pool)
        //   ldr  x0, [x0, #0x218]          ; x0 = Obj!GuardianStatus "active"
        //   ret
        val statusOverwriteAt = 0
        val statusOverwrite = intArrayOf(
            0x60, 0xeb, 0x40, 0x91,
            0x00, 0x0c, 0x41, 0xf9,
            0xc0, 0x03, 0x5f, 0xd6,
        ).map { it.toByte() }.toByteArray()

        // getUserGuardianTier() @ 0x1349298. Same shared prologue; the signature spans through the tier
        // FinchSettingType load (10 be 40 f9, ldr x16, [x16, #..] -> pp+0xc178), which distinguishes it
        // from the status getter. Unique in the 3.73.181 libapp.so.
        val tierSig = intArrayOf(
            0xfd, 0x79, 0xbf, 0xa9, 0xfd, 0x03, 0x0f, 0xaa,
            0xef, 0x61, 0x00, 0xd1, 0x50, 0x1f, 0x40, 0xf9,
            0xff, 0x01, 0x10, 0xeb, 0xa9, 0x02, 0x00, 0x54,
            0x40, 0x37, 0x40, 0xf9, 0x00, 0x3c, 0x52, 0xf9,
            0x70, 0x23, 0x40, 0xf9, 0x1f, 0x00, 0x10, 0x6b,
            0x81, 0x00, 0x00, 0x54, 0x62, 0x23, 0x40, 0x91,
            0x42, 0xe4, 0x41, 0xf9, 0x79, 0x70, 0x4b, 0x94,
            0x70, 0xff, 0x41, 0xf9, 0xe0, 0xc1, 0x00, 0xa9,
            0x70, 0x33, 0x40, 0x91, 0x10, 0xbe, 0x40, 0xf9,
        ).map { it.toByte() }.toByteArray()

        // Overwrite the entry with a constant return of the Champion GuardianTier at pp+0x3a1f0.
        //   add  x0, x27, #0x3a, lsl #12
        //   ldr  x0, [x0, #0x1f0]          ; x0 = Obj!GuardianTier "champion"
        //   ret
        val tierOverwriteAt = 0
        val tierOverwrite = intArrayOf(
            0x60, 0xeb, 0x40, 0x91,
            0x00, 0xf8, 0x40, 0xf9,
            0xc0, 0x03, 0x5f, 0xd6,
        ).map { it.toByte() }.toByteArray()

        // Both are mandatory: same build, same version pin, so a missing or ambiguous signature means
        // the input isn't the expected 3.73.181 arm64 libapp.so. Fail loud rather than apply a partial
        // unlock.
        listOf(
            Triple("getUserGuardianStatus", statusSig, statusOverwrite to statusOverwriteAt),
            Triple("getUserGuardianTier", tierSig, tierOverwrite to tierOverwriteAt),
        ).forEach { (label, signature, overwriteSpec) ->
            val (overwrite, overwriteAt) = overwriteSpec
            val match = bytes.findUnique(signature)
                ?: throw PatchException(
                    "Finch Guardian signature ($label) not found in $libPath. This patch targets Finch " +
                        "3.73.181 (arm64); a different build shifts these offsets and the object-pool " +
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
            throw PatchException("Finch Guardian signature is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}
