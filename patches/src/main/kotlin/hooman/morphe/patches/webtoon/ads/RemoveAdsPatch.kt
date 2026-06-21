package hooman.morphe.patches.webtoon.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Removes the ads WEBTOON shows while reading and browsing: the in-viewer top and end " +
        "ads, the home feed ads, and the title-page banner. Locked episodes (Fast Pass, Daily Pass, " +
        "coin-unlocked) are served by WEBTOON's servers and still need coins or a wait, so this does " +
        "not unlock them.",
) {
    compatibleWith(
        Compatibility(
            name = "WEBTOON",
            packageName = "com.naver.linewebtoon",
            appIconColor = 0x00DC64,
            targets = listOf(AppTarget("3.9.5")),
        ),
    )

    execute {
        // Every ad surface (reader top/end, home feed, title-page banner) gates ad loading on one use
        // case: IsAdFreeUserUseCaseImpl.invoke() = isLoggedIn() && adFreeBenefitCache.isActive(). Forcing
        // it true makes them all skip the ad, even logged out. The benefit is only written from the server
        // response, so a login or refresh can't reset it. (Splash ad is gated by onboarding, not this use
        // case, so it's out of scope.) R8 flattens the class + method to one-letter names, so pin it by the
        // one survivor: the only class that holds an AdFreeBenefitStatusV2Cache field AND a no-arg ()Z method.
        val cacheType =
            "Lcom/naver/linewebtoon/feature/coin/impl/mycoin/cache/AdFreeBenefitStatusV2Cache;"

        var patched = 0
        classDefForEach { classDef ->
            val hasCacheField = classDef.fields.any { it.type == cacheType }
            if (!hasCacheField) return@classDefForEach

            val isAdFree = classDef.methods.firstOrNull { method ->
                method.name == "invoke" &&
                    method.returnType == "Z" &&
                    method.parameterTypes.isEmpty()
            } ?: return@classDefForEach

            mutableClassDefBy(classDef).methods
                .first { it.name == isAdFree.name && it.parameterTypes == isAdFree.parameterTypes }
                .addInstructions(
                    0,
                    """
                        const/4 v0, 0x1
                        return v0
                    """,
                )
            patched++
        }

        if (patched == 0) {
            throw PatchException(
                "WEBTOON: the IsAdFreeUser use case (a class with an AdFreeBenefitStatusV2Cache field " +
                    "and a no-arg ()Z invoke) was not found. The ad-free gate shape changed; re-derive " +
                    "before shipping.",
            )
        }
    }
}
