package hooman.morphe.patches.tumblr.ads

import app.morphe.patcher.Fingerprint

// nf0.c0.b() is the timeline-object factory: it switches on each dashboard item's type and returns null
// to drop one, and the graywater feed builder (pf0) keeps only non-null results. R8 renames the class
// and method, so pin by a string unique to this switch: the Vungle-branch cast-guard message. The patch
// reads the factory's second param (the rumblr TimelineObject), accessed positionally.
object TimelineObjectFactoryFingerprint : Fingerprint(
    strings = listOf(
        "null cannot be cast to non-null type com.tumblr.rumblr.model.advertising.VungleAdResponse",
    ),
)
