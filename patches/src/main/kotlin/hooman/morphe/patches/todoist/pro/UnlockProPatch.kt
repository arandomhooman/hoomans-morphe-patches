package hooman.morphe.patches.todoist.pro

import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
    description = "Turns on Todoist Pro on the signed-in account: the project, task, section, filter and " +
        "label caps go to unlimited, reminders, comments, deadlines, durations, calendar layout, the " +
        "activity log, file uploads and the paid templates all open, and the plan badge reads Pro. Pro on " +
        "Todoist is a plan the server sends down and the app caches, so the unlock takes hold once a " +
        "signed-in account loads and it holds across a sync. Anything the Todoist server runs itself is " +
        "untouched: the AI assistant, real team/Business collaboration, and server-enforced storage still " +
        "need a paid account. Re-signing breaks Google sign-in, so sign in with email and password.",
) {
    compatibleWith(
        Compatibility(
            name = "Todoist",
            packageName = "com.todoist",
            // Todoist's brand red.
            appIconColor = 0xE44332,
            targets = listOf(AppTarget("v12190")),
        ),
    )

    execute {
        // Pro is decided by the cached personal UserPlan (source class UserPlan, R8-renamed). Every
        // feature gate reads a capability field straight off it - getMaxProjects/getUploads/getReminders
        // and so on - there is no isPremium boolean and the gates never re-check the server. UserPlan is
        // an immutable data class whose every field is set in its one constructor, and every path that
        // builds one funnels through there: the hardcoded free default, the /api/v1/sync mapper, and
        // copy(). Overwriting the constructor's own primitive params before the field stores (int caps ->
        // MAX_INT, feature booleans -> true; leave the plan-name String) unlocks all of personal Pro and
        // is sync-durable, because the next sync rebuilds the plan through the very constructor being
        // forced. Pin the class by its data-class toString name, which R8 keeps as the source name and
        // which is distinct from the ApiUserPlan wire model's ("ApiUserPlan(planName=").
        val userPlan = classDefByStrings("UserPlan(planName=", StringComparisonType.EQUALS)
            .singleOrNull()
            ?: throw PatchException(
                "Todoist: the cached UserPlan class was not found by its toString name " +
                    "\"UserPlan(planName=\". The model was renamed or refactored; re-derive the plan " +
                    "chokepoint.",
            )
        forcePlanConstructor(mutableClassDefBy(userPlan), "UserPlan")

        // The per-workspace limits (max projects/collaborators/filters, uploads, advanced permissions,
        // admin tools, and the rest) are their own immutable data class, WorkspaceLimits, built the same
        // way. Force its constructor params for the same reason UserPlan works: one constructor, every
        // path through it. The class keeps its name because it is part of the model package Jackson maps.
        val workspaceLimits = mutableClassDefByOrNull("Lcom/todoist/model/WorkspaceLimits;")
            ?: throw PatchException(
                "Todoist: com.todoist.model.WorkspaceLimits not found. The model package moved; " +
                    "re-derive the workspace-limits chokepoint.",
            )
        forcePlanConstructor(workspaceLimits, "WorkspaceLimits")

        // The plan badge and the enum-comparison checks on the User model read premium_status, a separate
        // cached value from the feature caps above, so force it too or the UI shows the account as free
        // while the features work. premium_status is an enum arg on the User constructor. Several classes
        // share the toString name "User(id=", so narrow that set to the one whose constructor actually
        // carries the premium_status enum (the domain User); the ApiUser wire model is already excluded by
        // the exact name ("ApiUser(id="). Then find the enum param and set it to CurrentPersonalPlan for
        // every User the app builds.
        val user = classDefByStrings("User(id=", StringComparisonType.EQUALS)
            .singleOrNull { candidate ->
                candidate.methods.any { method ->
                    method.name == "<init>" && method.parameterTypes.any { isPremiumStatusEnum(it.toString()) }
                }
            }
            ?: throw PatchException(
                "Todoist: the User model carrying premium_status was not found uniquely among the " +
                    "classes named \"User(id=\". The model was renamed or the enum shape changed; " +
                    "re-derive the premium_status chokepoint.",
            )
        forcePremiumStatus(mutableClassDefBy(user))
    }
}

// Rewrites an immutable-data-class constructor's own primitive params to premium at method entry: every
// int (a cap) to MAX_INT, every boolean (a feature flag) to true, leaving object params such as the
// plan-name String alone. Param i (0-based) maps to register p(i+1), which holds only because none of
// these params are wide (long/double); the helper throws if that stops being true.
private fun forcePlanConstructor(classDef: MutableClass, label: String) {
    val constructor = classDef.methods.singleOrNull { method ->
        method.name == "<init>" &&
            method.parameterTypes.isNotEmpty() &&
            method.parameterTypes.all { it == "Ljava/lang/String;" || it == "I" || it == "Z" } &&
            method.parameterTypes.any { it == "I" || it == "Z" }
    } ?: throw PatchException(
        "Todoist: expected exactly one all-primitive data constructor on $label; the constructor shape " +
            "changed. Re-derive.",
    )

    val forces = buildString {
        constructor.parameterTypes.forEachIndexed { index, type ->
            val register = "p${index + 1}"
            when (type) {
                "I" -> appendLine("const $register, 0x7fffffff")
                "Z" -> appendLine("const/16 $register, 0x1")
                "J", "D" -> throw PatchException(
                    "Todoist: $label constructor has a wide param; the p(i+1) register mapping no longer " +
                        "holds. Re-derive.",
                )
                // leave references (the plan-name String) untouched
            }
        }
    }

    constructor.addInstructions(0, forces)
}

