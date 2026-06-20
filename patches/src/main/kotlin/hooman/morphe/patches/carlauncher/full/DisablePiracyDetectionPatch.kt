package hooman.morphe.patches.carlauncher.full

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

// Internal companion to Unlock Full Version. AGAMA has a re-sign tripwire separate from the full/trial
// flag: a native JNI check (getSignatureBasedStringNative) SHA-256s the signing cert and hands back two
// strings, nativData[1] and nativData[2], that are equal on the legit Play cert and differ on a re-sign.
// setButtonNameAndIcons() counts the mismatches and, past a threshold, paints the shortcut icons with
// the skull drawable (plus a magenta theme and a swapped music cover). None of it reads isFull(), so the
// full unlock alone still trips it. Forcing nativData[2] = nativData[1] at the top of that method keeps
// the mismatch count at zero, so none of the retaliations ever arm.
val disablePiracyDetectionPatch = bytecodePatch(
    // No name -> internal; pulled in by unlockFullPatch via dependsOn so users can't omit it.
) {
    execute {
        val mainActivity = mutableClassDefByOrNull("Laltergames/carlauncher/MainActivity;")
            ?: throw PatchException(
                "Car Launcher: MainActivity not found — package layout changed.",
            )

        // The piracy verdict (nativData[1] vs nativData[2]) is computed and consumed inside the icon
        // painter. Pin it by shape: the lone void method taking a single boolean that reads nativData.
        val setIcons = mainActivity.methods.singleOrNull { method ->
            method.returnType == "V" &&
                method.parameterTypes.singleOrNull()?.toString() == "Z" &&
                method.implementation?.instructions?.any { instruction ->
                    (instruction as? ReferenceInstruction)?.reference
                        ?.let { it as? FieldReference }
                        ?.let { it.name == "nativData" } == true
                } == true
        }
            ?: throw PatchException(
                "Car Launcher: the icon painter that reads nativData was not found — the piracy " +
                    "check shape changed.",
            )

        // nativData[2] = nativData[1] before anything reads it, so nativData[1].equals(nativData[2])
        // is always true and the mismatch counter stays at zero. .locals here is >= 12, so v0-v2 are
        // free scratch at method entry (no locals are live before the first real instruction).
        setIcons.addInstructions(
            0,
            """
                iget-object v0, p0, Laltergames/carlauncher/MainActivity;->nativData:[Ljava/lang/String;
                const/4 v1, 0x1
                aget-object v1, v0, v1
                const/4 v2, 0x2
                aput-object v1, v0, v2
            """,
        )
    }
}
