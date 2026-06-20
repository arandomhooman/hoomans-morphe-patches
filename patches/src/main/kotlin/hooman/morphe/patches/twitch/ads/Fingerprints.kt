package hooman.morphe.patches.twitch.ads

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// The lambda that builds the LIVE HLS manifest URL handed to the player (Lk7m;->invoke in this
// build). It assembles https://usher.ttvnw.net/api/v2/channel/hls/<channel>.m3u8?... from the
// captured stream name. R8 renames the class, so pin it by the two strings it builds the URL from:
// "usher.ttvnw.net" plus "fast_bread" (a query param only the live builder sets). That pair is unique
// to this method -- the VOD builder also hits usher.ttvnw.net but never sets fast_bread, so the pair
// keeps us off the VOD path. The lambda implements a two-arg functional interface, so the method is
// invoke(Object, Object)Object; the captured stream-name field is the second instance field.
object LiveManifestUrlBuilderFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("usher.ttvnw.net", "fast_bread"),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
)
