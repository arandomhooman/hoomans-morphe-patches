package hooman.morphe.patches.swiftkey.login

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import hooman.morphe.patches.swiftkey.swiftKeyCompatibility
import org.w3c.dom.Element

// The stock value is already false in 9.13.13.5. Keep it explicit in the patched APK, while the
// bytecode patch below prevents a runtime preference override from re-enabling the automatic prompt.
private val disableCloudSignInResourcePatch = resourcePatch(
    description = "Keeps SwiftKey's optional cloud sign-in onboarding disabled. Applied automatically " +
        "with Disable cloud sign-in prompt.",
) {
    compatibleWith(swiftKeyCompatibility)

    execute {
        document("res/values/bools.xml").use { document ->
            val matches = document.getElementsByTagName("bool")
                .let { nodes ->
                    (0 until nodes.length)
                        .mapNotNull { nodes.item(it) as? Element }
                        .filter { it.getAttribute("name") == "onboarding_cloud_sign_in_enabled" }
                }
            val enabled = matches.singleOrNull() ?: throw PatchException(
                "SwiftKey: onboarding_cloud_sign_in_enabled was not found uniquely. The launcher " +
                    "onboarding resources changed for this version.",
            )
            enabled.textContent = "false"
        }
    }
}

@Suppress("unused")
val disableCloudSignInPromptPatch = bytecodePatch(
    name = "Disable cloud sign-in prompt",
    description = "Keeps the optional Microsoft cloud sign-in onboarding from replacing SwiftKey's " +
        "launcher, so local keyboard setup and settings remain usable without an account. This does " +
        "not sign in or unlock cloud sync and other account-backed features.",
) {
    compatibleWith(swiftKeyCompatibility)
    dependsOn(disableCloudSignInResourcePatch)

    execute {
        // This helper is used only for automatic onboarding launches. Manual Account settings invoke
        // the sign-in flow directly, so they and real account state remain untouched.
        CloudSignInPromptFingerprint.method.addInstructions(0, "return-void")
    }
}
