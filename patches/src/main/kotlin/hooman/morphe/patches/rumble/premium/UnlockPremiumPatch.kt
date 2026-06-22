package hooman.morphe.patches.rumble.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Turns on Rumble Premium without a subscription: removes the ad-supported playback, " +
        "drops the premium upsell, and unlocks the premium-only videos. The premium flag is read from " +
        "your account profile when you sign in, so it takes effect on a logged-in Rumble account.",
) {
    compatibleWith(
        Compatibility(
            name = "Rumble",
            packageName = "com.rumble.battles",
            // Rumble's brand green.
            appIconColor = 0x85C742,
            targets = listOf(AppTarget("3.14.5")),
        ),
    )

    execute {
        // Premium is a server entitlement: UserProfile.is_premium comes down in the account payload,
        // FetchUserInfoUseCase hands it to SessionManager.saveIsPremiumUser, and it's cached in DataStore
        // under "isUserPremiumKey" and read back as the isPremiumUserFlow that every on-device gate
        // checks (the player's HasPremiumRestrictionUseCase, the live-gate check, the raid popup). Force
        // the is_premium getter true so the cached flag is written true on each profile fetch and every
        // gate sees premium. That fetch only runs for a signed-in account, so the unlock takes effect
        // logged in (logged out, no profile is fetched and nothing is written). Only that fetch writes
        // the flag (false for a free account), so this holds across restarts. Pin UserProfile by its real
        // name (a Gson DTO, so R8 keeps the class) and pick the lone no-arg ()Z getter, which is
        // is_premium (the only boolean field on the model).
        val userProfile = mutableClassDefByOrNull("Lcom/rumble/network/dto/profile/UserProfile;")
            ?: throw PatchException(
                "Rumble: UserProfile DTO not found. This patch targets 3.14.5; the profile model " +
                    "package changed and the chokepoint must be re-derived.",
            )

        val isPremiumGetter = userProfile.methods.singleOrNull { method ->
            !AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "Z" &&
                method.parameterTypes.isEmpty()
        } ?: throw PatchException(
            "Rumble: the is_premium getter (the lone no-arg ()Z on UserProfile) was not found " +
                "uniquely. The DTO gained another boolean getter; re-derive which one is is_premium.",
        )

        isPremiumGetter.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
