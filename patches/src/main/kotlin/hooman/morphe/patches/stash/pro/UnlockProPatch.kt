package hooman.morphe.patches.stash.pro

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue

@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
    description = "Turns on Stash Pro and removes the ads. Pro is a flag the server sends with your " +
        "account, so the unlock takes effect on a signed-in account once it loads. With it on the ads " +
        "go away and the Pro-gated UI opens up: the Become Pro upsell card, the home screen block " +
        "layout, custom collection cover images, and the locked review detail fields. Anything the " +
        "Stash server checks for a non-paying account is unaffected, so data kept behind a paid " +
        "account does not sync. Re-signing breaks Google and Facebook login, so sign in with email.",
) {
    compatibleWith(
        Compatibility(
            name = "Stash",
            packageName = "io.stashteam.games.tracker.stashapp",
            // The gold accent on the Stash "S" (colors.xml names it "yellow", #f7bf00).
            appIconColor = 0xF7BF00,
            targets = listOf(AppTarget("2.48.2")),
        ),
    )

    execute {
        // Pro is the server-sent account.isPaid boolean. It comes down on AccountApiModel (the account
        // DTO) and is fanned out through several independent derivations that each read it off a
        // different object: the ad gate reads a Room-cached Boolean flow, the home upsell and the
        // profile screen read it off the domain Account, the custom-collection and review screens read
        // their own flows. There is no single late chokepoint - the readers take the value as a direct
        // field (iget), so forcing a getter or one flow downstream misses the others (an earlier attempt
        // flipping only the ad flow left every upsell showing).
        //
        // The one point they all converge on is the source getter AccountApiModel.getIsPaid(): both
        // mappers (AccountEntityMapperKt building the Room row, AccountMapperKt building the domain
        // Account) call it to seed isPaid. Force it true and the cached row and the domain Account are
        // both written paid on every account fetch, so the ad flow, the upsell, and the gated screens
        // all observe Pro. The app re-signs to a fresh install (no carried-over Room DB), so the first
        // sign-in fetch writes the paid row before anything reads it.
        //
        // Pin AccountApiModel by FQN (a Gson DTO, so R8 keeps the class). It has three structurally
        // identical no-arg ()Z getters (isClosed/isPaid/activated); the paid one is singled out by the
        // field it returns, the one annotated @SerializedName("paid"). To re-derive on a new version:
        // same DTO, same "paid" SerializedName, find the ()Z getter reading that field.
        val dto = mutableClassDefByOrNull("Lio/stashteam/stashapp/data/network/model/AccountApiModel;")
            ?: throw PatchException(
                "Stash: AccountApiModel DTO not found. This patch targets 2.48.2; the account model " +
                    "moved and the isPaid source must be re-derived.",
            )

        val paidFields = dto.fields.filter { field ->
            field.type == "Z" &&
                field.annotations.any { annotation ->
                    annotation.type == "Lcom/google/gson/annotations/SerializedName;" &&
                        annotation.elements.any { element ->
                            element.name == "value" &&
                                (element.value as? StringEncodedValue)?.value == "paid"
                        }
                }
        }
        if (paidFields.size != 1) {
            throw PatchException(
                "Stash: expected exactly 1 boolean field on AccountApiModel annotated " +
                    "@SerializedName(\"paid\"), found ${paidFields.size}. Re-derive the isPaid source.",
            )
        }
        val paidFieldName = paidFields.single().name

        // The lone no-arg ()Z getter whose body reads the "paid" field. The isClosed/activated getters
        // are the same shape but read their own fields, so the field reference distinguishes the reader.
        val getters = dto.methods.filter { method ->
            method.returnType == "Z" &&
                method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.any { instruction ->
                    (instruction as? ReferenceInstruction)?.reference
                        ?.let { it as? FieldReference }
                        ?.let {
                            it.definingClass ==
                                "Lio/stashteam/stashapp/data/network/model/AccountApiModel;" &&
                                it.name == paidFieldName
                        } == true
                } == true
        }
        if (getters.size != 1) {
            throw PatchException(
                "Stash: expected exactly 1 no-arg ()Z getter reading AccountApiModel.$paidFieldName, " +
                    "found ${getters.size}. The getter shape changed; re-derive.",
            )
        }

        getters.single().addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
