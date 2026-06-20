package hooman.morphe.patches.bandlab.membership

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import hooman.morphe.patches.bandlab.integrity.bypassInstallationCheckPatch

@Suppress("unused")
val unlockMembershipPatch = bytecodePatch(
    name = "Unlock Membership",
    description = "Unlocks the BandLab Membership tools that run inside the app without a " +
        "subscription, like the extra effects and instruments, more tracks, pitch tools, the voice " +
        "changer, mastering, and more. Anything BandLab makes on its servers, like the stem Splitter, " +
        "AI video, and distribution, still needs a real membership.",
) {
    // A re-signed build trips BandLab's repackaging check and won't launch. Bundle the bypass as an
    // internal dependency so it can't be omitted.
    dependsOn(bypassInstallationCheckPatch)

    compatibleWith(
        Compatibility(
            name = "BandLab",
            packageName = "com.bandlab.bandlab",
            appIconColor = 0xF02010,
            targets = listOf(AppTarget("11.25.3")),
        ),
    )

    execute {
        // One repository holds the server's MembershipStatus in a StateFlow and exposes three reads
        // the gate sites use. Both the sync and Flow paths are gate sources, so all three are forced:
        //   h() : isMember()        -> plan != Free
        //   i(feature) : hasFeature -> availableFeatures.contains(feature.key)   (sync)
        //   n(feature) : hasFeature -> same check, as a Flow                     (reactive)
        // The status is only written by the Play Billing callbacks, so a free account never resets it.
        // The repo's strings aren't obfuscated, so the class is pinned by a log line only it carries.
        val repoDef = classDefByStrings("FeedMock: setting mem status ").singleOrNull()
            ?: throw PatchException(
                "BandLab: membership repository not found uniquely by its log string. The layout changed.",
            )
        val repo = mutableClassDefBy(repoDef)

        // 1. isMember(): the lone no-arg ()Z that reads the plan enum (type Lsof;). The other no-arg
        //    ()Z (isTrial) reads a Boolean, so the plan-enum reference distinguishes it.
        val isMember = repo.methods.singleOrNull { method ->
            method.returnType == "Z" && method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.any { instruction ->
                    (instruction as? ReferenceInstruction)?.reference
                        ?.let { it as? FieldReference }
                        ?.let { it.type == "Lsof;" || it.definingClass == "Lsof;" } == true
                } == true
        } ?: throw PatchException(
            "BandLab: isMember() (no-arg ()Z reading the plan enum) not found uniquely in the repo.",
        )
        isMember.addInstructions(0, "const/4 v0, 0x1\nreturn v0")

        // 2. hasFeature (sync): the (Lgnf;)Z that does availableFeatures.contains(feature.key).
        val hasFeatureSync = repo.methods.singleOrNull { method ->
            method.returnType == "Z" && method.parameterTypes.singleOrNull()?.toString() == "Lgnf;"
        } ?: throw PatchException(
            "BandLab: sync hasFeature (Lgnf;)Z not found uniquely in the repo.",
        )
        hasFeatureSync.addInstructions(0, "const/4 v0, 0x1\nreturn v0")

        // 3. hasFeature (reactive): n(Lgnf;) maps the status flow through a synthetic mapper built with
        //    `new <mapper>(feature, 22)`, whose case 22 runs the same availableFeatures.contains check.
        //    Locate the mapper from n()'s new-instance, then force that case TRUE.
        val flowReader = repo.methods.singleOrNull { method ->
            method.returnType == "Lvye;" && method.parameterTypes.singleOrNull()?.toString() == "Lgnf;"
        } ?: throw PatchException(
            "BandLab: reactive hasFeature n(Lgnf;)Lvye; not found uniquely in the repo.",
        )
        val mapperType = flowReader.instructions.firstNotNullOfOrNull { instruction ->
            if (instruction.opcode == Opcode.NEW_INSTANCE) {
                (instruction as? ReferenceInstruction)?.reference?.toString()
            } else {
                null
            }
        } ?: throw PatchException(
            "BandLab: could not find the membership-feature flow mapper (no new-instance in n()).",
        )
        val mapper = mutableClassDefByOrNull(mapperType)
            ?: throw PatchException("BandLab: flow mapper class $mapperType not found.")

        val mapperInvoke = mapper.methods.singleOrNull { method ->
            method.name == "invoke" &&
                method.parameterTypes.singleOrNull()?.toString() == "Ljava/lang/Object;" &&
                method.returnType == "Ljava/lang/Object;"
        } ?: throw PatchException(
            "BandLab: flow mapper invoke(Object)Object not found in $mapperType.",
        )

        // The availableFeatures read (a List field on the status class) only appears in the feature
        // case, so returning TRUE there is unique to it and leaves the other mapper cases alone.
        val featureReadIndex = mapperInvoke.instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.IGET_OBJECT &&
                (instruction as? ReferenceInstruction)?.reference
                    ?.let { it as? FieldReference }
                    ?.let { it.definingClass == "Ldof;" && it.type == "Ljava/util/List;" } == true
        }
        if (featureReadIndex < 0) {
            throw PatchException(
                "BandLab: the availableFeatures read (Ldof;->i:Ljava/util/List;) was not found in the " +
                    "flow mapper. The membership-feature case changed.",
            )
        }
        mapperInvoke.addInstructions(
            featureReadIndex,
            "sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;\nreturn-object v0",
        )

        // Force the plan at its source. The "Get Membership" button and the ad gate don't call the
        // methods above; they read the plan through flows derived from the cached status object (Ldof;,
        // field "a" of enum type Lsof; with Free/Pro/Max = c/d/e). Set the plan to Max on every status
        // object, at the end of both constructors (no-arg default and deserialization), upstream of
        // every read. Both constructors declare a local, so v0 is free to clobber before a return.
        val statusClass = mutableClassDefByOrNull("Ldof;")
            ?: throw PatchException("BandLab: MembershipStatus class Ldof; not found.")
        val constructors = statusClass.methods.filter { it.name == "<init>" && it.returnType == "V" }
        if (constructors.isEmpty()) {
            throw PatchException("BandLab: no Ldof; constructors found to force the plan.")
        }
        constructors.forEach { constructor ->
            val returnIndices = constructor.instructions
                .withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }
                .sortedDescending()
            if (returnIndices.isEmpty()) {
                throw PatchException("BandLab: dof constructor has no return-void to anchor the plan force.")
            }
            returnIndices.forEach { index ->
                constructor.addInstructions(
                    index,
                    "sget-object v0, Lsof;->e:Lsof;\niput-object v0, p0, Ldof;->a:Lsof;",
                )
            }
        }
    }
}