// Sets the User model's premium_status enum param to CurrentPersonalPlan at constructor entry. The enum
// constant field is R8-renamed, so it is derived from the enum's <clinit> below.
private fun BytecodePatchContext.forcePremiumStatus(user: MutableClass) {
    // The real (non-synthetic) constructor; the synthetic default-args one delegates to it.
    val constructor = user.methods.singleOrNull { method ->
        method.name == "<init>" &&
            !AccessFlags.SYNTHETIC.isSet(method.accessFlags) &&
            method.parameterTypes.any { isPremiumStatusEnum(it.toString()) }
    } ?: throw PatchException(
        "Todoist: the User constructor carrying the premium_status enum was not found uniquely. " +
            "Re-derive the enum chokepoint.",
    )

    val enumParams = constructor.parameterTypes.withIndex().filter { isPremiumStatusEnum(it.value.toString()) }
    val enumParam = enumParams.singleOrNull()
        ?: throw PatchException(
            "Todoist: expected exactly one premium_status enum param on the User constructor, found " +
                "${enumParams.size}. Re-derive.",
        )
    val enumType = enumParam.value.toString()
    val enumRegister = "p${enumParam.index + 1}"
    val currentPersonalPlanField = currentPersonalPlanField(enumType)

    constructor.addInstructions(
        0,
        "sget-object $enumRegister, $enumType->$currentPersonalPlanField:$enumType",
    )
}

// True when the type resolves to an enum whose constants include the premium_status value
// "current_personal_plan" - i.e. the User model's premium_status enum, and not its ApiUser sibling
// (a different type) or the User's other nested enums.
private fun BytecodePatchContext.isPremiumStatusEnum(type: String): Boolean {
    if (!type.startsWith("L") || !type.endsWith(";")) return false
    val def = classDefByOrNull(type) ?: return false
    if (def.superclass != "Ljava/lang/Enum;") return false
    val clinit = def.methods.firstOrNull { it.name == "<clinit>" }?.implementation ?: return false
    return clinit.instructions.any {
        ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "current_personal_plan"
    }
}

// Walks the enum's <clinit> to find which static field holds the CurrentPersonalPlan constant. The
// constant is built by an invoke-direct of the enum's own <init> whose last argument register holds the
// "current_personal_plan" string; the new instance (the invoke's first argument) is then stored into a
// static field of the enum type, and that field's name is what we need.
private fun BytecodePatchContext.currentPersonalPlanField(enumType: String): String {
    val enumDef = classDefByOrNull(enumType)
        ?: throw PatchException("Todoist: premium_status enum $enumType not found. Re-derive.")
    val instructions = enumDef.methods.firstOrNull { it.name == "<clinit>" }?.implementation?.instructions?.toList()
        ?: throw PatchException("Todoist: premium_status enum $enumType has no <clinit>. Re-derive.")

    val serialIndex = instructions.indexOfFirst {
        ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "current_personal_plan"
    }
    if (serialIndex < 0) {
        throw PatchException("Todoist: \"current_personal_plan\" not found in $enumType <clinit>. Re-derive.")
    }
    val serialRegister = (instructions[serialIndex] as OneRegisterInstruction).registerA

    var instanceRegister = -1
    for (i in serialIndex until instructions.size) {
        val instruction = instructions[i]
        if (instruction.opcode != Opcode.INVOKE_DIRECT) continue
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: continue
        if (reference.definingClass != enumType || reference.name != "<init>") continue
        val invoke = instruction as FiveRegisterInstruction
        if (lastArgumentRegister(invoke) == serialRegister) {
            instanceRegister = invoke.registerC
            break
        }
    }
    if (instanceRegister < 0) {
        throw PatchException("Todoist: CurrentPersonalPlan construction not found in $enumType. Re-derive.")
    }

    for (i in serialIndex until instructions.size) {
        val instruction = instructions[i]
        if (instruction.opcode != Opcode.SPUT_OBJECT) continue
        if ((instruction as OneRegisterInstruction).registerA != instanceRegister) continue
        val field = (instruction as ReferenceInstruction).reference as FieldReference
        if (field.type == enumType) return field.name
    }
    throw PatchException("Todoist: CurrentPersonalPlan field store not found in $enumType. Re-derive.")
}

private fun lastArgumentRegister(invoke: FiveRegisterInstruction): Int = when (invoke.registerCount) {
    1 -> invoke.registerC
    2 -> invoke.registerD
    3 -> invoke.registerE
    4 -> invoke.registerF
    5 -> invoke.registerG
    else -> -1
}
