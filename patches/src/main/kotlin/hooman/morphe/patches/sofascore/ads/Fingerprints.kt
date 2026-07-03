package hooman.morphe.patches.sofascore.ads

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// The account name of the UserAccount model survives R8 (it lives in com.sofascore.local_persistance),
// so its getters are the stable anchor here.
private const val USER_ACCOUNT = "Lcom/sofascore/local_persistance/UserAccount;"

// The five ad flags that only the shouldShowAds derivation reads off the account. Its sibling in the
// same class shares the exact (UserAccount)Z static shape but only reads getDevMod, so requiring all
// five keeps the pin unambiguous.
private val AD_FLAG_GETTERS = setOf(
    "getForceAds",
    "getHasServerAds",
    "getPurchasedAds",
    "getForceHideAds",
    "getDataSharingOptOut",
)

// The single ad-decision chokepoint (dmj.f(UserAccount) in this build): returns true when ads should
// show. Every banner and interstitial site reads it, and the Application-level ad master switch caches
// its result, so all ad paths funnel through here. R8 renames the class and the method, so pin it by
// the static (UserAccount)->boolean shape plus the exact set of ad-flag getters it reads off the
// account, which nothing else in the app combines.
object ShouldShowAdsFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(USER_ACCOUNT),
    custom = { method, _ ->
        val calledGetters = method.implementation
            ?.instructions
            ?.mapNotNull { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                reference?.takeIf { it.definingClass == USER_ACCOUNT }?.name
            }
            ?.toSet()
            .orEmpty()
        calledGetters.containsAll(AD_FLAG_GETTERS)
    },
)
