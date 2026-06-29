package hooman.morphe.patches.smartaudiobook.full

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val LICENSE_TYPE = "Lak/alizandro/smartaudiobookplayer/Billings\$LicenseType;"
private const val LICENSE_FULL = "c"

@Suppress("unused")
val unlockFullPatch = bytecodePatch(
    name = "Unlock Full",
    description = "Unlocks Smart AudioBook Player's full version by forcing the local license " +
        "decision to Full.",
) {
    compatibleWith(
        Compatibility(
            name = "Smart AudioBook Player",
            packageName = "ak.alizandro.smartaudiobookplayer",
            appIconColor = 0xEF5350,
            targets = listOf(AppTarget("11.7.8")),
        ),
    )

    execute {
        fun Method.stringLiterals(): Set<String> =
            implementation?.instructions?.mapNotNull { instruction ->
                (instruction as? ReferenceInstruction)?.reference
                    ?.let { it as? StringReference }?.string
            }?.toSet().orEmpty()

        // The billing/license class owns the Play product keys and the one license getter. In 11.7.8
        // that getter returns Full only when a purchase/donation flag is present, otherwise Trial or
        // Expired. Every app gate reaches it directly or through PlayerService.m1(), so force this
        // single read to Full and leave the billing callbacks and stored prefs alone. The enum fields
        // are obfuscated in dex: c = Full, e = Trial, f = Expired.
        val billingCandidates = classDefByStrings("full_version")
            .filter { classDef ->
                val strings = classDef.methods.flatMap { it.stringLiterals() }.toSet()
                listOf("donate_1", "donate_2", "donate_3", "donate_5").all { it in strings } &&
                    classDef.methods.any { method ->
                        method.returnType == LICENSE_TYPE && method.parameterTypes.isEmpty()
                    }
            }

        if (billingCandidates.size != 1) {
            throw PatchException(
                "Smart AudioBook Player: expected exactly 1 billing class with the full/donate " +
                    "product keys and a license getter, found ${billingCandidates.size}. Re-derive.",
            )
        }

        val billingClass = mutableClassDefBy(billingCandidates.single())
        val licenseGetter = billingClass.methods.singleOrNull { method ->
            method.returnType == LICENSE_TYPE && method.parameterTypes.isEmpty()
        } ?: throw PatchException(
            "Smart AudioBook Player: the no-arg LicenseType getter was not found uniquely.",
        )

        licenseGetter.addInstructions(
            0,
            """
                sget-object v0, $LICENSE_TYPE->$LICENSE_FULL:$LICENSE_TYPE
                return-object v0
            """,
        )
    }
}
