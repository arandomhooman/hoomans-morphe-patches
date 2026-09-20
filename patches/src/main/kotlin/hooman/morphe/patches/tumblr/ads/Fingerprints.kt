package hooman.morphe.patches.tumblr.ads

import app.morphe.patcher.Fingerprint

// bg0.c0.b() is the timeline-object factory: it switches on each dashboard item's type and returns null
// to drop one. Dashboard assembly filters those nulls, but blog search also sends its results to the
// memory cache, which needs a companion null guard. R8 renames the class and method, so pin by a string
// unique to this switch: the Vungle-branch cast-guard message. The patch reads the factory's second
// param (the rumblr TimelineObject), accessed positionally.
object TimelineObjectFactoryFingerprint : Fingerprint(
    strings = listOf(
        "null cannot be cast to non-null type com.tumblr.rumblr.model.advertising.VungleAdResponse",
    ),
)
