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
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val unlockPlusPatch = bytecodePatch(
    name = "Unlock Plus",
    description = "Removes ads and unlocks the on-device Quizlet Plus features without a " +
        "subscription, including unlimited Learn and Test rounds and textbook explanation views " +
        "that free accounts meter. The AI tools, like Magic Notes and generation, run on " +
        "Quizlet's servers and stay locked.",
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
        fun Method.stringLiterals(): Set<String> =
            implementation?.instructions?.mapNotNull { instruction ->
                (instruction as? ReferenceInstruction)?.reference
                    ?.let { it as? StringReference }?.string
            }?.toSet().orEmpty()

        fun Method.returnVoidIndices(): List<Int> =
            instructions
                .withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }
                .sortedDescending()

        // Two local axes on DBUser (JSON/OrmLite; names survive R8):
        //   userUpgradeType: 0=NO_UPGRADE, 1=PLUS, 2=TEACHER, 3=GO
        //   featurePlanType: plus_limited | plus_unlimited | ...
        // isFreeUser = (type == 0). Type alone kills ads but Learn/Test still meter free and
        // plus_limited budgets. Force GO + plus_unlimited.
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

        // Study-mode MeteringInfo (j2): at-limit g, can-use h. Call sites often IGET the fields
        // instead of T()/c0(), so force fields at every return-void of the main ctor AND keep
        // the accessors. Pin by exact toString prefix (not Remote/Explanations variants).
        val meteringInfoDef = classDefByStrings("MeteringInfo(numEvents=").singleOrNull { def ->
            def.methods.any { method ->
                method.name == "toString" && method.stringLiterals().any {
                    it.startsWith("MeteringInfo(numEvents=") &&
                        !it.startsWith("Remote") &&
                        !it.startsWith("Explanations")
                }
            }
        } ?: throw PatchException(
            "Quizlet: study MeteringInfo class not unique or missing. Re-derive the metering pin.",
        )
        val meteringInfo = mutableClassDefBy(meteringInfoDef)
        val meteringCtor = meteringInfo.methods.firstOrNull {
            it.name == "<init>" && it.parameterTypes.size >= 4
        } ?: throw PatchException("Quizlet: MeteringInfo constructor not found.")
        val writtenBoolFields = mutableListOf<String>()
        for (instruction in meteringCtor.instructions) {
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
        // Field-level force: IGETs of g/h ignore the accessor patches.
        // Do NOT use v0 here: many of these ctors are .locals 0, so v0 aliases p0 (this) and
        // const/4 v0 clobbers this -> VerifyError "iput on BooleanConstant". Use p-registers.
        // Also force numEvents/threshold args (p1/p2): Learn/Test hand j2.a/j2.b into shared
        // MeteredEvent and re-evaluate limits there, ignoring the boolean flags.
        meteringCtor.addInstructions(
            0,
            """
                const/4 p1, 0x0
                const/16 p2, 0x270f
            """,
        )
        meteringCtor.returnVoidIndices().forEach { index ->
            meteringCtor.addInstructions(
                index,
                """
                    const/4 p1, 0x0
                    iput-boolean p1, p0, ${meteringInfo.type}->$atLimitField:Z
                    const/4 p1, 0x1
                    iput-boolean p1, p0, ${meteringInfo.type}->$canUseField:Z
                """,
            )
        }

        // Shared KMP MeteredEvent(numEvents, threshold) is what Learn/Test study engines meter on.
        // Force every 2-int ctor to 0 / 9999 so mid-session increments and raw j2 copies stay open.
        val meteredEventDef = classDefByStrings("MeteredEvent(numEvents=").singleOrNull()
            ?: throw PatchException("Quizlet: MeteredEvent class not found.")
        val meteredEvent = mutableClassDefBy(meteredEventDef)
        for (ctor in meteredEvent.methods) {
            if (ctor.name != "<init>" || ctor.returnType != "V") continue
            // Public MeteredEvent(II): p1=numEvents, p2=threshold.
            if (ctor.parameterTypes == listOf("I", "I")) {
                ctor.addInstructions(
                    0,
                    """
                        const/4 p1, 0x0
                        const/16 p2, 0x270f
                    """,
                )
            }
            // kotlinx synthetic (mask, numEvents, threshold) uses p2/p3 for the ints.
            if (ctor.parameterTypes == listOf("I", "I", "I")) {
                ctor.addInstructions(
                    0,
                    """
                        const/4 p2, 0x0
                        const/16 p3, 0x270f
                    """,
                )
            }
        }

        // PaywallContent carries canUseTestFeature / canUseLearnFeature. Force both true on the
        // full 10-arg ctor (last two booleans) so set-page mode tiles unlock.
        val paywallContentDef = classDefByStrings("PaywallContent(header=").singleOrNull {
            it.methods.any { m ->
                m.name == "toString" && m.stringLiterals().any { s -> s.contains("canUseLearnFeature=") }
            }
        }
        if (paywallContentDef != null) {
            val paywallContent = mutableClassDefBy(paywallContentDef)
            val fullCtor = paywallContent.methods.firstOrNull {
                it.name == "<init>" && it.parameterTypes.size == 10
            }
            if (fullCtor != null) {
                // Params: g,I,I,a,a,Z,I,Z,Z,Z -> last two Z are canUseTest (p9) and canUseLearn (p10)
                // Count slots carefully: g=1, I=1, I=1, a=1, a=1, Z=1, I=1, Z=1, Z=1, Z=1
                // p0=this, p1..p10
                fullCtor.addInstructions(
                    0,
                    """
                        const/4 p9, 0x1
                        const/4 p10, 0x1
                    """,
                )
            }
        }

        // Textbook / explanation answers use ExplanationsMeteringInfo (k1). Force ctor inputs so
        // computed atLimit is false and remaining is large: numEvents=0, threshold=9999.
        val explanationsMeteringDef = classDefByStrings("ExplanationsMeteringInfo(numEvents=")
            .singleOrNull()
            ?: throw PatchException(
                "Quizlet: ExplanationsMeteringInfo class not found. Re-derive textbook metering pin.",
            )
        val explanationsMetering = mutableClassDefBy(explanationsMeteringDef)
        val expCtor = explanationsMetering.methods.firstOrNull {
            it.name == "<init>" && it.parameterTypes.size == 2 && it.returnType == "V"
        } ?: throw PatchException("Quizlet: ExplanationsMeteringInfo(II) constructor not found.")
        expCtor.addInstructions(
            0,
            """
                const/4 p1, 0x0
                const/16 p2, 0x270f
            """,
        )

        // QuestionDetailsWithMetering / ExerciseDetailsWithMetering: force isContentLimited arg
        // (3rd param p3) false before the original iputs run.
        for (limitedPin in listOf(
            "QuestionDetailsWithMetering(question=",
            "ExerciseDetailsWithMetering(exerciseDetails=",
        )) {
            val def = classDefByStrings(limitedPin).singleOrNull()
                ?: throw PatchException("Quizlet: $limitedPin host class not found.")
            val host = mutableClassDefBy(def)
            val ctor = host.methods.firstOrNull {
                it.name == "<init>" && it.parameterTypes.size == 3 && it.returnType == "V"
            } ?: throw PatchException("Quizlet: $limitedPin 3-arg constructor not found.")
            ctor.addInstructions(0, "const/4 p3, 0x0")
        }

        // Server entitlement payload: canUseFeature is the first ctor boolean (p1). Force true at
        // entry so Moshi-built instances are usable without post-return iputs (which broke Verify).
        val entitlementDef = classDefByStrings("RemoteEntitlementData(canUseFeature=")
            .singleOrNull()
            ?: throw PatchException("Quizlet: RemoteEntitlementData class not found.")
        val entitlement = mutableClassDefBy(entitlementDef)
        val entitlementCtor = entitlement.methods.firstOrNull {
            it.name == "<init>" && it.parameterTypes.size == 4 && it.returnType == "V"
        } ?: throw PatchException("Quizlet: RemoteEntitlementData constructor not found.")
        entitlementCtor.addInstructions(0, "const/4 p1, 0x1")

        // Unmetered(..., canUseFeature). Last param is Z; with eventType, Long, long (wide), reason,
        // boolean the boolean lands in p6. Force true at entry.
        val unmeteredDef = classDefByStrings("Unmetered(eventType=").singleOrNull()
        if (unmeteredDef != null) {
            val unmetered = mutableClassDefBy(unmeteredDef)
            val unmeteredCtor = unmetered.methods.firstOrNull {
                it.name == "<init>" && it.parameterTypes.size >= 4
            }
            // Prefer entry-arg force when the last param is boolean.
            if (unmeteredCtor != null && unmeteredCtor.parameterTypes.lastOrNull() == "Z") {
                // Count register slots: each J takes 2. p0=this.
                var slot = 1
                for (param in unmeteredCtor.parameterTypes.dropLast(1)) {
                    slot += if (param == "J" || param == "D") 2 else 1
                }
                unmeteredCtor.addInstructions(0, "const/4 p$slot, 0x1")
            }
        }

        // StudiableMeteringData: force at-limit false (+ remaining) at return using p1 as temp.
        val studiableMetering = mutableClassDefByOrNull(
            "Lcom/quizlet/studiablemodels/StudiableMeteringData;",
        ) ?: throw PatchException(
            "Quizlet: StudiableMeteringData class not found — studiablemodels package changed.",
        )
        val atLimitSmdField = studiableMetering.fields.singleOrNull { it.type == "Z" }
            ?: throw PatchException(
                "Quizlet: StudiableMeteringData boolean at-limit field not unique.",
            )
        val remainingSmdField = studiableMetering.fields.firstOrNull {
            it.type == "Ljava/lang/Integer;" && it.name != "b" && it.name != "c"
        } ?: studiableMetering.fields.lastOrNull { it.type == "Ljava/lang/Integer;" }
        val smdCtor = studiableMetering.methods.firstOrNull { method ->
            method.name == "<init>" &&
                method.parameterTypes.size == 3 &&
                method.returnType == "V"
        } ?: throw PatchException(
            "Quizlet: StudiableMeteringData(event, num, threshold) constructor not found.",
        )
        smdCtor.returnVoidIndices().forEach { index ->
            val remainingForce = if (remainingSmdField != null) {
                """
                    const/16 p1, 0x270f
                    invoke-static {p1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
                    move-result-object p1
                    iput-object p1, p0, ${studiableMetering.type}->${remainingSmdField.name}:Ljava/lang/Integer;
                """
            } else {
                ""
            }
            smdCtor.addInstructions(
                index,
                """
                    $remainingForce
                    const/4 p1, 0x0
                    iput-boolean p1, p0, ${studiableMetering.type}->${atLimitSmdField.name}:Z
                """,
            )
        }
    }
}
