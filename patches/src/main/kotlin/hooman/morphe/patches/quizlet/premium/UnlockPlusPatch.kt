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
        // Plus and ads are decided on-device from DBUser.userUpgradeType (0=free, 1/3=Plus,
        // 2=teacher); isFreeUser = (type == 0). DBUser is a JSON/OrmLite model so its names survive
        // R8. Force the getter to PLUS(1) to flip isFreeUser false app-wide; server-side AI/metered
        // features are unaffected. Pin by exact type, match the getter by shape; fail loudly if gone.
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

        // Failsafe in case R8 inlined the getter at the free-user check: force isFreeUser(..) false
        // if present. Best-effort — don't fail the patch if obfuscation moved it.
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
