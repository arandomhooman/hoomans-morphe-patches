package hooman.morphe.patches.teachmeanatomy.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Resolves the boolean setter `d(String key, boolean value)` on the app's preferences
 * manager (`u1.a` in 5.115), which wraps `SharedPreferences.Editor.putBoolean`.
 *
 * Premium is a single local boolean stored under the key `isProAccount` in the
 * `app_preference` file. Unlike a typical app there is no read wrapper — every gate reads
 * `appPreferences.f12254a.getBoolean("isProAccount", false)` directly in ~25 scattered call
 * sites — so the one shared chokepoint we *can* hook is this writer. Forcing it to always
 * store `true` for the `isProAccount` key means no purchase re-check or logout can ever flip
 * the stored flag back to `false`.
 *
 * The class is pinned via its constructor (the unique `(Context) -> void` method that holds
 * the `"app_preference"` SharedPreferences-file name), then the setter is matched by its
 * `(String, boolean) -> void` signature and its `putBoolean` call.
 */
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

/**
 * Resolves the preferences manager's constructor `u1.a.<init>(Context)`:
 *
 *     public a(Context context) { this.f12254a = context.getSharedPreferences("app_preference", 0); }
 *
 * Forcing the setter alone leaves a gap: a guest who never logs in (and so never triggers a
 * write of `isProAccount`) would read the `false` default. Seeding the flag `true` here — right
 * after the SharedPreferences field is assigned, the earliest possible point — closes that gap.
 *
 * It is the only `(Context) -> void` method that contains the `"app_preference"` string, which
 * uniquely identifies it (the other classes that mention the string do so from non-constructor
 * methods with different signatures).
 */
object PrefsConstructorFingerprint : Fingerprint(
    strings = listOf("app_preference"),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
)
