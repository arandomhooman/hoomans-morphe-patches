package hooman.morphe.patches.twitch.ads

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// The parser that turns an ad-edge HTTP response into the app's ad-result union (oq.a(Result, Z)nq in
// this build). It returns one of the nq subtypes: a display ad (hq), no-ad (gq), or an error (jq/kq).
// Every display/banner/in-feed ad the app shows comes through here first. R8 renames the class, so pin
// it by two error strings only this parser builds, plus its (Result, boolean) -> nq shape. Forcing it
// to return the no-ad singleton makes the whole display-ad pipeline see "nothing to show".
object DisplayAdResponseParserFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf(
            "failed to parse display ad response: ",
            "could not parse content type: ",
        ),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    name = "a",
    returnType = "Lnq;",
    parameters = listOf("Lretrofit2/adapter/rxjava2/Result;", "Z"),
)
