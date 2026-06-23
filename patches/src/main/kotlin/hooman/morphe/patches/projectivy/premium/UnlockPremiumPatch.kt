package hooman.morphe.patches.projectivy.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
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
            targets = listOf(AppTarget("4.68")),
        ),
    )

    execute {
        // Premium is one local gate on PTApplication, a non-obfuscated class pinned by its real name.
        // The gate reads a StateFlow that the app seeds to "locked" and returns whether premium is
        // withheld: every paywall redirect does `if-eqz` on it, so false runs the feature and true sends
        // you to PremiumActivity. Forcing the reader to return false unlocks all of them at once. It reads
        // the flow live on each call rather than a cached snapshot, so it survives the app's periodic
        // license re-check.
        val ptApplication = mutableClassDefByOrNull("Lcom/spocky/projengmenu/PTApplication;")
            ?: throw PatchException(
                "Projectivy: PTApplication not found. This patch targets 4.68; the application class " +
                    "moved and the chokepoint must be re-derived.",
            )

        // PTApplication has three ()Z methods and R8 strips their names, so shape alone is not enough.
        // Only the premium reader unboxes a Boolean (Boolean.booleanValue), which is the StateFlow value
        // it returns. The instance e()Z returns a raw boolean field with no unboxing, and the static
        // g()Z (telemetry, leave it alone) never unboxes either, so the booleanValue invoke singles out
        // the reader.
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
