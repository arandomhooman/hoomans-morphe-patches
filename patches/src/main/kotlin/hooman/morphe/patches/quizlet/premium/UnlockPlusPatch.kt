package hooman.morphe.patches.quizlet.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

@Suppress("unused")
val unlockPlusPatch = bytecodePatch(
    name = "Unlock Plus",
    description = "Removes ads and unlocks the on-device Quizlet Plus features without a " +
        "subscription, including unlimited Learn and Test rounds that free and Plus Limited " +
        "accounts meter. The AI tools, like Magic Notes and generation, run on Quizlet's servers " +
        "and stay locked.",
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
        // Two local axes on DBUser (JSON/OrmLite; names survive R8):
        //   userUpgradeType: 0=NO_UPGRADE, 1=PLUS, 2=TEACHER, 3=GO
        //   featurePlanType: plus_limited | plus_unlimited | plus_unlimited_teacher | family_plan
        // isFreeUser = (type == 0). Type alone kills ads but Learn/Test still meter free and
        // plus_limited budgets, so mid-quiz subscribe walls still fire. Force GO + plus_unlimited.
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
        // GO (3) is the highest student upgrade type (PLUS=1 also badges as Plus).
        upgradeTypeGetter.addInstructions(
            0,
            """
                const/4 v0, 0x3
                return v0
            """,
        )

        val featurePlanTypeGetter = dbUser.methods.firstOrNull { method ->
            method.name == "getFeaturePlanType" &&
                method.returnType == "Ljava/lang/String;" &&
                method.parameterTypes.isEmpty()
        }
            ?: throw PatchException(
                "Quizlet: DBUser.getFeaturePlanType() not found — feature-plan gate shape changed.",
            )
        featurePlanTypeGetter.addInstructions(
            0,
            """
                const-string v0, "plus_unlimited"
                return-object v0
            """,
        )

        // Failsafe if R8 inlined the getter at free-user checks. Best-effort.
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

        // Study-mode paywalls (Test start, set-page locks, Learn checkpoints) call MeteringInfo's
        // at-limit / can-use booleans (numEvents vs threshold). Free accounts still get real counts
        // from the server and local store, so the upgrade-type force above is not enough. Pin by the
        // data-class toString prefix (unique vs Remote/Explanations variants); class name is R8-short.
        val meteringInfoDefs = classDefByStrings("MeteringInfo(numEvents=")
        val meteringInfoDef = meteringInfoDefs.singleOrNull()
            ?: throw PatchException(
                "Quizlet: MeteringInfo class not unique or missing " +
                    "(found ${meteringInfoDefs.size}). Re-derive the metering pin.",
            )
        val meteringInfo = mutableClassDefBy(meteringInfoDef)

        // Constructor writes g = (numEvents >= soft+threshold), then h = !g. Accessors T()/c0() each
        // load one of those fields. Map write order -> force T false / c0 true.
        val ctor = meteringInfo.methods.firstOrNull {
            it.name == "<init>" && it.parameterTypes.size >= 4
        } ?: throw PatchException("Quizlet: MeteringInfo constructor not found.")
        val writtenBoolFields = mutableListOf<String>()
        for (instruction in ctor.instructions) {
            if (instruction.opcode != Opcode.IPUT_BOOLEAN) continue
            val ref = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                ?: continue
            if (ref.definingClass == meteringInfo.type &&
                ref.type == "Z" &&
                ref.name !in writtenBoolFields
            ) {
                writtenBoolFields.add(ref.name)
            }
        }
        if (writtenBoolFields.size < 2) {
            throw PatchException(
                "Quizlet: could not resolve MeteringInfo at-limit/can-use fields from constructor.",
            )
        }
        val atLimitField = writtenBoolFields[0]
        val canUseField = writtenBoolFields[1]

        fun Method.loadedBooleanField(): String? {
            for (instruction in instructions) {
                if (instruction.opcode != Opcode.IGET_BOOLEAN) continue
                val ref = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                    ?: continue
                if (ref.definingClass == meteringInfo.type && ref.type == "Z") {
                    return ref.name
                }
            }
            return null
        }

        var forcedAtLimit = false
        var forcedCanUse = false
        for (method in meteringInfo.methods) {
            if (method.returnType != "Z" || method.parameterTypes.isNotEmpty()) continue
            when (method.loadedBooleanField()) {
                atLimitField -> {
                    method.addInstructions(
                        0,
                        """
                            const/4 v0, 0x0
                            return v0
                        """,
                    )
                    forcedAtLimit = true
                }
                canUseField -> {
                    method.addInstructions(
                        0,
                        """
                            const/4 v0, 0x1
                            return v0
                        """,
                    )
                    forcedCanUse = true
                }
            }
        }
        if (!forcedAtLimit || !forcedCanUse) {
            throw PatchException(
                "Quizlet: failed to force MeteringInfo at-limit/can-use accessors " +
                    "(atLimit=$forcedAtLimit, canUse=$forcedCanUse).",
            )
        }

        // Some mid-quiz paths read StudiableMeteringData's remaining<=0 flag directly (stable
        // package name). Force that single boolean field false at the end of the main constructor.
        val studiableMetering = mutableClassDefByOrNull(
            "Lcom/quizlet/studiablemodels/StudiableMeteringData;",
        ) ?: throw PatchException(
            "Quizlet: StudiableMeteringData class not found — studiablemodels package changed.",
        )
        val atLimitSmdField = studiableMetering.fields.singleOrNull { it.type == "Z" }
            ?: throw PatchException(
                "Quizlet: StudiableMeteringData boolean at-limit field not unique.",
            )
        val smdCtor = studiableMetering.methods.firstOrNull { method ->
            method.name == "<init>" &&
                method.parameterTypes.size == 3 &&
                method.returnType == "V"
        } ?: throw PatchException(
            "Quizlet: StudiableMeteringData(event, num, threshold) constructor not found.",
        )
        val returnIndices = smdCtor.instructions
            .withIndex()
            .filter { it.value.opcode == Opcode.RETURN_VOID }
            .map { it.index }
            .sortedDescending()
        if (returnIndices.isEmpty()) {
            throw PatchException("Quizlet: StudiableMeteringData constructor has no return-void.")
        }
        returnIndices.forEach { index ->
            smdCtor.addInstructions(
                index,
                "const/4 v0, 0x0\niput-boolean v0, p0, ${studiableMetering.type}->${atLimitSmdField.name}:Z",
            )
        }
    }
}
