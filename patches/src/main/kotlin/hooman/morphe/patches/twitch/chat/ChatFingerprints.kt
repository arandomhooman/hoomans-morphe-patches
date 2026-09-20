package hooman.morphe.patches.twitch.chat

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// The ClickableSpan put on a deleted chat message (upc in this build). Its boolean field gates both
// revealing the original message and drawing the placeholder as clickable. R8 renames the class, so
// pin it as the only ClickableSpan subclass with this constructor shape.
object DeletedMessageSpanCtorFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Landroid/text/SpannedString;",
        "Z",
        "Ltv/twitch/android/core/mvp/viewdelegate/EventDispatcher;",
    ),
    custom = { _, classDef ->
        classDef.superclass == "Landroid/text/style/ClickableSpan;"
    },
)

// The formatter rebuilds a deleted-message span with the row's live EventDispatcher, but only when its
// input does not already contain one. Twitch now sends it a placeholder that has a span with a null
// dispatcher, so the existing-span fast path returns a link that cannot dispatch the reveal event.
// The exact parameter and return types uniquely identify this static formatter in 30.7.2.
object DeletedMessageFormatterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/text/Spanned;",
    parameters = listOf(
        "Ljava/lang/String;",
        "Landroid/text/SpannedString;",
        "Landroid/content/Context;",
        "Ltv/twitch/android/core/mvp/viewdelegate/EventDispatcher;",
        "Z",
    ),
)

// The community-points button state provider (zn8 = CommunityPointsButtonStateProvider). V2(state)
// rebuilds the button's view state on every update; when the incoming state carries an active claim
// (field d, an ActiveClaimModel) a bonus is waiting to be claimed. The provider holds the data
// provider that performs the claim (field m, type ir8, exposing H(claimId, ChatModeMetadata)). R8
// renames the class, so pin it by the MVP state-class name it keeps in a method signature string, plus
// the V2(state)->viewState shape.
object CommunityPointsStateProviderFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("CommunityPointsButtonStateProvider\$State"),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    name = "V2",
    returnType = "Lkn8;",
    parameters = listOf("Lmn8;"),
)
