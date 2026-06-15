package hooman.morphe.patches.quizlet.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val unlockPlusPatch = bytecodePatch(
    name = "Unlock Plus",
    description = "Removes ads and unlocks the locally-gated Quizlet Plus features by forcing the " +
        "account's upgrade type to Plus. Plus status and ad display are decided on-device from a " +
        "cached flag, so this works without a subscription. Server-side Plus features (AI \"Magic " +
        "Notes\"/generation and other cloud/metered tools) are validated and produced on Quizlet's " +
        "servers and stay locked.",
) {
    compatibleWith(
        Compatibility(
            name = "Quizlet",
            packageName = "com.quizlet.quizletandroid",
            appIconColor = 0x4255FF,
            targets = listOf(AppTarget("10.38.1")),
        ),
    )

    execute {
        // Plus status + ad display are decided on-device from DBUser.userUpgradeType
        // (0=free, 1/3=Plus, 2=teacher). getUserUpgradeType() is the master local getter, and the
        // canonical "is this user free" check is isFreeUser = (getUserUpgradeType() == 0). Forcing
        // the upgrade type to PLUS(1) flips isFreeUser false app-wide, which removes ads and unlocks
        // every locally-gated Plus surface. Server-side metered/AI features
        // (RemoteEntitlementData.canUseFeature + metering) are unaffected and remain locked.

        // Primary chokepoint — DBUser.getUserUpgradeType() -> 1 (PLUS). DBUser is a JSON/OrmLite
        // model, so its class + method names survive R8 (they must match the server JSON and DB
        // columns); pin it by exact type and match the getter by name + shape (no-arg, returns int).
        // Fail loudly if the shape changed.
        val dbUser = mutableClassDefByOrNull("Lcom/quizlet/db/data/models/persisted/DBUser;")
            ?: throw PatchException("Quizlet: DBUser class not found — model package changed.")
        val upgradeTypeGetter = dbUser.methods.firstOrNull { method ->
            method.name == "getUserUpgradeType" &&
                method.returnType == "I" &&
                method.parameterTypes.isEmpty()
        }
            ?: throw PatchException(
                "Quizlet: DBUser.getUserUpgradeType()I not found — upgrade-type gate shape changed.",
            )
        upgradeTypeGetter.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )

        // Belt-and-suspenders — LoggedInUserStatusKt.isFreeUser(LoggedInUserStatus) -> false, in
        // case R8 inlined getUserUpgradeType() at the canonical free-user check. Best-effort: the
        // method calls the (now-forced) getter, so this is redundant when not inlined; only patch it
        // if present, don't fail the whole patch if the obfuscation moved it.
        val isFreeUser = mutableClassDefByOrNull(
            "Lcom/quizlet/db/data/models/wrappers/LoggedInUserStatusKt;",
        )?.methods?.firstOrNull { method ->
            method.name == "isFreeUser" &&
                method.returnType == "Z" &&
                method.parameterTypes.size == 1
        }
        isFreeUser?.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )
    }
}
