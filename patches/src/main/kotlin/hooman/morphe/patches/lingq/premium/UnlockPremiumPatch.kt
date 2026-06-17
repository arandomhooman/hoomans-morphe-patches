package hooman.morphe.patches.lingq.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks LingQ's Premium and Premium Plus tiers without a subscription. Membership " +
        "is decided on-device from the account tier and the cached subscription details, so forcing " +
        "the premium check and the cached Premium/Premium Plus flags turns on the locally-gated " +
        "features. Lesson content, courses, and anything LingQ generates or counts on its servers " +
        "(imports, AI, sync quotas) is validated server-side and stays tied to the real account.",
) {
    compatibleWith(
        Compatibility(
            name = "LingQ",
            packageName = "com.linguist",
            appIconColor = 0x3070E0,
            targets = listOf(AppTarget("6.1.23")),
        ),
    )

    execute {
        // ProfileAccount.b() is the app-wide premium check: (childAccountId == null && tier.title !=
        // "FREE"). Class names survive R8, so pin the class and force the lone no-arg ()Z to true.
        val profile = mutableClassDefByOrNull("Lcom/lingq/core/domain/model/user/ProfileAccount;")
            ?: throw PatchException("LingQ: ProfileAccount model not found; the package layout changed.")
        val isPremium = profile.methods.singleOrNull { method ->
            method.name == "b" && method.returnType == "Z" && method.parameterTypes.isEmpty()
        } ?: throw PatchException("LingQ: ProfileAccount premium check b()Z not found uniquely.")
        isPremium.addInstructions(0, "const/4 v0, 0x1\nreturn v0")

        // SubscriptionDetails holds the cached flags the account screen and Plus-only gates read:
        // p = isPremium, q = isPremiumPlus, h = isActive (all Boolean). Seed them true at the source so
        // every read, including a fresh server response, sees an active Plus member. Only the two
        // seedable ctors need it (the no-arg default and the kotlinx-serialization one, int bitmask
        // first); the primary ctor is .locals 0 with no free register, but copy() carries the seeded
        // values forward.
        val sub = mutableClassDefByOrNull("Lcom/lingq/core/domain/model/user/SubscriptionDetails;")
            ?: throw PatchException("LingQ: SubscriptionDetails model not found; the layout changed.")
        val seedCtors = sub.methods.filter { method ->
            method.name == "<init>" && method.returnType == "V" &&
                (method.parameterTypes.isEmpty() || method.parameterTypes.firstOrNull()?.toString() == "I")
        }
        if (seedCtors.isEmpty()) {
            throw PatchException("LingQ: no seedable SubscriptionDetails constructor found.")
        }
        val forceFlags =
            "sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;\n" +
                "iput-object v0, p0, Lcom/lingq/core/domain/model/user/SubscriptionDetails;->p:Ljava/lang/Boolean;\n" +
                "iput-object v0, p0, Lcom/lingq/core/domain/model/user/SubscriptionDetails;->q:Ljava/lang/Boolean;\n" +
                "iput-object v0, p0, Lcom/lingq/core/domain/model/user/SubscriptionDetails;->h:Ljava/lang/Boolean;"
        seedCtors.forEach { ctor ->
            val returnIndices = ctor.instructions
                .withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }
                .sortedDescending()
            if (returnIndices.isEmpty()) {
                throw PatchException("LingQ: SubscriptionDetails ctor has no return-void to seed.")
            }
            returnIndices.forEach { index -> ctor.addInstructions(index, forceFlags) }
        }
    }
}
