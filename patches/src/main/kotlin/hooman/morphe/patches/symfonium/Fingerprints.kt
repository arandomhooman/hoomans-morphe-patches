package hooman.morphe.patches.symfonium

import app.morphe.patcher.Fingerprint

// nh3, the license manager. R8 renames it, so pin it by the one string only it carries: "Probably
// soon", the fallback shown when the trial-end date can't be formatted. The match is the status method
// (nh3.r) that builds the "trial expires on ..." text; its defining class is the manager, which also
// holds the premium gate (the lone ()Z method).
internal object LicenseStatusFingerprint : Fingerprint(
    returnType = "Ljava/io/Serializable;",
    parameters = listOf("L"),
    strings = listOf("Probably soon"),
)

// er2.d(): the SNTP client that fetches a trusted wall-clock time off time.android.com. It is the only
// caller of the network-time fetch and the only source of the clock the build-expiry/trial checks read,
// so the unique host string pins it cleanly.
internal object NetworkTimeFingerprint : Fingerprint(
    returnType = "J",
    parameters = emptyList(),
    strings = listOf("time.android.com"),
)
