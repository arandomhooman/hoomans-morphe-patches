package hooman.morphe.patches.at4k.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import hooman.morphe.patches.at4k.license.disableLicenseCheckPatch

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks AT4K Launcher's premium features without paying, like more apps per row " +
        "and the premium wallpaper options. It all runs on the device, so nothing stays locked.",
) {
    // A re-signed (patched) install trips the PairIP Play license check, so bundle the bypass.
    dependsOn(disableLicenseCheckPatch)

    compatibleWith(
        Compatibility(
            name = "AT4K Launcher",
            packageName = "com.overdevs.at4k",
            appIconColor = 0x000000,
            targets = listOf(AppTarget("0.99")),
        ),
    )

    execute {
        // Premium lives in one place: a Compose MutableState<Boolean> on the purchases manager, read
        // through the exposed getter (f()) by every gate. It is seeded from the "is_premium" pref in
        // launcher_prefs at MainActivity.onCreate and written only by the billing callback through the
        // shared setter h(boolean), which also persists the pref. Force both ends so a free user reads
        // premium on every launch and no billing result can clear it.

        // Pin the purchases manager by the unique log literal in its setter.
        val purchasesManager = classDefByStrings("Premium status updated to ")
            .firstOrNull()
            ?: throw PatchException(
                "AT4K: purchases manager (\"Premium status updated to \") not found — the premium " +
                    "flag layout changed.",
            )
        val mutablePurchasesManager = mutableClassDefBy(purchasesManager)

        // h(boolean) is the one writer: it sets the MutableState and persists is_premium. Force the
        // argument true at entry so the billing callback (which passes false for a non-owner) can never
        // clear the flag, and any direct call lands true.
        val setPremium = mutablePurchasesManager.methods.firstOrNull { method ->
            method.name == "h" &&
                method.returnType == "V" &&
                method.parameterTypes == listOf("Z") &&
                AccessFlags.STATIC.isSet(method.accessFlags)
        }
            ?: throw PatchException(
                "AT4K: static premium setter h(Z)V not found — the writer shape changed.",
            )
        setPremium.addInstructions(0, "const/4 p0, 0x1")

        // MainActivity.onCreate seeds the flag from the saved pref (false for a free user). After the
        // manager's application field is assigned there, call the now-forced setter once so the live
        // state and the persisted pref both read premium from launch. Pinned by the un-obfuscated class.
        val mainActivity = mutableClassDefByOrNull("Lcom/overdevs/at4k/MainActivity;")
            ?: throw PatchException(
                "AT4K: MainActivity not found — package layout changed.",
            )
        val onCreate = mainActivity.methods.firstOrNull { method: Method ->
            method.name == "onCreate" &&
                method.returnType == "V" &&
                method.parameterTypes == listOf("Landroid/os/Bundle;")
        }
            ?: throw PatchException(
                "AT4K: MainActivity.onCreate(Bundle) not found.",
            )
        // Inject before the return(s), after the manager's application field is set earlier in
        // onCreate. v0 is clobbered right before the return, so nothing live depends on it.
        val onCreateReturnIndices = onCreate.instructions
            .withIndex()
            .filter { it.value.opcode == Opcode.RETURN_VOID }
            .map { it.index }
            .sortedDescending()
        if (onCreateReturnIndices.isEmpty()) {
            throw PatchException("AT4K: MainActivity.onCreate has no return-void to anchor the seed.")
        }
        onCreateReturnIndices.forEach { index ->
            onCreate.addInstructions(
                index,
                """
                    const/4 v0, 0x1
                    invoke-static {v0}, ${setPremium.definingClass}->h(Z)V
                """,
            )
        }
    }
}
