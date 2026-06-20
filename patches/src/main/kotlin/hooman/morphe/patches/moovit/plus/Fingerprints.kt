package hooman.morphe.patches.moovit.plus

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// The subscription-manager "is subscribed" check (vvd.b() in this build) that every Moovit+ feature
// gate reads: true when the local override is set or the stored subscribed-skus set is non-empty. R8
// renames the class, so pin it by the subscribed-skus preference key, which is unique to this class;
// b() is the only public no-arg boolean on it.
object SubscriptionStateFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("subscribed_skus"),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = emptyList(),
)
