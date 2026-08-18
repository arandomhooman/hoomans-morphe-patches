package hooman.morphe.patches.acrobat.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

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
            targets = listOf(AppTarget("26.7.1.47181")),
        ),
    )

    execute {
        // SVServicesAccount is the single entitlement chokepoint, and the class descriptor survives R8.
        // Every feature gate reads it through a small set of "is entitled" methods keyed by SERVICE_TYPE
        // or SERVICES_VARIANTS. Pin the class by descriptor; select the gate methods by SHAPE, not name.
        val account = mutableClassDefByOrNull("Lcom/adobe/libs/services/auth/SVServicesAccount;")
            ?: throw PatchException(
                "Acrobat: SVServicesAccount not found. The services-account package changed.",
            )

        // R8 renames these gate methods every release, which is what broke the old name-pinned patch on
        // 26.6.2+ (issue #131). Match by shape instead. We grant the on-device Pro entitlements at the TOP
        // of each gate, before its own signed-in guard, so the granted types read true even though the
        // account is really signed out. We do NOT force the signed-in check itself: a globally-spoofed
        // signed-in state with no real Adobe token sends the app into a Play-Billing reconcile that never
        // converges and respins a BillingClient about twice a second for the session (the "glitchy"
        // behaviour). Leaving it honest keeps that reconcile on its signed-out branch while the per-type
        // grants still unlock the tools. Grant by enum name(), not ordinal, which R8 can renumber.
        val serviceTypeDesc = "Lcom/adobe/libs/services/utils/SVConstants\$SERVICE_TYPE;"
        val variantDesc = "Lcom/adobe/libs/services/utils/SVConstants\$SERVICES_VARIANTS;"

        // SERVICE_TYPE gates: every public (SERVICE_TYPE)Z method. On 26.7.1 that is the MeterIQ entry
        // point (which delegates its arg to the private legacy readers) plus one direct reader; granting
        // at their top short-circuits before the real read, so the private helpers are covered too. Grant
        // the on-device Pro types and let cloud types fall through so the app can't offer work it can't
        // finish server-side.
        val serviceTypeGates = account.methods.filter {
            it.returnType == "Z" &&
                it.parameterTypes == listOf(serviceTypeDesc) &&
                AccessFlags.PUBLIC.isSet(it.accessFlags)
        }
        if (serviceTypeGates.isEmpty()) {
            throw PatchException(
                "Acrobat: no public (SERVICE_TYPE)Z gate on SVServicesAccount. The entitlement API changed.",
            )
        }
        serviceTypeGates.forEach {
            it.grantForEnumNames(
                "ACROBATPRO_SERVICE",
                "ACROBAT_PREMIUM_SERVICE",
                "EDITPDF_SERVICE",
                "ORGANIZEPDF_SERVICE",
                "CROPPDF_SERVICE",
                "ACROBAT_DC_LITE_SERVICE",
            )
        }

        // SERVICES_VARIANTS gates: public (SERVICES_VARIANTS)Z methods that actually read a subscription
        // status pref. The "...SubscriptionStatusKey" string literals survive R8 and let us skip the
        // account-type check (the one SERVICES_VARIANTS method that ignores its arg and reads no pref).
        val variantGates = account.methods.filter {
            it.returnType == "Z" &&
                it.parameterTypes == listOf(variantDesc) &&
                AccessFlags.PUBLIC.isSet(it.accessFlags) &&
                it.readsSubscriptionStatusKey()
        }
        if (variantGates.isEmpty()) {
            throw PatchException(
                "Acrobat: no public (SERVICES_VARIANTS)Z entitlement read on SVServicesAccount. " +
                    "The variant gate shape changed.",
            )
        }
        val proVariants = arrayOf(
            "ACROBAT_PRO_SUBSCRIPTION",
            "ACROBAT_PREMIUM_SUBSCRIPTION",
            "ACROBAT_DC_LITE_SUBSCRIPTION",
        )
        variantGates.forEach { it.grantForEnumNames(*proVariants) }

        // The entitlement grant alone does not let the Edit tool START. Edit's PDF engine ships in the
        // base APK (com.adobe.libs.pdfEditCore / pdfEditUI), but tapping Edit first asks Google Play to
        // install an on-demand dynamic-feature module (ARDynamicFeature.EDIT, module "PDFEditFontsDF",
        // label "Fonts For Edit"). On a re-signed sideload Play cannot serve that split, the install
        // fails, and the launcher aborts with "There was an error in starting Edit. Please try clearing
        // the cache data of your Play Store application" (string IDS_EDIT_DF_DOWNLOAD_ERROR). The tool
        // launcher gates on one predicate: a static (Context, ARDynamicFeature)Z that returns true only
        // when SplitInstallManager.getInstalledModules() already holds every required module -- if false
        // it runs the download instead of opening the tool. Report EDIT as installed so the launcher
        // skips the download and opens Edit using the fonts already bundled in base. Only EDIT: the other
        // modules carry real code the APK does not contain (the OCR engine, Fill and Sign's OpenCV lib),
        // so faking those would turn a clean "can't download" message into a crash.
        val featureDesc = "Lcom/adobe/reader/dynamicFeature/ARDynamicFeature;"
        val dfHelper = classDefByStrings("Feature Available Without Explicit Download").singleOrNull()
            ?: throw PatchException(
                "Acrobat: dynamic-feature install helper not found. The DF install check moved.",
            )
        val installedCheck = mutableClassDefBy(dfHelper).methods.singleOrNull {
            it.returnType == "Z" &&
                it.parameterTypes == listOf("Landroid/content/Context;", featureDesc) &&
                AccessFlags.STATIC.isSet(it.accessFlags)
        } ?: throw PatchException(
            "Acrobat: dynamic-feature install check (Context, ARDynamicFeature)Z not found.",
        )
        // Same "return true when the enum arg's name() matches" shape as the entitlement gates: here the
        // enum arg is the ARDynamicFeature, so name() == "EDIT" forces the module-installed result.
        installedCheck.grantForEnumNames("EDIT")
    }
}

// True if the method reads any "...SubscriptionStatusKey" SharedPreferences key. That string literal
// survives R8 and marks a real entitlement read, so it separates the variant gates from the account-type
// check (some keys carry a stray leading space in the DEX, hence the trim).
private fun MutableMethod.readsSubscriptionStatusKey(): Boolean =
    implementation?.instructions?.any { instruction ->
        ((instruction as? ReferenceInstruction)?.reference as? StringReference)
            ?.string?.trim()?.endsWith("SubscriptionStatusKey") == true
    } == true

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
