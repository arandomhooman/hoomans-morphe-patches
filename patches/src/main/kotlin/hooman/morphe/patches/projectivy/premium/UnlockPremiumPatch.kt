package hooman.morphe.patches.projectivy.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks Projectivy Launcher's premium so the paywall stops sending you to the upgrade " +
        "screen. Display profiles, custom wallpapers and backgrounds, parental controls, and the locked " +
        "settings and customization options all open up. The gate is decided on the device, so there is " +
        "no account to sign in to.",
) {
    compatibleWith(
        Compatibility(
            name = "Projectivy Launcher",
            packageName = "com.spocky.projengmenu",
            // Orange of the vehicle in the launcher icon (ic_launcher_foreground fillColor #f08029).
            appIconColor = 0xF08029,
            targets = listOf(AppTarget("4.68"), AppTarget("4.70")),
        ),
    )

    execute {
        // Premium is one local gate on PTApplication, a non-obfuscated class pinned by its real name.
        // Every paywall redirect reads a StateFlow the app seeds to "locked" and if-eqz's on its value,
        // so true sends you to PremiumActivity and false runs the feature. Where you force it open
        // changed between releases: 4.68 had a single reader method to override, but 4.70 removed it and
        // inlined the read across ~30 call sites, so the two need different chokepoints. The static
        // license re-check writer a(PTApplication, boolean) exists only in 4.70 (4.68's writer is an
        // instance method), so its presence tells the two versions apart.
        val ptApplication = mutableClassDefByOrNull("Lcom/spocky/projengmenu/PTApplication;")
            ?: throw PatchException(
                "Projectivy: PTApplication not found. This patch targets 4.68 and 4.70; the application " +
                    "class moved and the chokepoint must be re-derived.",
            )

        val staticWriter = ptApplication.methods.singleOrNull { method ->
            AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "V" &&
                method.parameterTypes.map { it.toString() } ==
                listOf("Lcom/spocky/projengmenu/PTApplication;", "Z")
        }

        if (staticWriter != null) {
            // 4.70: no lone reader is left to force, so unlock the flow at its source. The constructor
            // builds two StateFlows: the premium one (seeded Boolean.TRUE) and an unrelated Integer
            // counter (seeded Integer.valueOf(0)). Only the premium seed is a Boolean.TRUE, so it is the
            // lone such read in <init>. Flip it to Boolean.FALSE so the flow starts unlocked from the
            // first gate read at cold start.
            val constructor = ptApplication.methods.singleOrNull { it.name == "<init>" }
                ?: throw PatchException("Projectivy: PTApplication.<init> not found.")
            val seeds = constructor.implementation?.instructions?.withIndex()?.filter { (_, instruction) ->
                (instruction as? ReferenceInstruction)?.reference?.toString() ==
                    "Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;"
            }?.toList() ?: emptyList()
            if (seeds.size != 1) {
                throw PatchException(
                    "Projectivy: expected exactly one Boolean.TRUE seed in PTApplication.<init> but found " +
                        "${seeds.size}. Re-derive which StateFlow holds the premium gate.",
                )
            }
            val (seedIndex, seedInstruction) = seeds.single()
            val seedRegister = (seedInstruction as OneRegisterInstruction).registerA
            constructor.replaceInstruction(
                seedIndex,
                "sget-object v$seedRegister, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;",
            )

            // a(PTApplication, boolean) is the periodic license/time re-check, the only writer of that
            // flow: it recomputes the gate and calls setValue. No-op it so the seeded false can never be
            // flipped back to locked after a re-check.
            staticWriter.addInstructions(0, "return-void")
        } else {
            // 4.68: force the reader. PTApplication has three ()Z methods and R8 strips their names, so
            // shape alone is not enough. Only the premium reader unboxes a Boolean (Boolean.booleanValue),
            // which is the StateFlow value it returns. The instance e()Z returns a raw boolean field with
            // no unboxing, and the static g()Z (telemetry, leave it alone) never unboxes either, so the
            // booleanValue invoke singles out the reader. It reads the flow live on each call rather than
            // a cached snapshot, so forcing it false survives the app's periodic license re-check.
            fun Method.unboxesBoolean(): Boolean =
                implementation?.instructions?.any { instruction ->
                    (instruction as? ReferenceInstruction)?.reference?.toString()
                        ?.contains("Ljava/lang/Boolean;->booleanValue()Z") == true
                } == true

            val premiumReader = ptApplication.methods.singleOrNull { method ->
                !AccessFlags.STATIC.isSet(method.accessFlags) &&
                    method.returnType == "Z" &&
                    method.parameterTypes.isEmpty() &&
                    method.unboxesBoolean()
            } ?: throw PatchException(
                "Projectivy: the premium reader (the lone no-arg instance ()Z that unboxes a Boolean on " +
                    "PTApplication) was not found uniquely. Re-derive which ()Z reads the premium flow.",
            )

            premiumReader.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return v0
                """,
            )
        }
    }
}
