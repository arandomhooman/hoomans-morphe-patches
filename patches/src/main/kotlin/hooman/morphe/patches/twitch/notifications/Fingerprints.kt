package hooman.morphe.patches.twitch.notifications

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// Firebase Installations computes X-Android-Cert from the installed signer. Twitch's Firebase API key
// accepts only the stock certificate, so a re-signed build receives API_KEY_ANDROID_APP_BLOCKED before
// it can register for push notifications. Pin the connection builder by its two stable header names.
object FirebaseInstallationsConnectionFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("X-Android-Cert", "x-goog-api-key"),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/net/HttpURLConnection;",
    parameters = listOf("Ljava/net/URL;", "Ljava/lang/String;"),
)
