package hooman.morphe.patches.tumblr.featureflags

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

// Shared machinery, ported from ReVanced's Tumblr patches. A "Feature" is one of Tumblr's A/B flags
// (the wz.f enum); its on/off value is read through wz.b.d(Feature, boolean). The annoyance patches
// that want a flag forced call addFeatureFlagOverride(name, value) and depend on this patch so the hook
// is present. Feature.toString() is the CaseFormat-lowerCamel form of the enum name, so the names used
// are e.g. "adFreeCtaBanner" for the AD_FREE_CTA_BANNER constant.

/**
 * Override a feature flag with a value. Call from a patch that dependsOn(overrideFeatureFlagsPatch).
 *
 * @param name The lowerCamel name of the feature flag to override (Feature.toString()).
 * @param value The value to override the feature flag with.
 */
@Suppress("KDocUnresolvedReference")
internal lateinit var addFeatureFlagOverride: (name: String, value: String) -> Unit
    private set

// Internal (no name): applied automatically as a dependency of each Disable... feature-flag patch.
@Suppress("unused")
val overrideFeatureFlagsPatch = bytecodePatch(
    description = "Forces the value of chosen Tumblr A/B feature flags.",
) {
    compatibleWith(
        Compatibility(
            name = "Tumblr",
            packageName = "com.tumblr",
            appIconColor = 0x001935,
            targets = listOf(AppTarget("45.0.0.109")),
        ),
    )

    execute {
        val configurationClass = GetFeatureValueFingerprint.originalMethod.definingClass
        val featureClass = GetFeatureValueFingerprint.originalMethod.parameterTypes[0].toString()

        // The getter only has one spare register, so the override logic lives in a helper method we add
        // to the same class. Each registered override appends one compare-and-return into the helper
        // (see addFeatureFlagOverride below); if none match it falls through to return null.
        val helperMethod = ImmutableMethod(
            GetFeatureValueFingerprint.originalMethod.definingClass,
            "getValueOverride",
            listOf(ImmutableMethodParameter(featureClass, null, "feature")),
            "Ljava/lang/String;",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(4),
        ).toMutable().apply {
            addInstructions(
                0,
                """
                    invoke-virtual {p1}, $featureClass->toString()Ljava/lang/String;
                    move-result-object v0

                    # Overrides are inserted here, one compare-and-return each (helperInsertIndex below).
                    # If none matched, return null and let the original getter run.
                    const/4 v0, 0x0
                    return-object v0
                """,
            )
        }.also { helperMethod ->
            GetFeatureValueFingerprint.classDef.methods.add(helperMethod)
        }

        // Call the helper at the top of the getter; if it returns non-null, return that, otherwise let
        // the original lookup continue.
        val getFeatureIndex = GetFeatureValueFingerprint.instructionMatches.first().index
        GetFeatureValueFingerprint.method.addInstructionsWithLabels(
            getFeatureIndex,
            """
                invoke-virtual {p0, p1}, $configurationClass->getValueOverride($featureClass)Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :is_null
                return-object v0
                :is_null
                nop
            """,
        )

        // Index 2: right after the toString() + move-result that seed v0 in the helper above.
        val helperInsertIndex = 2
        addFeatureFlagOverride = { name, value ->
            helperMethod.addInstructionsWithLabels(
                helperInsertIndex,
                """
                    const-string v1, "$name"
                    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :no_override
                    const-string v1, "$value"
                    return-object v1
                    :no_override
                    nop
                """,
            )
        }
    }
}
