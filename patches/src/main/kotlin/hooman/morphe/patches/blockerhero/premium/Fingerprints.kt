package hooman.morphe.patches.blockerhero.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

// Boolean prefs getter e(String, boolean) every premium check funnels through (isPremium =
// e(g(), false)). Pinned by the unique KEY_IS_PREMIUM string and its getBoolean call.
object PrefsGetBooleanFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("com.blockerhero.KEY_IS_PREMIUM"),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;", "Z"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/content/SharedPreferences;",
            name = "getBoolean",
        ),
    ),
)

// The isLoggedIn gate l() = userId > 0; the only no-arg ()->boolean in the prefs class using if-lez.
object IsLoggedInFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("com.blockerhero.KEY_IS_PREMIUM"),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        opcode(Opcode.IF_LEZ),
    ),
)

// PreferencesState ctor — the gates read its cached isPremium field (p2, the first boolean).
// Pinned by the data-class toString prefix.
object PrefsStateConstructorFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("PreferencesState(test="),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
)

// App-wide toast helper Q(Context, String); hooked to drop the server's "Unauthenticated" 401 toast.
object ShowToastFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("*3-Day Money-Back Guarantee"),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/widget/Toast;",
            name = "makeText",
        ),
    ),
)
