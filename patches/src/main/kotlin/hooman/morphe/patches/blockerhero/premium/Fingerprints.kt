package hooman.morphe.patches.blockerhero.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Resolves the single boolean `SharedPreferences` getter that every premium check
 * funnels through, i.e. `e(String key, boolean default)` in the obfuscated preferences
 * manager class (`Y3.b` in 1.5.0).
 *
 * The class is pinned by the unique premium preference key string
 * `com.blockerhero.KEY_IS_PREMIUM`, which is assembled by the sibling method `g()` of the
 * same class. Within that class the getter is then matched by its exact signature
 * `(String, boolean) -> boolean` and its call to `SharedPreferences.getBoolean`.
 *
 * Premium state flows as `isPremium = e(g(), false)`, so this getter is the one
 * chokepoint that unlocks every gated feature at once.
 */
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
