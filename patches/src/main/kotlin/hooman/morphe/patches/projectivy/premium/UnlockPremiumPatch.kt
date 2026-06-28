package hooman.morphe.patches.projectivy.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
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
            targets = listOf(AppTarget("4.70")),
        ),
    )

    execute {
        // Premium is one local gate on PTApplication, a non-obfuscated class pinned by its real name.
        // Every paywall redirect reads a StateFlow that the app seeds to "locked": ~30 call sites do
        // `getInstance().<flow>.getValue()` and `if-eqz` on it, so true sends you to PremiumActivity and
        // false runs the feature. There is no single reader method left to force (4.68 had one; 4.70
        // inlined the read everywhere), so unlock the flow at its source instead: seed it false and stop
        // the only writer from ever flipping it back.
        val ptApplication = mutableClassDefByOrNull("Lcom/spocky/projengmenu/PTApplication;")
            ?: throw PatchException(
                "Projectivy: PTApplication not found. This patch targets 4.70; the application class " +
                    "moved and the chokepoint must be re-derived.",
            )

        // The constructor builds two StateFlows: the premium one (seeded Boolean.TRUE) and an unrelated
        // Integer counter (seeded Integer.valueOf(0)). Only the premium seed is a Boolean.TRUE, so it is
        // the lone such read in <init>. Flip it to Boolean.FALSE so the flow starts unlocked, which holds
        // from the first gate read at cold start.
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

        // The periodic license/time re-check is the only writer of that flow. It is the static
        // a(PTApplication, boolean) that recomputes the gate and calls setValue, and it is the lone
        // static void method on the class taking (PTApplication, boolean). No-op it so the seeded false
        // can never be flipped back to locked after a re-check.
        val setter = ptApplication.methods.singleOrNull { method ->
            AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "V" &&
                method.parameterTypes.map { it.toString() } ==
                listOf("Lcom/spocky/projengmenu/PTApplication;", "Z")
        } ?: throw PatchException(
            "Projectivy: the premium setter (the lone static void (PTApplication, boolean) that writes " +
                "the gate flow) was not found uniquely. Re-derive the license re-check writer.",
        )
        setter.addInstructions(0, "return-void")
    }
}
