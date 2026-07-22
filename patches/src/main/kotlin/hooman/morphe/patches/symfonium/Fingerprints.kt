package hooman.morphe.patches.symfonium

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction

// nh3, the license manager. R8 renames it, so pin it by the one string only it carries: "Probably
// soon", the fallback shown when the trial-end date can't be formatted. The match is the status method
// (nh3.r) that builds the "trial expires on ..." text; its defining class is the manager, which also
// holds the premium gate (the lone ()Z method).
internal object LicenseStatusFingerprint : Fingerprint(
    returnType = "Ljava/io/Serializable;",
    parameters = listOf("L"),
    strings = listOf("Probably soon"),
)

// nh3.h(): the async beta-cutoff check. It is the only continuation method on the manager that
// compares the license sentinel (42) and the six-day cutoff (518400000 ms).
internal object BetaExpiryStatusFingerprint : Fingerprint(
    classFingerprint = LicenseStatusFingerprint,
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
    custom = { method, _ ->
        val literals = method.implementation?.instructions
            ?.mapNotNull { (it as? WideLiteralInstruction)?.wideLiteral }
            ?.toSet()
            .orEmpty()
        42L in literals && 518_400_000L in literals
    },
)

// nh3.b(): the Firebase-backed KeyCheck used by media resolution. Re-signing disables the app's
// Firebase configuration, so pin the method by its encoded key and expected digest.
internal object MediaKeyCheckFingerprint : Fingerprint(
    classFingerprint = LicenseStatusFingerprint,
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
    strings = listOf(
        "S2V5Q2hlY2s=",
        "0C4D6E1E46285A9F81030288BAFDFD00",
    ),
)
