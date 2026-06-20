package hooman.morphe.patches.flightradar24.ads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

// The single ad-enabled decision (jadx: User.a()): return !s() || e().isAdvertsEnabled() — i.e. ads
// show whenever the user is logged out OR their UserFeatures say adverts are enabled. R8 renames the
// User class and this method, so pin it by the one thing that survives: it is the only method in the
// whole app that calls UserFeatures.isAdvertsEnabled(). UserFeatures is a Gson model, so its class and
// getter names are kept. The ()Z shape plus that call resolve it uniquely.
object AdEnabledFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/flightradar24free/models/account/UserFeatures;",
            name = "isAdvertsEnabled",
        ),
    ),
)
