package hooman.morphe.patches.flightradar24.maps

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element

// Flightradar24 registers its Google Maps key (com.google.android.maps.v2.API_KEY in the manifest)
// against its own signing certificate's SHA-1 plus the package name. Every Morphe build re-signs the
// APK with a different cert, so Google rejects FR24's key and the flight map renders blank. There is no
// way around that with FR24's own key, so the patch swaps in a key the user controls. Without a key the
// map is dead, which is why this refuses to apply unless one is supplied.
@Suppress("unused")
val useMapsApiKeyPatch = resourcePatch(
    name = "Use your own Maps API key [REQUIRED]",
    description = "Required for the map to load. Patching breaks Flightradar24's built-in Google Maps " +
        "key, so you have to supply your own from a free Google Cloud project. In this patch's option, " +
        "create a project, enable Maps SDK for Android, turn on billing, make an API key, and paste " +
        "it in (leaving it unrestricted is easiest). Remove ads and Unlock aircraft data both pull this " +
        "in, so the map keeps working with either.",
) {
    compatibleWith(
        Compatibility(
            name = "Flightradar24",
            packageName = "com.flightradar24free",
            appIconColor = 0x327CB5,
            targets = listOf(AppTarget("11.6.1")),
        ),
    )

    // Required user-supplied Google Maps Android SDK key. Kept required = true so Morphe Manager marks
    // it mandatory, but the real enforcement is the blank check in execute below, since a key that is
    // never set reads back as the null default and would not trip the patcher's own required check.
    val mapsApiKey by stringOption(
        key = "mapsApiKey",
        default = null,
        title = "Google Maps API key",
        description = "Your own Google Maps Android API key (free). In the Google Cloud Console: create " +
            "a project, enable \"Maps SDK for Android\", and turn on billing for the project (Maps needs " +
            "a billing account, but normal use stays inside the free monthly credit). Then under " +
            "Credentials create an API key and paste it here. Easiest is to leave the key unrestricted. " +
            "If you do restrict it, set Application restrictions to Android apps and add package " +
            "com.flightradar24free with the SHA-1 of the certificate your patched build is signed with " +
            "(read it from the patched APK). A blank map means the key is empty, restricted to a " +
            "different cert, or its project is missing \"Maps SDK for Android\" or billing.",
        required = true,
    )

    execute {
        val key = mapsApiKey
        if (key.isNullOrBlank()) {
            throw PatchException(
                "No Google Maps API key was provided. Re-signing breaks Flightradar24's bundled key, so " +
                    "the map cannot load without your own. Set the \"Google Maps API key\" option on the " +
                    "\"Use your own Maps API key\" patch and apply again. See the patch description for " +
                    "how to get a key.",
            )
        }

        // The manifest's maps.v2.API_KEY meta-data points at a string resource (@string/map_key).
        // Overwrite android:value on that element with the literal key so the manifest is self-contained
        // and does not depend on the string resource keeping its name. FR24 uses the legacy
        // com.google.android.maps.v2.API_KEY name rather than the newer geo.API_KEY, but the Maps SDK
        // accepts either. The patcher's manifest DOM is not namespace-aware, so attributes come back
        // under their literal qualified name.
        fun Element.androidName(): String = getAttribute("android:name")

        document("AndroidManifest.xml").use { document ->
            val metaData = document.getElementsByTagName("meta-data")
            val apiKeyElement = (0 until metaData.length)
                .mapNotNull { metaData.item(it) as? Element }
                .singleOrNull { it.androidName() == "com.google.android.maps.v2.API_KEY" }
                ?: throw PatchException(
                    "Flightradar24: the com.google.android.maps.v2.API_KEY meta-data was not found in " +
                        "the manifest. The manifest layout changed for this version.",
                )

            apiKeyElement.setAttribute("android:value", key)
        }
    }
}
