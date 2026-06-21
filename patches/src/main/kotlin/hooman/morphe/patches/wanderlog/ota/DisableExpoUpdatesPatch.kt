package hooman.morphe.patches.wanderlog.ota

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

// Internal (no name): applied automatically as a dependency of Unlock Pro.
//
// Wanderlog ships with Expo Updates enabled (expo.modules.updates.ENABLED=true, CHECK_ON_LAUNCH=ALWAYS,
// EXPO_UPDATE_URL=https://expo-open-ota.wanderlog.com/manifest). On a later launch the runtime would
// fetch a fresh, unpatched JS bundle from that URL and run it instead of the embedded one, silently
// reverting the Unlock Pro byte-patch. Flip ENABLED to false (and blank EXPO_UPDATE_URL as a backstop)
// so the patched embedded bundle stays authoritative.
@Suppress("unused")
val disableExpoUpdatesPatch = resourcePatch(
    description = "Stops Wanderlog from pulling a fresh over-the-air JavaScript bundle on launch, so " +
        "the patched bundle stays in place. Applied automatically with Unlock Pro.",
) {
    compatibleWith(
        Compatibility(
            name = "Wanderlog",
            packageName = "com.wanderlog.android",
            appIconColor = 0xF75940,
            targets = listOf(AppTarget("2.208")),
        ),
    )

    execute {
        // The patcher's manifest DOM is not namespace-aware, so android:name comes back under its
        // literal qualified name rather than the resolved namespace.
        fun Element.androidName(): String = getAttribute("android:name")

        document("AndroidManifest.xml").use { document ->
            val metaData = document.getElementsByTagName("meta-data")
            val elements = (0 until metaData.length).mapNotNull { metaData.item(it) as? Element }

            val enabled = elements
                .singleOrNull { it.androidName() == "expo.modules.updates.ENABLED" }
                ?: throw PatchException(
                    "Wanderlog: the expo.modules.updates.ENABLED meta-data was not found in the " +
                        "manifest. The Expo Updates configuration changed for this version.",
                )
            enabled.setAttribute("android:value", "false")

            // Blank the update URL too, so even if some other path re-reads it the launcher has
            // nowhere to fetch a replacement bundle from.
            elements
                .singleOrNull { it.androidName() == "expo.modules.updates.EXPO_UPDATE_URL" }
                ?.setAttribute("android:value", "")
        }
    }
}
