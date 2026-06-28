package hooman.morphe.patches.tumblr.featureflags

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

// wz.b.d(Feature, boolean): the getter that reads a Feature's value off the A/B config Tumblr fetches
// from /v2/config at startup. The override hook is spliced in right after the arg null check, at the
// first if-eqz of the lookup, where there is still a free register. R8 renames the class and method, so
// pin by the "feature" null-check string plus the (Feature, boolean) -> String shape.
internal object GetFeatureValueFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf("L", "Z"),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IF_EQZ,
        Opcode.INVOKE_STATIC,
        Opcode.MOVE_RESULT,
    ),
    strings = listOf("feature"),
)
