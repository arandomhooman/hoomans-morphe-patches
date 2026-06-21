package hooman.morphe.patches.moovit.plus

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import hooman.morphe.patches.moovit.maps.useMapsApiKeyPatch

@Suppress("unused")
val unlockPlusPatch = bytecodePatch(
    name = "Unlock Moovit+",
    description = "Unlocks the Moovit+ extras without paying, like the extra sort and time-of-travel " +
        "options and compare-on-map, and stops the upgrade-to-Moovit+ popups that nag you on app open. " +
        "Things Moovit runs on its servers, like transit ticketing, still need the real subscription. " +
        "Pair this with Remove ads for the ad-free part of Moovit+.",
) {
    // Re-signing invalidates Moovit's bundled Maps key, so require a user-supplied one. The dependency
    // refuses to apply with a blank key, so a patched build can't end up with a dead map.
    dependsOn(useMapsApiKeyPatch)

    compatibleWith(
        Compatibility(
            name = "Moovit",
            packageName = "com.tranzmate",
            appIconColor = 0xFF6400,
            targets = listOf(AppTarget("5.194.0.1785")),
        ),
    )

    execute {
        // Force the subscription check true so every Moovit+ feature package reads "subscribed" and
        // flips to active. The subscribed-skus set behind it is filled by a server/billing round-trip,
        // so this only opens the client-gated features, not anything the backend re-validates.
        SubscriptionStateFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )

        // The feature-discovery popups (tap a locked Moovit+ feature) read the subscription above, so
        // forcing it true already silences those. Two upsell interstitials do NOT read it: they auto-show
        // on app entry from MoovitAppActivity, independent of subscription state, so they keep nagging an
        // unlocked user. Both are AbstractSubscriptionActivity subclasses launched only as interstitials
        // (no user-initiated path; the real purchase screen is MoovitPlusActivity, which is untouched).
        // Each already has a "skip and continue to where the user was going" method it runs when its own
        // config gate is off; that method launches the saved next-activity and finishes. Calling it at the
        // top of onReady makes the interstitial finish straight through, so the user lands on the screen
        // they were headed to with no popup. The activity class names survive R8 (com.moovit.app.* are
        // kept); the skip methods are obfuscated, so pin those by an anchor inside their body.

        // The full-screen "user acquisition" paywall. Its skip method (relaunchCallingActivity) is the only
        // private no-arg method that calls getActivityToStartOnFinish(); the only other no-arg method that
        // calls it is the public onSkipButtonClicked, so the private filter keeps the match unambiguous.
        run {
            val descriptor = "Lcom/moovit/app/plus/paywall/BlockPaywallActivity;"
            val activity = mutableClassDefByOrNull(descriptor)
                ?: throw PatchException(
                    "Moovit: BlockPaywallActivity not found. The upsell-interstitial layout changed.",
                )
            val onReady = activity.methods.singleOrNull {
                it.name == "onReady" && it.returnType == "V" &&
                    it.parameterTypes.singleOrNull()?.toString() == "Landroid/os/Bundle;"
            } ?: throw PatchException("Moovit: onReady(Bundle) not found in BlockPaywallActivity.")
            val skip = activity.methods.singleOrNull { method ->
                AccessFlags.PRIVATE.isSet(method.accessFlags) &&
                    method.parameterTypes.isEmpty() && method.returnType == "V" &&
                    method.implementation?.instructions?.any { instruction ->
                        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)
                            ?.name == "getActivityToStartOnFinish"
                    } == true
            } ?: throw PatchException(
                "Moovit: the BlockPaywall skip method (the lone private ()V calling " +
                    "getActivityToStartOnFinish) was not found. The upsell-interstitial layout changed.",
            )
            // relaunchCallingActivity is private, so it must be called with invoke-direct, not -virtual.
            onReady.addInstructions(0, "invoke-direct { p0 }, $descriptor->${skip.name}()V\nreturn-void")
        }

        // The onboarding upsell (a Moovit+ promo WebView). Its skip method (P0) is the only no-arg method
        // that reads the "activity_to_start_on_finish" extra, so that string pins it.
        run {
            val descriptor = "Lcom/moovit/app/plus/onboarding/MoovitPlusOnboardingActivity;"
            val activity = mutableClassDefByOrNull(descriptor)
                ?: throw PatchException(
                    "Moovit: MoovitPlusOnboardingActivity not found. The upsell-interstitial layout changed.",
                )
            val onReady = activity.methods.singleOrNull {
                it.name == "onReady" && it.returnType == "V" &&
                    it.parameterTypes.singleOrNull()?.toString() == "Landroid/os/Bundle;"
            } ?: throw PatchException("Moovit: onReady(Bundle) not found in MoovitPlusOnboardingActivity.")
            val skip = activity.methods.singleOrNull { method ->
                method.parameterTypes.isEmpty() && method.returnType == "V" &&
                    method.implementation?.instructions?.any { instruction ->
                        ((instruction as? ReferenceInstruction)?.reference as? StringReference)
                            ?.string == "activity_to_start_on_finish"
                    } == true
            } ?: throw PatchException(
                "Moovit: the onboarding skip method (the lone ()V reading \"activity_to_start_on_finish\") " +
                    "was not found. The upsell-interstitial layout changed.",
            )
            onReady.addInstructions(0, "invoke-virtual { p0 }, $descriptor->${skip.name}()V\nreturn-void")
        }
    }
}
