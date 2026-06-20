package hooman.morphe.patches.acrobat.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel

@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
    description = "Unlocks the Acrobat Pro tools that work on your device, like editing text and " +
        "images and rearranging pages, without paying. The parts Adobe runs on its servers, such " +
        "as Export to Office, Create PDF, and cloud storage, still need a real subscription.",
) {
    compatibleWith(
        Compatibility(
            name = "Adobe Acrobat",
            packageName = "com.adobe.reader",
            appIconColor = 0xB30B00,
            targets = listOf(AppTarget("26.5.0.45958")),
        ),
    )

    execute {
        // SVServicesAccount is the single entitlement chokepoint, and its names survive R8. Every
        // feature gate reads it through z0/G0/H0, which short-circuit to false unless E0() (signed
        // in) is true. Pin the class by descriptor, match each method by shape.
        val account = mutableClassDefByOrNull("Lcom/adobe/libs/services/auth/SVServicesAccount;")
            ?: throw PatchException(
                "Acrobat: SVServicesAccount not found. The services-account package changed.",
            )

        // We grant the on-device entitlements at the TOP of z0/G0/H0, before their own !E0() guard
        // runs, so the granted types read true even though the account is really signed out. We do
        // NOT force E0() itself: a globally-spoofed signed-in state with no real Adobe token sends
        // the app down the authenticated paths it can't finish. The worst is a Play-Billing /
        // paywall subscription reconcile that never converges and respins a BillingClient about
        // twice a second for the whole session (the "glitchy" behaviour). Leaving E0() honest keeps
        // that reconcile on its signed-out branch, which resolves cleanly, while the per-type grants
        // still unlock the tools.

        // z0(SERVICE_TYPE) backs every feature gate. Grant the on-device Pro types and let the cloud
        // types fall through to their real value, so the app doesn't offer cloud work it can't finish.
        // Match the enum constant name, not the ordinal, which R8 can renumber.
        val serviceTypeDesc = "Lcom/adobe/libs/services/utils/SVConstants\$SERVICE_TYPE;"
        val z0 = account.methods.firstOrNull {
            it.name == "z0" && it.returnType == "Z" && it.parameterTypes == listOf(serviceTypeDesc)
        }
            ?: throw PatchException("Acrobat: SVServicesAccount.z0(SERVICE_TYPE)Z not found. The entitlement gate shape changed.")
        z0.grantForEnumNames(
            "ACROBATPRO_SERVICE",
            "ACROBAT_PREMIUM_SERVICE",
            "EDITPDF_SERVICE",
            "ORGANIZEPDF_SERVICE",
            "CROPPDF_SERVICE",
            "ACROBAT_DC_LITE_SERVICE",
        )

        // G0/H0(SERVICES_VARIANTS) are the variant-level reads behind the same gates. Same grant.
        val variantDesc = "Lcom/adobe/libs/services/utils/SVConstants\$SERVICES_VARIANTS;"
        val proVariants = arrayOf(
            "ACROBAT_PRO_SUBSCRIPTION",
            "ACROBAT_PREMIUM_SUBSCRIPTION",
            "ACROBAT_DC_LITE_SUBSCRIPTION",
        )
        listOf("G0", "H0").forEach { name ->
            val method = account.methods.firstOrNull {
                it.name == name && it.returnType == "Z" && it.parameterTypes == listOf(variantDesc)
            }
                ?: throw PatchException("Acrobat: SVServicesAccount.$name(SERVICES_VARIANTS)Z not found. The variant gate shape changed.")
            method.grantForEnumNames(*proVariants)
        }
    }
}

// Grant (return true) if the enum parameter's name() matches a granted constant, else fall through
// to the original body. Name, not ordinal, because R8 renumbers ordinals.
private fun MutableMethod.grantForEnumNames(vararg names: String) {
    val checks = StringBuilder()
    names.forEach { name ->
        checks.append(
            """
                const-string v1, "$name"
                invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v1
                if-nez v1, :grant
            """,
        )
    }
    addInstructionsWithLabels(
        0,
        """
            if-eqz p1, :original
            invoke-virtual {p1}, Ljava/lang/Enum;->name()Ljava/lang/String;
            move-result-object v0
            $checks
            goto :original
            :grant
            const/4 v0, 0x1
            return v0
        """,
        ExternalLabel("original", getInstruction(0)),
    )
}
