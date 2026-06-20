package hooman.morphe.patches.inshot.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
    description = "Unlocks InShot's Pro features without a subscription: no export watermark, no ads, " +
        "and the paid filters, effects, transitions, stickers and HD export open. The on-device AI " +
        "tools (background remover, AI retouch, auto captions) verify the original signing key in " +
        "native code, so those may not run on a re-signed build; the rest of Pro works offline.",
) {
    compatibleWith(
        Compatibility(
            name = "InShot",
            packageName = "com.camerasideas.instashot",
            appIconColor = 0xFF2558,
            targets = listOf(AppTarget("2.214.1539")),
        ),
    )

    execute {
        // Whole-app Pro check funnels through one read in UserManager (obfuscated to class T): w()Z.
        // The per-SKU check m(sku) calls w() first, so forcing w() true unlocks every m()-gated feature
        // (watermark, ads via k() = !m("remove.ads"), the paid packs, HD export) plus the ~120 direct
        // callers. Only Play Billing writes the backing state (never on a free account) and w() re-reads
        // prefs each call, so this is reset-proof and live. Pin the class by its unique pref key
        // "SubscribeProOfHw" (R8 keeps pref literals), then pick w() as the lone instance ()Z reading "DebugPro".
        val userManagerDef = classDefByStrings("SubscribeProOfHw")
            .singleOrNull()
            ?: throw PatchException(
                "InShot: UserManager (the Pro gate) not found by its unique pref key. The " +
                    "obfuscation map changed; re-derive the chokepoint.",
            )
        val userManager = mutableClassDefBy(userManagerDef)

        val isProGate = userManager.methods.singleOrNull { method ->
            !AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "Z" &&
                method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.any { instruction ->
                    (instruction as? ReferenceInstruction)?.reference
                        ?.let { it as? StringReference }
                        ?.string == "DebugPro"
                } == true
        } ?: throw PatchException(
            "InShot: the Pro decision w()Z (the lone instance ()Z reading \"DebugPro\") was not " +
                "found uniquely in UserManager. The gate shape changed.",
        )

        isProGate.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
