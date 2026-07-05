package hooman.morphe.patches.chatboost.premium

import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Turns on ChatBoost premium so the app's paid features and UI open up. Premium is " +
        "decided by a single internal flag holder whose two flags both ship hardcoded off, so forcing " +
        "them on makes the entitlement flow report premium no matter what Google Play Billing says, and " +
        "every gate reads that one flow. One thing to know: ChatBoost is bring your own key, so the AI " +
        "runs on your own provider API keys and was never part of the subscription. This unlocks the " +
        "client-side paid features and UX, not free AI usage. The AI already runs on your keys.",
) {
    compatibleWith(
        Compatibility(
            name = "ChatBoost",
            packageName = "studio.muggle.chatboost",
            // Sampled from the pastel cloud launcher icon.
            appIconColor = 0xB1A5E6,
            targets = listOf(AppTarget("3.23.0")),
        ),
    )

    execute {
        // The whole premium state collapses to one entitlement flow (xd.b.c(), impl xd.a). That flow is
        // built by a combiner of shape (z11 || z12) ? premiumGrantedConstant : realState, where z11 and
        // z12 come from two flags on a tiny holder class (R8-named wd.a): a debug-bypass flag and a
        // limited-time-free flag, both hardcoded to return false. Flip either flag on and the combiner
        // emits the granted constant (new PremiumEntitlements(true, false, false)) unconditionally,
        // overriding the live billing/DataStore state. So there is exactly one place to touch.
        //
        // wd.a has no unique string of its own, so pin it indirectly. xd.a wraps its two flag methods as
        // Kotlin callable references whose method-name metadata survives R8: the wrapper carrying
        // "isLimitedTimeFreeAccessEnabled" invokes the flag holder's no-arg ()Z flag method. That string
        // is app-specific and stable across releases, so we resolve the wrapper by it, then read the
        // flag holder's type off the ()Z call inside the wrapper.
        val wrapper = classDefByStrings("isLimitedTimeFreeAccessEnabled", StringComparisonType.EQUALS)
            .singleOrNull()
            ?: throw PatchException(
                "ChatBoost: the callable-reference wrapper naming \"isLimitedTimeFreeAccessEnabled\" was " +
                    "not found or was ambiguous. The premium flag wiring changed; re-derive the flag " +
                    "holder.",
            )

        val flagHolderType = wrapper.methods.firstNotNullOfOrNull { method ->
            method.implementation?.instructions?.firstNotNullOfOrNull { instruction ->
                ((instruction as? ReferenceInstruction)?.reference as? MethodReference)
                    ?.takeIf { it.returnType == "Z" && it.parameterTypes.isEmpty() }
                    ?.definingClass
            }
        } ?: throw PatchException(
            "ChatBoost: could not read the flag-holder type from the " +
                "\"isLimitedTimeFreeAccessEnabled\" wrapper. Re-derive the flag holder.",
        )

        val flagHolder = mutableClassDefByOrNull(flagHolderType)
            ?: throw PatchException(
                "ChatBoost: the premium flag holder $flagHolderType was not found. Re-derive.",
            )

        // Force every instance no-arg ()Z flag on the holder to return true (expected: the debug-bypass
        // and limited-time-free flags, two methods). b() alone is enough to trip the combiner; forcing
        // both is belt-and-suspenders, and both are pure hardcoded-false flag getters so nothing else
        // reads them for real state.
        val flags = flagHolder.methods.filter { method ->
            method.returnType == "Z" &&
                method.parameterTypes.isEmpty() &&
                !AccessFlags.STATIC.isSet(method.accessFlags)
        }
        if (flags.isEmpty()) {
            throw PatchException(
                "ChatBoost: no instance no-arg ()Z flag methods on $flagHolderType. The holder shape " +
                    "changed; re-derive.",
            )
        }

        flags.forEach { flag ->
            flag.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """,
            )
        }
    }
}
