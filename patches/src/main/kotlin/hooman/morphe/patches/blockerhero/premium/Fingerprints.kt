package hooman.morphe.patches.blockerhero.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

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

/**
 * Resolves the `isLoggedIn` gate `l()` in the same preferences manager class (`Y3.b`):
 *
 *     public final boolean l() { return j() > 0; }   // j() = MyApplication.userId ?: -1
 *
 * Premium features are gated a *second* time on this (a Google account / login), separate
 * from the premium flag. Tapping one while logged out shows "Login required." — and the
 * re-signed patched app can't complete Google sign-in anyway. Forcing it true bypasses the
 * gate so the (local) premium features are usable without an account.
 *
 * Within `Y3.b` it is the only no-arg `() -> boolean` method that uses an `if-lez`
 * (the `j() > 0` comparison), which uniquely identifies it.
 */
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

/**
 * Resolves the constructor of the `PreferencesState` data class (`Y3.c` in 1.5.0), the
 * immutable UI-state snapshot exposed via a `StateFlow`.
 *
 * This is the real premium chokepoint at runtime. The feature gates (e.g. the keyword /
 * app blocklist 10-item cap, focus mode) do NOT read `e(g())` live — they read the
 * **cached** `isPremium` field off this state object. That cached field is populated from
 * the stored premium pref, which on a fresh / re-signed / billing-less install is false,
 * so hooking the `e()` getter alone leaves the gates closed even though the badge flips.
 *
 * Pinning the class by its data-class `toString` prefix `"PreferencesState(test="`, the
 * sole constructor takes the fields in declaration order; the 2nd parameter (`p2`, the
 * first `boolean`) is `isPremium`. Forcing it true makes every state snapshot report
 * premium, which is what the gates actually consult.
 */
object PrefsStateConstructorFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("PreferencesState(test="),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
)

/**
 * Resolves the app-wide toast helper `Q(Context, String message)` in `p5.f`, which shows a
 * `Toast` of whatever message it is given. That includes the server's "Unauthenticated." 401
 * body, which fires whenever a premium toggle tries to sync without a real account/token.
 *
 * The class is pinned via a distinctive subscription-screen string it also contains, then the
 * method is matched by its `(Context, String) -> void` signature and its `Toast.makeText` call.
 */
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
