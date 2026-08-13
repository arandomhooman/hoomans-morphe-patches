package hooman.morphe.patches.moovit.maps

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element

// Moovit uses the same certificate-restricted key for the Maps SDK and its direct Google Places and
// Geocoding requests. Every Morphe build re-signs the APK, so all three reject Moovit's key. Replace
// both key entry points with one key the user controls and refuse to apply when it is missing.
@Suppress("unused")
val useMapsApiKeyPatch = resourcePatch(
    name = "Use your own Maps API key [REQUIRED]",
    description = "Required for maps and exact-address search. Patching breaks Moovit's built-in " +
        "Google Maps Platform key, so supply your own from a Google Cloud project. Enable Maps SDK " +
        "for Android, Places API (New), and Geocoding API, turn on billing, create an API key, and " +
        "paste it in (leaving it unrestricted is easiest). Remove ads and Unlock Moovit+ both pull " +
        "this in.",
) {
    compatibleWith(
        Compatibility(
            name = "Moovit",
            packageName = "com.tranzmate",
            appIconColor = 0xFF6400,
            targets = listOf(AppTarget("5.194.0.1785")),
        ),
    )

    // Required user-supplied Google Maps Android SDK key. Kept required = true so Morphe Manager marks
    // it mandatory, but the real enforcement is the blank check in execute below, since a key that is
    // never set reads back as the null default and would not trip the patcher's own required check.
    val mapsApiKey by stringOption(
        key = "mapsApiKey",
        default = null,
        title = "Google Maps Platform API key",
        description = "Your own Google Maps Platform key. In the Google Cloud Console, enable \"Maps " +
            "SDK for Android\", \"Places API (New)\", and \"Geocoding API\", turn on billing, then " +
            "create an API key and paste it here. Leaving the key unrestricted is easiest. If you " +
            "restrict it, set Application restrictions to Android apps and add package com.tranzmate " +
            "with the SHA-1 of the certificate your patched build is signed with. Also allow all three " +
            "APIs under API restrictions. A blank map or missing exact-address results means the key, " +
            "certificate restriction, API list, or billing setup is incomplete.",
        required = true,
    )

    execute {
        val key = mapsApiKey
        if (key.isNullOrBlank()) {
            throw PatchException(
                "No Google Maps Platform API key was provided. Re-signing breaks Moovit's bundled key, so the " +
                    "map cannot load without your own. Set the \"Google Maps Platform API key\" option on the " +
                    "\"Use your own Maps API key\" patch and apply again. See the patch description for " +
                    "how to get a key.",
            )
        }

        // The Maps SDK reads manifest meta-data. Moovit's Places and Geocoding HTTP clients separately
        // resolve the google_wla_api_key string by name, so both locations must carry the replacement.
        // The patcher's manifest DOM is not namespace-aware, so attributes use their qualified names.
        fun Element.androidName(): String = getAttribute("android:name")

        document("AndroidManifest.xml").use { document ->
            val metaData = document.getElementsByTagName("meta-data")
            val apiKeyElement = (0 until metaData.length)
                .mapNotNull { metaData.item(it) as? Element }
                .singleOrNull { it.androidName() == "com.google.android.geo.API_KEY" }
                ?: throw PatchException(
                    "Moovit: the com.google.android.geo.API_KEY meta-data was not found in the manifest. " +
                        "The manifest layout changed for this version.",
                )

            apiKeyElement.setAttribute("android:value", key)
        }

        document("res/values/strings.xml").use { document ->
            val strings = document.getElementsByTagName("string")
            val webServicesKey = (0 until strings.length)
                .mapNotNull { strings.item(it) as? Element }
                .singleOrNull { it.getAttribute("name") == "google_wla_api_key" }
                ?: throw PatchException(
                    "Moovit: the google_wla_api_key string was not found. The Google web-service " +
                        "key layout changed for this version.",
                )

            webServicesKey.textContent = key
        }
    }
}
