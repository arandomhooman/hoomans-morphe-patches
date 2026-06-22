package hooman.morphe.patches.alphaprogression.premium

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

/**
 * Capacitor/Ionic app: feature logic lives in the JS bundle, not the DEX. Pro is one client-side
 * flag `proVersion.isActive`, read everywhere but written in only four places. All four are forced
 * truthy so it holds on the free-account paths (boot default and the not-entitled reset) too.
 */
@Suppress("unused")
val unlockPremiumPatch = rawResourcePatch(
    name = "Unlock Premium",
    description = "Unlocks Alpha Progression's premium training tools without a subscription, like " +
        "the training-plan generator, charts, exercise evaluations, the warmup calculator, and more. " +
        "They run on the workout data already on your device, so they keep working offline.",
) {
    compatibleWith(
        Compatibility(
            name = "Alpha Progression",
            packageName = "com.alphaprogression.alphaprogression",
            appIconColor = 0x0091FF,
            targets = listOf(AppTarget("6.8.1")),
        ),
    )

    execute {
        val assetsDir = get("assets/public/assets")
        if (!assetsDir.isDirectory) {
            throw PatchException(
                "assets/public/assets/ not found — apply this to a merged universal APK (the JS " +
                    "bundle lives in the base split; merge an xapk with APKEditor m first).",
            )
        }

        // The bundle filename is content-hashed and changes every release, so find it by content.
        val declaresState = "window.proVersion="
        val bundles = (assetsDir.listFiles { file ->
            file.isFile && file.name.startsWith("index-") && file.name.endsWith(".js")
        } ?: emptyArray()).filter { it.readText().contains(declaresState) }
        val bundle = bundles.singleOrNull()
            ?: throw PatchException(
                "Expected exactly one JS bundle declaring `$declaresState`, found ${bundles.size}; " +
                    "re-derive the anchors for this app version.",
            )

        var js = bundle.readText()

        // Boot default: `window.proVersion = ... || {}`. Anchor on the unique declaration, then fix
        // the first `||{}` after it (`||{}` alone occurs ~75x, so it can't be matched directly).
        val init = js.indexOf(declaresState)
        if (init < 0 || js.indexOf(declaresState, init + 1) >= 0) {
            throw PatchException("`$declaresState` anchor missing or not unique; re-derive for this version.")
        }
        val emptyDefault = js.indexOf("||{}", init)
        if (emptyDefault < 0 || emptyDefault - init > 160) {
            throw PatchException("Boot-init default `||{}` not found after `$declaresState`; re-derive.")
        }
        js = js.substring(0, emptyDefault) + "||{isActive:!0}" + js.substring(emptyDefault + 4)

        js = js.replaceOnce("(proVersion={})", "(proVersion={isActive:!0})", "free-reset")
        js = js.replaceOnce("proVersion={isActive:i,", "proVersion={isActive:!0,", "trial")
        js = js.replaceOnce(
            "proVersion=a,localStorage.setItem(\"pro-version\"",
            "proVersion=(a.isActive=!0,a),localStorage.setItem(\"pro-version\"",
            "toggle-persist",
        )

        bundle.writeText(js)
    }
}

/** Replaces the sole occurrence of [find]; throws if it is absent or ambiguous. */
private fun String.replaceOnce(find: String, replacement: String, label: String): String {
    val first = indexOf(find)
    if (first < 0) throw PatchException("Anchor not found ($label); the minified JS changed, re-derive for this version.")
    if (indexOf(find, first + 1) >= 0) throw PatchException("Anchor ambiguous ($label, matched more than once).")
    return substring(0, first) + replacement + substring(first + find.length)
}
