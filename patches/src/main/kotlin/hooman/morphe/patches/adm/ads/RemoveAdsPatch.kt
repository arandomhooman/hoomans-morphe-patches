package hooman.morphe.patches.adm.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Removes the ads in Advanced Download Manager. ADM's paid state is the ad-free " +
        "RevenueCat entitlement cached on the device, so the app also labels itself Pro.",
) {
    compatibleWith(
        Compatibility(
            name = "Advanced Download Manager",
            packageName = "com.dv.adm",
            appIconColor = 0x1E88E5,
            targets = listOf(AppTarget("14.0.39")),
        ),
    )

    execute {
        fun stringLiterals(classDef: com.android.tools.smali.dexlib2.iface.ClassDef): Set<String> =
            classDef.methods.flatMap { method ->
                method.implementation?.instructions?.mapNotNull { instruction ->
                    (instruction as? ReferenceInstruction)?.reference
                        ?.let { it as? StringReference }?.string
                }.orEmpty()
            }.toSet()

        val adsClassDef = classDefByStrings("goog_bUMUOXFxvLTUOlswTdyrgasIoSN").singleOrNull()
            ?: throw PatchException(
                "ADM: ad/purchase class pinned by the RevenueCat key was not found uniquely. " +
                    "Re-derive the ads gate.",
            )
        val adsClassStrings = stringLiterals(adsClassDef)
        if ("Disable Ads" !in adsClassStrings || "hua_voices" !in adsClassStrings) {
            throw PatchException(
                "ADM: the RevenueCat-key class is missing the expected entitlement/premium-pref " +
                    "literals. Re-derive the ads gate.",
            )
        }
        val adsClass = mutableClassDefBy(adsClassDef)
        val adsEnabledField = "${adsClass.type}->a:Z"

        val classesWithPremiumPref = classDefByStrings("hua_voices")
        if (classesWithPremiumPref.size != 3) {
            throw PatchException(
                "ADM: expected exactly 3 classes containing the \"hua_voices\" premium pref, found " +
                    "${classesWithPremiumPref.size}. Re-derive the launch-time ads gate writers.",
            )
        }

        val patchedStores = classesWithPremiumPref.sumOf { classDef ->
            mutableClassDefBy(classDef).methods.sumOf { method ->
                val stores = method.instructions.withIndex().filter { (_, instruction) ->
                    (instruction as? ReferenceInstruction)?.reference?.toString() == adsEnabledField
                }.filter { (_, instruction) ->
                    instruction.opcode.name == "sput-boolean"
                }.toList()

                stores.asReversed().forEach { (index, instruction) ->
                    val register = (instruction as OneRegisterInstruction).registerA
                    method.addInstructions(index, "const/4 v$register, 0x0")
                }
                stores.size
            }
        }
        if (patchedStores != 5) {
            throw PatchException(
                "ADM: expected exactly 5 writes to $adsEnabledField from the premium pref/entitlement " +
                    "paths, found $patchedStores. Re-derive before patching.",
            )
        }

        val classInitializer = adsClass.methods.singleOrNull { it.name == "<clinit>" }
            ?: throw PatchException("ADM: ${adsClass.type}.<clinit> not found.")
        classInitializer.addInstructions(
            classInitializer.instructions.lastIndex,
            """
                const/4 v0, 0x0
                sput-boolean v0, $adsEnabledField
            """,
        )
    }
}
