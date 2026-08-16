package hooman.morphe.patches.twitch.chat

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// The ClickableSpan put on a deleted chat message (hic in this build). Its third field (a boolean we
// call hasModAccess) gates everything: onClick only reveals the original message when it is true, and
// updateDrawState only keeps the message tappable-looking when it is true. A normal viewer gets false,
// so the message stays hidden behind the "<message deleted>" placeholder. R8 renames the class, so pin
// it as the only ClickableSpan subclass whose constructor takes (String, SpannedString, boolean,
// EventDispatcher) -- that shape is unique to the deleted-message span.
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
