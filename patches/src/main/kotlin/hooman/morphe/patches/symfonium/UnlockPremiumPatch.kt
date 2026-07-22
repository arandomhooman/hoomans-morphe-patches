package hooman.morphe.patches.symfonium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Turns on Symfonium's paid features. The build ships as a public beta with a built-in " +
        "trial, so this also prevents the expired-beta screen. The license is checked locally and the " +
        "unlock does not require an account. Connecting Plex, " +
        "Jellyfin and other media servers is untouched.",
) {
    compatibleWith(
        Compatibility(
            name = "Symfonium",
            packageName = "app.symfonik.music.player",
            // The red Symfonium "S".
            appIconColor = 0xE53935,
            targets = listOf(AppTarget("14.1.0")),
        ),
    )

    execute {
        // The license manager (nh3). Pinned through the status method that carries the unique "Probably
        // soon" string; its defining class holds both the premium gate and the beta-expiry status.
        val licenseManager = LicenseStatusFingerprint.classDef

        // The premium gate. Features read this boolean at ~25 sites; it returns true when the manager's
        // license value equals the licensed sentinel (42). It is the only ()Z on the manager, and the
        // only one comparing against 42, so match on that literal. Force it true and every gated screen
        // and setting opens regardless of the live license value.
        val gate = licenseManager.methods.filter { method ->
            method.returnType == "Z" &&
                method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.any { instruction ->
                    (instruction as? WideLiteralInstruction)?.wideLiteral == 42L
                } == true
        }
        if (gate.size != 1) {
            throw PatchException(
                "Symfonium: expected exactly 1 license-gate ()Z method (compares the license value to " +
                    "42) on the manager, found ${gate.size}. The gate shape changed; re-derive.",
            )
        }
        val gateMethod = gate.single()

        // Playback resolution reads the backing license state directly instead of calling the gate.
        // Seed it as licensed and force every later write to keep the same value.
        val licenseStateSetter = licenseManager.methods.singleOrNull { method ->
            method.returnType == "V" &&
                method.parameterTypes == listOf("J") &&
                method.instructions.any { instruction ->
                    ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let { reference ->
                        reference.definingClass == "Ljava/lang/Long;" &&
                            reference.name == "valueOf" &&
                            reference.parameterTypes == listOf("J")
                    } == true
                }
        } ?: throw PatchException("Symfonium: license-state setter (J)V not found uniquely.")
        val mutableStateField = licenseStateSetter.instructions.mapNotNull { instruction ->
            ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.takeIf { field ->
                instruction.opcode == Opcode.IGET_OBJECT && field.definingClass == licenseManager.type
            }
        }.singleOrNull() ?: throw PatchException(
            "Symfonium: mutable license-state field not found uniquely in the setter.",
        )
        if (gateMethod.instructions.none { instruction ->
                (instruction as? ReferenceInstruction)?.reference == mutableStateField
            }
        ) {
            throw PatchException("Symfonium: premium gate no longer reads the derived license-state field.")
        }
        licenseStateSetter.addInstructions(0, "const-wide/16 p1, 0x2a")

        val constructor = licenseManager.methods.singleOrNull { method -> method.name == "<init>" }
            ?: throw PatchException("Symfonium: license-manager constructor not found uniquely.")
        val stateStoreIndex = constructor.instructions.withIndex().singleOrNull { (_, instruction) ->
            instruction.opcode == Opcode.IPUT_OBJECT &&
                ((instruction as? ReferenceInstruction)?.reference as? FieldReference) == mutableStateField
        }?.index ?: throw PatchException("Symfonium: initial license-state store not found uniquely.")
        val seedWindowStart = maxOf(0, stateStoreIndex - 12)
        val initialState = constructor.instructions.withIndex()
            .filter { (index, instruction) ->
                index in seedWindowStart until stateStoreIndex &&
                    (instruction as? WideLiteralInstruction)?.wideLiteral == -1L
            }
            .singleOrNull() ?: throw PatchException(
            "Symfonium: initial -1 license-state sentinel not found uniquely before its store.",
        )
        val stateRegister = (initialState.value as? OneRegisterInstruction)?.registerA
            ?: throw PatchException("Symfonium: initial license-state register shape changed.")
        constructor.replaceInstruction(initialState.index, "const-wide/16 v$stateRegister, 0x2a")

        // Re-signing disables the stock Firebase app identity, so its KeyCheck cannot load. Media
        // resolution treats that missing value as a rejection even when the license state is 42.
        MediaKeyCheckFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                return-object v0
            """,
        )

        // The async cutoff check can substitute the ExpiredBeta route after six days. Keep its
        // boxed status at zero while leaving unrelated status consumers untouched.
        BetaExpiryStatusFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
                move-result-object v0
                return-object v0
            """,
        )
    }
}
