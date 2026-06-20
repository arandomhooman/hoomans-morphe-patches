package hooman.morphe.patches.moovit.ads

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// The universal ad-unit resolver (og9.g(AdSource) in this build): returns the ad-unit id when an ad
// should show and "" when it should be suppressed. R8 renames the class, so pin the method by the
// unique remote-config key it reads ("is_ads_free_version") plus its (AdSource)->String shape. The
// sibling resolver h() shares the shape but doesn't read that key, so the string keeps this unambiguous.
object AdUnitResolverFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf("Lcom/moovit/app/ads/AdSource;"),
    strings = listOf("is_ads_free_version"),
)
