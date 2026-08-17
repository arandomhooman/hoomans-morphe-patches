package hooman.morphe.patches.autozen.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.autozen.checks.disableChecksPatch

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock premium",
    description = "Unlocks AutoZen's premium features (all tiers and lifetime), which also removes the " +
        "ads since the ad manager only shows ads to non-premium users. Anything AutoZen verifies on its " +
        "own servers is not affected.",
) {
    dependsOn(disableChecksPatch)

    compatibleWith(
        Compatibility(
            name = "AutoZen",
            packageName = "com.zenthek.autozen",
            appIconColor = 0x1B1B1B,
            targets = listOf(AppTarget("8.0.10")),
        ),
    )

    execute {
        // Every feature gate (28 of them) and the ad manager read entitlement through PremiumManager,
        // whose sole impl is PremiumManagerImpl. Force its tier booleans true so all features unlock and
        // AdManagerImpl.shouldDisplayAds()/... short-circuit to false. The app packages are not
        // obfuscated, so pin by descriptor.
        val premiumImpl = mutableClassDefByOrNull(
            "Lcom/zenthek/domain/inappbilling/PremiumManagerImpl;",
        ) ?: throw PatchException(
            "AutoZen: PremiumManagerImpl not found. The billing layer changed.",
        )

        val gates = listOf(
            "itHasAnyPremium", "isPremium", "isPremiumPlus", "isBasicPremium", "itHasLifeTime",
        )
        for (name in gates) {
            val method = premiumImpl.methods.firstOrNull {
                it.name == name && it.returnType == "Z" && it.parameterTypes.isEmpty()
            } ?: throw PatchException(
                "AutoZen: PremiumManagerImpl.$name()Z not found. Re-derive the premium gates.",
            )
            method.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """,
            )
        }
    }
}
