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

// nh3.e(): the getter for the status written by native startup. It is the only no-arg int method on
// the manager that recognizes -837 as its uninitialized sentinel. Zero is the normal status; a
// nonzero value can substitute the ExpiredBeta route or reject an otherwise ready provider.
internal object BetaExpiryStatusFingerprint : Fingerprint(
    classFingerprint = LicenseStatusFingerprint,
    returnType = "I",
    parameters = emptyList(),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            (instruction as? WideLiteralInstruction)?.wideLiteral == -837L
        } == true
    },
)
