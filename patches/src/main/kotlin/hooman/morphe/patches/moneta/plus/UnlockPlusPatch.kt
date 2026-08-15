package hooman.morphe.patches.moneta.plus

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import hooman.morphe.patches.moneta.license.disableLicenseCheckPatch

// Moneta is a Flutter app. Its RevenueCat CustomerInfo is mapped in SubscriptionRepository to a
// local premium Future, a BehaviorSubject used by live feature gates, and a SharedPreferences cache.
// Converter limits, rates, charts, the camera price lens, settings, and banners all consume one of
// those three local views. The edits below keep every local view true:
//
// 1. seed the premium BehaviorSubject with true;
// 2. make getCachedIsPremium() return true;
// 3. publish true to the subject in _applyCustomerInfo(); and
// 4. make _loadPremium() return true after customer-info loading succeeds or throws.
//
// _applyCustomerInfo() still caches, mirrors, and returns the real RevenueCat value. This avoids
// writing a forged premium flag to Moneta's Firestore data and keeps purchase/restore outcomes real;
// only the local _loadPremium() caller and subject publication are forced true. The signatures are
// pinned to the 1.2.2 arm64 libapp.so and must be re-derived with Blutter for a different build.
@Suppress("unused")
val unlockPlusPatch = rawResourcePatch(
    name = "Unlock Plus",
    description = "Unlocks Moneta Plus features, including the full asset catalog, more than five " +
        "selected assets, five-minute rate updates, camera price scanning, and extended historical " +
        "charts. Purchases, restore purchases, and server account state are unchanged. This is the " +
        "arm64 build.",
) {
    dependsOn(disableLicenseCheckPatch)

    compatibleWith(
        Compatibility(
            name = "Moneta",
            packageName = "currency.converter.moneta",
            appIconColor = 0x4BBA17,
            targets = listOf(AppTarget("1.2.2")),
        ),
    )

    execute {
        val libPath = "lib/arm64-v8a/libapp.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            throw PatchException(
                "$libPath not found in the APK. This patch targets the arm64 Moneta 1.2.2 build. " +
                    "Apply it to a merged universal APK that includes split_config.arm64_v8a.apk.",
            )
        }

        val bytes = lib.readBytes()

        // SubscriptionRepository::_applyCustomerInfo() @ 0x47906c. This signature starts at
        // 0x4790fc, after the RevenueCat "Plus" entitlement lookup, and spans the local cache,
        // BehaviorSubject publish, Firebase mirror, and return. It is unique in 1.2.2.
        val applyCustomerInfoSig = intArrayOf(
            0xe3, 0x03, 0x00, 0xaa, 0xa0, 0x03, 0x5f, 0xf8,
            0xe1, 0x03, 0x00, 0xaa, 0xe2, 0x03, 0x03, 0xaa,
            0xa3, 0x83, 0x1f, 0xf8, 0xaf, 0x00, 0x00, 0x94,
            0xa0, 0x03, 0x5f, 0xf8, 0x01, 0x30, 0x42, 0xb8,
            0x21, 0x80, 0x1c, 0x8b, 0xa2, 0x83, 0x5f, 0xf8,
            0xe1, 0x1b, 0x0e, 0x94, 0xa0, 0x03, 0x5f, 0xf8,
            0x01, 0x70, 0x40, 0xb8, 0x21, 0x80, 0x1c, 0x8b,
            0xbf, 0x9b, 0xff, 0x97, 0x1f, 0x00, 0x16, 0x6b,
            0x61, 0x00, 0x00, 0x54, 0xe2, 0x03, 0x16, 0xaa,
            0x04, 0x00, 0x00, 0x14, 0xe1, 0x03, 0x00, 0xaa,
            0x97, 0x00, 0x00, 0x94, 0xe2, 0x03, 0x00, 0xaa,
            0x5f, 0x00, 0x16, 0x6b, 0x80, 0x00, 0x00, 0x54,
            0xa1, 0x03, 0x5f, 0xf8, 0xa3, 0x83, 0x5f, 0xf8,
            0x07, 0x00, 0x00, 0x94, 0xa0, 0x83, 0x5f, 0xf8,
            0xef, 0x03, 0x1d, 0xaa, 0xfd, 0x79, 0xc1, 0xa8,
            0xc0, 0x03, 0x5f, 0xd6,
        ).map { it.toByte() }.toByteArray()

        // SubscriptionRepository constructor @ 0x506444. The target at signature offset 0x1c is
        // `mov x2, NULL`, the seed passed to BehaviorSubject<bool?>. Replace it with true.
        val premiumSubjectSeedSig = intArrayOf(
            0x50, 0x27, 0x40, 0xf9, 0xff, 0x01, 0x10, 0xeb,
            0x69, 0x10, 0x00, 0x54, 0xc0, 0x30, 0x03, 0xb8,
            0xc0, 0xb0, 0x04, 0xb8, 0xc0, 0x30, 0x05, 0xb8,
            0x61, 0x7b, 0x5b, 0xf9, 0xe2, 0x03, 0x16, 0xaa,
            0x82, 0x00, 0x00, 0x94, 0xa2, 0x83, 0x5f, 0xf8,
        ).map { it.toByte() }.toByteArray()

        // SubscriptionRepository::getCachedIsPremium() @ 0x71fa5c. The signature includes the
        // SharedPreferences containsKey/getBool path. Replace its stack allocation at offset 8 with
        // a constant-true return, using the function's own frame teardown.
        val cachedPremiumSig = intArrayOf(
            0xfd, 0x79, 0xbf, 0xa9, 0xfd, 0x03, 0x0f, 0xaa,
            0xef, 0x21, 0x00, 0xd1, 0x50, 0x27, 0x40, 0xf9,
            0xff, 0x01, 0x10, 0xeb, 0xe9, 0x01, 0x00, 0x54,
            0x20, 0xf0, 0x40, 0xb8, 0x00, 0x80, 0x1c, 0x8b,
            0xe1, 0x03, 0x00, 0xaa, 0xa0, 0x83, 0x1f, 0xf8,
            0x0c, 0x00, 0x00, 0x94, 0xa0, 0x00, 0x20, 0x37,
        ).map { it.toByte() }.toByteArray()

        // SubscriptionRepository::_loadPremium() @ 0x478f3c. On success, it calls
        // _applyCustomerInfo() at 0x478fcc and returns that helper's real entitlement. Redirect the
        // return branch at signature offset 0x10 to the handled fallback's patched true return. The
        // helper still runs, so its real cache/mirror writes and local subject publication remain.
        val premiumLoadSuccessSig = intArrayOf(
            0xa1, 0x03, 0x5a, 0xf8, 0xe2, 0x03, 0x00, 0xaa,
            0xa0, 0x03, 0x19, 0xf8, 0x28, 0x00, 0x00, 0x94,
            0x50, 0x9e, 0xfe, 0x17, 0xaf, 0x23, 0x02, 0xd1,
            0xa0, 0x03, 0x1a, 0xf8, 0x82, 0x07, 0x80, 0xd2,
        ).map { it.toByte() }.toByteArray()

        // SubscriptionRepository::_loadPremium() @ 0x478f3c. This signature is the handled
        // RevenueCat exception path and its adjacent rethrow block. Replace the handled fallback false
        // at offset 0x38 with true and redirect the otherwise-rethrown exception at offset 0x40 to it.
        val premiumLoadFallbackSig = intArrayOf(
            0x82, 0x00, 0x80, 0xd2, 0x01, 0x43, 0x12, 0x94,
            0x70, 0x27, 0x50, 0xf9, 0x10, 0xf0, 0x00, 0xb8,
            0xa2, 0x03, 0x5a, 0xf8, 0x41, 0xb0, 0x40, 0xb8,
            0x21, 0x80, 0x1c, 0x8b, 0x01, 0x30, 0x01, 0xb8,
            0xe0, 0x01, 0x00, 0xf9, 0xae, 0x41, 0xfe, 0x97,
            0xf6, 0x01, 0x00, 0xf9, 0xe1, 0x03, 0x00, 0xaa,
            0x64, 0x53, 0x40, 0xf9, 0xf6, 0x12, 0xff, 0x97,
            0xc0, 0xc2, 0x00, 0x91, 0x30, 0x9e, 0xfe, 0x17,
            0xe2, 0x03, 0x00, 0xaa, 0xe0, 0x03, 0x02, 0xaa,
            0x66, 0x3b, 0x12, 0x94, 0x00, 0x00, 0x20, 0xd4,
        ).map { it.toByte() }.toByteArray()

        // Resolve every required signature before changing the file, so a wrong build cannot receive
        // a partial native patch.
        val applyCustomerInfoAt = bytes.findUnique(applyCustomerInfoSig, "_applyCustomerInfo")
            ?: throw signatureNotFound("_applyCustomerInfo", libPath)
        val premiumSubjectSeedAt = bytes.findUnique(premiumSubjectSeedSig, "premium subject seed")
            ?: throw signatureNotFound("premium subject seed", libPath)
        val cachedPremiumAt = bytes.findUnique(cachedPremiumSig, "getCachedIsPremium")
            ?: throw signatureNotFound("getCachedIsPremium", libPath)
        val premiumLoadSuccessAt = bytes.findUnique(
            premiumLoadSuccessSig,
            "_loadPremium success",
        ) ?: throw signatureNotFound("_loadPremium success", libPath)
        val premiumLoadFallbackAt = bytes.findUnique(
            premiumLoadFallbackSig,
            "_loadPremium fallback",
        ) ?: throw signatureNotFound("_loadPremium fallback", libPath)

        val trueX0 = intArrayOf(0xc0, 0x82, 0x00, 0x91).toBytes()
        val trueX2 = intArrayOf(0xc2, 0x82, 0x00, 0x91).toBytes()
        val trueReturn = intArrayOf(
            0xc0, 0x82, 0x00, 0x91, // add x0, x22, #0x20: true
            0xef, 0x03, 0x1d, 0xaa, // mov x15, x29: restore SP from FP
            0xfd, 0x79, 0xc1, 0xa8, // ldp x29, x30, [x15], #0x10
            0xc0, 0x03, 0x5f, 0xd6, // ret
        ).toBytes()
        val premiumLoadSuccessBranchAt = premiumLoadSuccessAt + 0x10
        val premiumLoadFallbackTrueAt = premiumLoadFallbackAt + 0x38
        val premiumLoadRethrowAt = premiumLoadFallbackAt + 0x40
        val branchToTrueFallback = encodeUnconditionalBranch(
            premiumLoadSuccessBranchAt,
            premiumLoadFallbackTrueAt,
        )
        val rethrowToTrueFallback = encodeUnconditionalBranch(
            premiumLoadRethrowAt,
            premiumLoadFallbackTrueAt,
        )

        // Publish true only to local feature gates. _applyCustomerInfo() keeps its real return for
        // purchase and restore outcomes, and its real fp-8 value for SharedPreferences and Firestore.
        bytes.overwrite(applyCustomerInfoAt + 0x24, trueX2)
        bytes.overwrite(premiumSubjectSeedAt + 0x1c, trueX2)
        bytes.overwrite(cachedPremiumAt + 8, trueReturn)
        bytes.overwrite(premiumLoadSuccessBranchAt, branchToTrueFallback)
        bytes.overwrite(premiumLoadRethrowAt, rethrowToTrueFallback)
        bytes.overwrite(premiumLoadFallbackTrueAt, trueX0)

        lib.writeBytes(bytes)
    }
}

