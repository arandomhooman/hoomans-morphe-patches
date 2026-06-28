package hooman.morphe.patches.tumblr.annoyances.popups

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// vh0.k4.h(Activity, View, float, View.OnClickListener): builds and shows the gift-message popup that
// suggests buying a TumblrMart item for someone. Pin by the three argument null-check strings it holds.
internal object ShowGiftMessagePopupFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.FINAL, AccessFlags.PUBLIC),
    returnType = "V",
    strings = listOf("activity", "anchorView", "textMessage"),
)
