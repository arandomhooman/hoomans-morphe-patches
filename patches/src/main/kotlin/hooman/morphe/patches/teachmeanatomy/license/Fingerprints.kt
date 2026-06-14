package hooman.morphe.patches.teachmeanatomy.license

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Resolves `initializeLicenseCheck()` on PairIP's `com.pairip.licensecheck.LicenseClient` — the
 * single entry point of the Google Play license check (called from `LicenseContentProvider`
 * before the app's own code runs, and from the static `checkLicense(Context)`).
 *
 * On a sideloaded build the check returns NOT_LICENSED and PairIP launches a Play "paywall"
 * activity and kills the process, so the patched app can never start. No-opping this method
 * skips the whole check; nothing in the app reads the license state, so it is inert.
 *
 * The class is pinned by the unique licensing-service action string; within it, the method is
 * the only public no-arg `() -> void`.
 */
object InitializeLicenseCheckFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("com.android.vending.licensing.ILicensingService"),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
)

/**
 * Resolves `startPaywallActivity(PendingIntent)` on the same `LicenseClient`, which (after
 * scheduling the app's shutdown) launches the Play paywall. A failsafe: even if the check were
 * to run, no-opping this stops the paywall ever showing and the process ever being killed.
 *
 * It is the only method on the class that takes a single `PendingIntent`, which uniquely
 * identifies it.
 */
object StartPaywallActivityFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("com.android.vending.licensing.ILicensingService"),
    ),
    accessFlags = listOf(AccessFlags.PRIVATE),
    returnType = "V",
    parameters = listOf("Landroid/app/PendingIntent;"),
)
