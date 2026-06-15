package hooman.morphe.patches.teachmeanatomy.license

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// PairIP's initializeLicenseCheck() — the single Play-license entry point (called from
// LicenseContentProvider and the static checkLicense()). The only public no-arg ()->void on the
// class, pinned by the licensing-service string.
object InitializeLicenseCheckFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("com.android.vending.licensing.ILicensingService"),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
)

// startPaywallActivity(PendingIntent) on the same LicenseClient (the failsafe target); the only
// method on the class taking a single PendingIntent.
object StartPaywallActivityFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("com.android.vending.licensing.ILicensingService"),
    ),
    accessFlags = listOf(AccessFlags.PRIVATE),
    returnType = "V",
    parameters = listOf("Landroid/app/PendingIntent;"),
)
