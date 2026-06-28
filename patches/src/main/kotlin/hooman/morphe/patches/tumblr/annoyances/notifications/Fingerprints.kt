package hooman.morphe.patches.tumblr.annoyances.notifications

import app.morphe.patcher.Fingerprint

// vh0.z0.l(): the BlogNotifyCtaDialog "isEnabled" check. The dialog asks whether you want to enable
// notifications for a blog and reappears on later visits. Forcing this false keeps it from ever showing.
// Pin by the two log/pref strings unique to the check.
internal object IsBlogNotifyEnabledFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf("isEnabled --> ", "blog_notify_enabled"),
)
