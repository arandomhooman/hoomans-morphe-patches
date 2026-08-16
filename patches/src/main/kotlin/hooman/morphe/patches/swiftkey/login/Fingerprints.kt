package hooman.morphe.patches.swiftkey.login

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// Every automatic cloud-sign-in launch funnels through this helper: LauncherActivity calls it after
// setup, and the keyboard lifecycle calls it when the IME hides. Pin the R8-renamed method by all three
// preference keys plus its stable ContextWrapper / holder / boolean shape instead of patching one caller.
internal object CloudSignInPromptFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    strings = listOf(
        "onboarding_cloud_sign_in_enabled",
        "onboarding_cloud_sign_in_shown",
        "onboarding_onetime_bypass",
    ),
    custom = { method, _ ->
        method.parameterTypes.size == 3 &&
            method.parameterTypes[0].toString() == "Landroid/content/ContextWrapper;" &&
            method.parameterTypes[2].toString() == "Z"
    },
)
