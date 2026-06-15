package hooman.morphe.patches.teachmeanatomy.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

// Boolean setter d(String, boolean) wrapping Editor.putBoolean. There's no read wrapper (~25 gates
// read isProAccount directly), so this writer is the only shared chokepoint. Pinned via the
// constructor that holds "app_preference", matched by (String, boolean) + putBoolean.
object PrefsPutBooleanFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("app_preference"),
        parameters = listOf("Landroid/content/Context;"),
        returnType = "V",
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Z"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/content/SharedPreferences\$Editor;",
            name = "putBoolean",
        ),
    ),
)

// The prefs-manager ctor (Context) that assigns getSharedPreferences("app_preference"). Used to seed
// isProAccount for a guest who never logs in. The only (Context)->void holding that string.
object PrefsConstructorFingerprint : Fingerprint(
    strings = listOf("app_preference"),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
)