private fun signatureNotFound(label: String, libPath: String) = PatchException(
    "Moneta Plus signature ($label) not found in $libPath. This patch targets Moneta 1.2.2 " +
        "(arm64); the signature must be re-derived with Blutter for a different build.",
)

private fun ByteArray.findUnique(pattern: ByteArray, label: String): Int? {
    var found: Int? = null
    val last = size - pattern.size
    outer@ for (i in 0..last) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        if (found != null) {
            throw PatchException(
                "Moneta Plus signature ($label) is ambiguous (matched more than once).",
            )
        }
        found = i
    }
    return found
}

private fun encodeUnconditionalBranch(fromOffset: Int, toOffset: Int): ByteArray {
    val delta = toOffset - fromOffset
    if (delta % 4 != 0) {
        throw PatchException("Moneta Plus _loadPremium branch target is not instruction-aligned.")
    }

    val immediate = delta / 4
    if (immediate !in -0x2000000..0x1ffffff) {
        throw PatchException("Moneta Plus _loadPremium branch target is out of AArch64 range.")
    }

    val instruction = 0x14000000 or (immediate and 0x03ffffff)
    return intArrayOf(
        instruction,
        instruction ushr 8,
        instruction ushr 16,
        instruction ushr 24,
    ).map { (it and 0xff).toByte() }.toByteArray()
}

private fun ByteArray.overwrite(offset: Int, replacement: ByteArray) {
    replacement.forEachIndexed { i, byte -> this[offset + i] = byte }
}

private fun IntArray.toBytes() = map { it.toByte() }.toByteArray()
