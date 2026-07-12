package hooman.morphe.patches.alphaprogression.premium

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

/**
 * Capacitor/Ionic app: feature logic lives in the JS bundle, not the DEX. Pro is one client-side
 * flag `proVersion.isActive`, read everywhere but written in only four places. All four are forced
 * truthy so it holds on the free-account paths (boot default and the not-entitled reset) too.
 *
 * 6.8.1 and 7.1.1 minify differently (dot vs bracket, literal vs obfuscated localStorage keys), so
 * the four write sites are re-derived per shape. Bundle is located by content, not the hashed name.
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
            targets = listOf(
                AppTarget("6.8.1"),
                AppTarget("7.1.1"),
            ),
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

        // Content-hashed filename changes every release; pick the bundle that owns proVersion state.
        // 6.8.1: `window.proVersion=...`. 7.x: `window[obf]=JSON[parse](localStorage['getItem']('pro-version'))||{}`.
        val bootV6 = "window.proVersion="
        val bootV7 = "localStorage['getItem']('pro-version'))||{}"
        val bundles = (assetsDir.listFiles { file ->
            file.isFile && file.name.startsWith("index-") && file.name.endsWith(".js")
        } ?: emptyArray()).filter {
            val text = it.readText()
            text.contains(bootV6) || text.contains(bootV7)
        }
        val bundle = bundles.singleOrNull()
            ?: throw PatchException(
                "Expected exactly one JS bundle declaring proVersion boot state, found ${bundles.size}; " +
                    "re-derive the anchors for this app version.",
            )

        var js = bundle.readText()
        val isV7 = js.contains(bootV7)
        val isV6 = js.contains(bootV6)
        if (isV7 == isV6) {
            throw PatchException(
                "Could not tell 6.8.1 vs 7.x JS shape (v6=$isV6 v7=$isV7); re-derive for this version.",
            )
        }

        if (isV7) {
            // 7.1.1 (obfuscator-io style): quoted keys, mangled locals, setItem via decode table.
            js = js.replaceOnce(
                bootV7,
                "localStorage['getItem']('pro-version'))||{isActive:!0}",
                "boot-default",
            )
            js = js.replaceOnce("(proVersion={})", "(proVersion={isActive:!0})", "free-reset")
            // Trial / server-sub path in checkFree; value is a mangled local (_0x3085f2 on 7.1.1).
            js = js.replaceOnce(
                "proVersion={'isActive':_0x3085f2,",
                "proVersion={'isActive':!0,",
                "trial",
            )
            // toggle() persistence chokepoint: param is _0x2224d0 on 7.1.1.
            js = js.replaceOnce(
                "proVersion=_0x2224d0,localStorage",
                "proVersion=(_0x2224d0.isActive=!0,_0x2224d0),localStorage",
                "toggle-persist",
            )
        } else {
            // 6.8.1: literal property access and setItem("pro-version", ...).
            // Boot default: anchor on the unique declaration, then fix the first `||{}` after it
            // (`||{}` alone occurs ~75x, so it can't be matched directly).
            val init = js.indexOf(bootV6)
            if (init < 0 || js.indexOf(bootV6, init + 1) >= 0) {
                throw PatchException("`$bootV6` anchor missing or not unique; re-derive for this version.")
            }
            val emptyDefault = js.indexOf("||{}", init)
            if (emptyDefault < 0 || emptyDefault - init > 160) {
                throw PatchException("Boot-init default `||{}` not found after `$bootV6`; re-derive.")
            }
            js = js.substring(0, emptyDefault) + "||{isActive:!0}" + js.substring(emptyDefault + 4)

            js = js.replaceOnce("(proVersion={})", "(proVersion={isActive:!0})", "free-reset")
            js = js.replaceOnce("proVersion={isActive:i,", "proVersion={isActive:!0,", "trial")
            js = js.replaceOnce(
                "proVersion=a,localStorage.setItem(\"pro-version\"",
                "proVersion=(a.isActive=!0,a),localStorage.setItem(\"pro-version\"",
                "toggle-persist",
            )
        }

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
