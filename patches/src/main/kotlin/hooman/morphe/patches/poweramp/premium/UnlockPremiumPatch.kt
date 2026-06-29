package hooman.morphe.patches.poweramp.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val LICENSE_STATE_CLASS_SUFFIX = "/i30;"
private const val FULL_VERIFIED_CACHED = "0x111"
private const val FEATURE_PACKAGE_PURCHASED = "0x1"
private const val FEATURE_PACKAGE_REFRESH_DONE = "0x3"
private const val FEATURE_PACKAGE_STATE_KEY = 0x0F0D0163
private const val PLAY_PURCHASE_CONTROLLER_KIND = "0x9"
private const val POWERAMP_PACKAGE_NAME = "com.maxmpz.audioplayer"
private const val POWERAMP_VERSION = "build-1025-bundle-play"
private const val POWERAMP_ICON_COLOR = 0xFF6A00

private class NativeProbePatch(
    val label: String,
    val signature: ByteArray,
    val offset: Int,
    val replacement: ByteArray,
)

private fun ints(vararg b: Int): ByteArray = b.map { it.toByte() }.toByteArray()

private fun movW(register: Int, value: Int): ByteArray {
    val instruction = 0x52800000 or (value shl 5) or register
    return byteArrayOf(
        instruction.toByte(),
        (instruction ushr 8).toByte(),
        (instruction ushr 16).toByte(),
        (instruction ushr 24).toByte(),
    )
}

// Native re-sign probes: 0x269/0x257/0x22b are size multipliers; 0x256/0x355 are guards.
private val arm64NativeProbePatches = listOf(
    NativeProbePatch(
        label = "guard-256-a",
        signature = ints(
            0x08, 0x34, 0x00, 0xD0, 0x08, 0x31, 0x40, 0xF9,
            0x08, 0x59, 0x49, 0x39, 0x08, 0xF3, 0xFF, 0x35,
        ),
        offset = 8,
        replacement = movW(register = 8, value = 0),
    ),
    NativeProbePatch(
        label = "guard-256-b",
        signature = ints(
            0x08, 0x34, 0x00, 0xD0, 0x08, 0x31, 0x40, 0xF9,
            0x08, 0x59, 0x49, 0x39, 0x68, 0xF0, 0xFF, 0x35,
        ),
        offset = 8,
        replacement = movW(register = 8, value = 0),
    ),
    NativeProbePatch(
        label = "size-269-main",
        signature = ints(
            0x08, 0x31, 0x40, 0xF9, 0x09, 0xA5, 0x49, 0x39,
            0xA9, 0x7E, 0x09, 0x1B, 0x46, 0x0D, 0x09, 0x2A,
        ),
        offset = 4,
        replacement = movW(register = 9, value = 1),
    ),
    NativeProbePatch(
        label = "guard-256-after-size",
        signature = ints(
            0x21, 0x2C, 0x0F, 0x91, 0x42, 0x18, 0x34, 0x91,
            0x63, 0xB8, 0x10, 0x91, 0x88, 0x4C, 0x05, 0x94,
            0x03, 0x00, 0x00, 0x14, 0x08, 0x59, 0x49, 0x39,
            0xE8, 0x00, 0x00, 0x34,
        ),
        offset = 20,
        replacement = movW(register = 8, value = 0),
    ),
    NativeProbePatch(
        label = "guard-256-c",
        signature = ints(
            0x08, 0x34, 0x00, 0x90, 0x08, 0x31, 0x40, 0xF9,
            0x08, 0x59, 0x49, 0x39, 0x48, 0x01, 0x00, 0x34,
        ),
        offset = 8,
        replacement = movW(register = 8, value = 0),
    ),
    NativeProbePatch(
        label = "size-257",
        signature = ints(
            0xA8, 0x30, 0x00, 0x90, 0xE0, 0x03, 0x13, 0xAA,
            0x41, 0x06, 0x80, 0x52, 0x08, 0x31, 0x40, 0xF9,
            0x62, 0x00, 0x80, 0x52, 0x08, 0x5D, 0x49, 0x39,
        ),
        offset = 20,
        replacement = movW(register = 8, value = 1),
    ),
    NativeProbePatch(
        label = "jni-guard-269",
        signature = ints(
            0x48, 0x30, 0x00, 0x90, 0x08, 0x31, 0x40, 0xF9,
            0x08, 0xA5, 0x49, 0x39, 0x1F, 0x05, 0x00, 0x71,
        ),
        offset = 8,
        replacement = movW(register = 8, value = 1),
    ),
    NativeProbePatch(
        label = "output-size-pair-a",
        signature = ints(
            0x08, 0x31, 0x40, 0xF9, 0xE0, 0x83, 0x00, 0xAD,
            0xE0, 0x03, 0x80, 0x3D, 0xE0, 0x03, 0x00, 0x91,
            0xE1, 0x03, 0x15, 0x2A, 0x00, 0x41, 0x20, 0x1E,
            0x09, 0xA5, 0x49, 0x39, 0x08, 0xAD, 0x48, 0x39,
            0xE2, 0x7E, 0x09, 0x1B, 0xC3, 0x7E, 0x08, 0x1B,
        ),
        offset = 24,
        replacement = movW(register = 9, value = 1) + movW(register = 8, value = 1),
    ),
    NativeProbePatch(
        label = "output-size-269-extra",
        signature = ints(
            0x3B, 0x30, 0x00, 0xB0, 0x29, 0x30, 0x00, 0xB0,
            0xE0, 0x1F, 0x00, 0xB9, 0x68, 0x33, 0x40, 0xF9,
            0x22, 0xA1, 0x42, 0xF9, 0xE0, 0x03, 0x16, 0xAA,
            0xE1, 0x03, 0x1A, 0xAA, 0x15, 0xA5, 0x49, 0x39,
            0xC8, 0x02, 0x40, 0xF9,
        ),
        offset = 28,
        replacement = movW(register = 21, value = 1),
    ),
    NativeProbePatch(
        label = "output-size-22b-extra",
        signature = ints(
            0x68, 0x33, 0x40, 0xF9, 0xFB, 0x03, 0x17, 0x2A,
            0x29, 0x30, 0x00, 0xB0, 0x22, 0xA9, 0x42, 0xF9,
            0xFC, 0x03, 0x00, 0x2A, 0xE0, 0x03, 0x16, 0xAA,
            0x17, 0xAD, 0x48, 0x39, 0xC8, 0x02, 0x40, 0xF9,
        ),
        offset = 24,
        replacement = movW(register = 23, value = 1),
    ),
    NativeProbePatch(
        label = "output-size-pair-b",
        signature = ints(
            0x08, 0x31, 0x40, 0xF9, 0xA0, 0x83, 0x3D, 0xAD,
            0x09, 0xA5, 0x49, 0x39, 0x08, 0xAD, 0x48, 0x39,
            0xA0, 0x03, 0x9A, 0x3C, 0xE1, 0x03, 0x17, 0x2A,
            0x60, 0x41, 0x20, 0x1E, 0x41, 0x41, 0x20, 0x1E,
            0x02, 0x7F, 0x09, 0x1B, 0xA3, 0x7E, 0x08, 0x1B,
            0xF4, 0xFC, 0xFF, 0x97,
        ),
        offset = 8,
        replacement = movW(register = 9, value = 1) + movW(register = 8, value = 1),
    ),
    NativeProbePatch(
        label = "output-size-pair-c",
        signature = ints(
            0x08, 0x31, 0x40, 0xF9, 0xE0, 0x83, 0x00, 0xAD,
            0x09, 0xA5, 0x49, 0x39, 0x08, 0xAD, 0x48, 0x39,
            0xE0, 0x03, 0x80, 0x3D, 0xE1, 0x03, 0x15, 0x2A,
            0x20, 0x41, 0x20, 0x1E, 0x01, 0x41, 0x20, 0x1E,
            0xE2, 0x7E, 0x09, 0x1B, 0xC3, 0x7E, 0x08, 0x1B,
            0xCE, 0xFB, 0xFF, 0x97,
        ),
        offset = 8,
        replacement = movW(register = 9, value = 1) + movW(register = 8, value = 1),
    ),
    NativeProbePatch(
        label = "guard-355",
        signature = ints(
            0xA8, 0x2F, 0x00, 0xB0, 0x08, 0x31, 0x40, 0xF9,
            0x08, 0x55, 0x4D, 0x39, 0x1F, 0x0D, 0x00, 0x71,
        ),
        offset = 8,
        replacement = movW(register = 8, value = 0),
    ),
)

@Suppress("unused")
val patchNativeTamperChecksPatch = rawResourcePatch(
    description = "Neutralizes Poweramp's native re-sign checks in libpowerampcore.so so the patched " +
        "arm64 build can finish startup and load the audio engine.",
) {
    compatibleWith(
        Compatibility(
            name = "Poweramp",
            packageName = POWERAMP_PACKAGE_NAME,
            appIconColor = POWERAMP_ICON_COLOR,
            targets = listOf(AppTarget(POWERAMP_VERSION)),
        ),
    )

    execute {
        val libPath = "lib/arm64-v8a/libpowerampcore.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            throw PatchException(
                "$libPath not found in the APK. Poweramp's native tamper bypass is derived for the " +
                    "arm64 build; merge an arm64 split into a universal APK before patching.",
            )
        }

        val bytes = lib.readBytes()
        for (entry in arm64NativeProbePatches) {
            val match = bytes.findUnique(entry.signature, entry.label)
                ?: throw PatchException(
                    "Poweramp native probe ${entry.label} not found in $libPath. This patch targets " +
                        "Poweramp $POWERAMP_VERSION; re-derive the native signatures for this build.",
                )

            entry.replacement.copyInto(bytes, match + entry.offset)
        }
        lib.writeBytes(bytes)
    }
}

private fun ByteArray.findUnique(pattern: ByteArray, label: String): Int? {
    var found: Int? = null
    val last = size - pattern.size
    outer@ for (i in 0..last) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        if (found != null) {
            throw PatchException("Poweramp native probe $label is ambiguous (matched more than once).")
        }
        found = i
    }
    return found
}

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks Poweramp's full version state and Feature Package #1.",
) {
    dependsOn(patchNativeTamperChecksPatch)

    compatibleWith(
        Compatibility(
            name = "Poweramp",
            packageName = POWERAMP_PACKAGE_NAME,
            appIconColor = POWERAMP_ICON_COLOR,
            targets = listOf(AppTarget(POWERAMP_VERSION)),
        ),
    )

    execute {
        fun Method.stringLiterals(): Set<String> =
            implementation?.instructions?.mapNotNull { instruction ->
                (instruction as? ReferenceInstruction)?.reference
                    ?.let { it as? StringReference }?.string
            }?.toSet().orEmpty()

        fun Method.singleStateFieldReferenceIndex(label: String, fieldName: String): Int {
            val matches = implementation?.instructions?.withIndex()?.filter { (_, instruction) ->
                val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                field != null &&
                    field.definingClass.endsWith(LICENSE_STATE_CLASS_SUFFIX) &&
                    field.name == fieldName
            }.orEmpty()

            if (matches.size != 1) {
                throw PatchException(
                    "Poweramp: expected exactly 1 state reference ($fieldName) in $label, " +
                        "found ${matches.size}. Re-derive the Poweramp state patch.",
                )
            }

            return matches.single().index
        }

        fun Method.singleLicenseStateReferenceIndex(label: String) =
            singleStateFieldReferenceIndex(label, "H")

        fun Method.stateFieldNameForKey(label: String, stateKey: Int): String {
            val instructions = implementation?.instructions
                ?: throw PatchException("Poweramp: $label has no implementation.")
            val keyIndex = instructions.indexOfFirst { instruction ->
                (instruction as? NarrowLiteralInstruction)?.narrowLiteral == stateKey
            }
            if (keyIndex < 0) {
                throw PatchException("Poweramp: state key 0x${stateKey.toString(16)} not found in $label.")
            }

            return instructions.drop(keyIndex + 1).asSequence().mapNotNull { instruction ->
                val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                field?.takeIf {
                    it.definingClass.endsWith(LICENSE_STATE_CLASS_SUFFIX) &&
                        it.type.endsWith("/m10;")
                }?.name
            }.firstOrNull()
                ?: throw PatchException("Poweramp: state field for 0x${stateKey.toString(16)} not found in $label.")
        }

        fun opcodeName(instruction: com.android.tools.smali.dexlib2.iface.instruction.Instruction) =
            instruction.opcode.name.replace('-', '_').replace('/', '_')

        // i30.H is the full/trial state; 0x111 is Poweramp's cached full-version value.
        val licenseState = classDefByStrings("sbuf").singleOrNull()
            ?: throw PatchException(
                "Poweramp: license state holder (sbuf) not found or ambiguous.",
            )
        if ("getSbuf()Ljava/nio/ByteBuffer;" !in licenseState.methods.flatMap { it.stringLiterals() }) {
            throw PatchException(
                "Poweramp: class matched by sbuf is missing getSbuf()Ljava/nio/ByteBuffer;.",
            )
        }
        val mutableLicenseState = mutableClassDefBy(licenseState)
        val classInitializer = mutableLicenseState.methods.singleOrNull { method ->
            method.name == "<clinit>" &&
                method.returnType == "V" &&
                method.parameterTypes.isEmpty()
        } ?: throw PatchException("Poweramp: license state class initializer not found.")

        val hStoreIndex = classInitializer.singleLicenseStateReferenceIndex("license state initializer")
        val hInitializerIndex = classInitializer.instructions.withIndex().filter { (index, instruction) ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            index < hStoreIndex &&
                reference != null &&
                reference.name == "P" &&
                reference.parameterTypes == listOf("I") &&
                reference.returnType.endsWith("/m10;")
        }.lastOrNull()?.index ?: throw PatchException(
            "Poweramp: initializer for license state H not found before the H field store.",
        )
        classInitializer.addInstructions(hInitializerIndex, "const/16 v1, $FULL_VERIFIED_CACHED")

        val baseApplication = mutableClassDefByOrNull("Lcom/maxmpz/app/base/BaseApplication;")
            ?: throw PatchException("Poweramp: BaseApplication not found.")
        val getIntState = baseApplication.methods.singleOrNull { method ->
            method.name == "getIntState" &&
                method.returnType == "I" &&
                method.parameterTypes == listOf("I")
        } ?: throw PatchException("Poweramp: BaseApplication.getIntState not found.")
        val featurePackageStateFieldName =
            getIntState.stateFieldNameForKey("BaseApplication.getIntState", FEATURE_PACKAGE_STATE_KEY)
        val featureStateStoreIndex = classInitializer
            .singleStateFieldReferenceIndex("feature package state initializer", featurePackageStateFieldName)
        val featureStateInitializerIndex = classInitializer.instructions.withIndex().filter { (index, instruction) ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            index < featureStateStoreIndex &&
                reference != null &&
                reference.name == "P" &&
                reference.parameterTypes == listOf("I") &&
                reference.returnType.endsWith("/m10;")
        }.lastOrNull()?.index ?: throw PatchException(
            "Poweramp: initializer for Feature Package #1 state not found before the field store.",
        )
        classInitializer.addInstructions(featureStateInitializerIndex, "const/4 v2, $FEATURE_PACKAGE_PURCHASED")

        val preferenceStore = classDefByStrings("hide_verified").singleOrNull()
            ?: throw PatchException(
                "Poweramp: shared preference restore class not found or ambiguous.",
            )
        if ("luae" !in preferenceStore.methods.flatMap { it.stringLiterals() }) {
            throw PatchException(
                "Poweramp: class matched by hide_verified is missing luae; wrong preference class.",
            )
        }
        val mutablePreferenceStore = mutableClassDefBy(preferenceStore)
        val restoreLicenseState = mutablePreferenceStore.methods.singleOrNull { method ->
            method.returnType == "V" &&
                method.parameterTypes.isEmpty() &&
                method.stringLiterals().let { strings ->
                    "hide_verified" in strings && "luae" in strings && "r" in strings
                }
        } ?: throw PatchException(
            "Poweramp: shared preference restore method for license state not found.",
        )
        restoreLicenseState.addInstructions(
            restoreLicenseState.singleLicenseStateReferenceIndex("shared preference restore"),
            "const/16 v1, $FULL_VERIFIED_CACHED",
        )
        restoreLicenseState.addInstructions(
            restoreLicenseState.singleStateFieldReferenceIndex(
                "shared preference restore",
                featurePackageStateFieldName,
            ),
            "const/4 v1, $FEATURE_PACKAGE_PURCHASED",
        )

        val onCreate = baseApplication.methods.singleOrNull { method ->
            method.name == "onCreate" &&
                method.returnType == "V" &&
                method.parameterTypes.isEmpty()
        } ?: throw PatchException("Poweramp: BaseApplication.onCreate not found.")
        val firstTamperStoreIndex = onCreate.instructions.withIndex().firstOrNull { (_, instruction) ->
            opcodeName(instruction).equals("SPUT_BOOLEAN", ignoreCase = true) &&
                ((instruction as? ReferenceInstruction)?.reference as? FieldReference)
                    ?.definingClass?.endsWith("/p5;") == true
        }?.index ?: throw PatchException(
            "Poweramp: signature-check state store not found in BaseApplication.onCreate.",
        )
        val invalidSignatureValueIndex = onCreate.instructions.withIndex()
            .take(firstTamperStoreIndex)
            .lastOrNull { (_, instruction) ->
                opcodeName(instruction).equals("CONST_4", ignoreCase = true) &&
                    (instruction as? OneRegisterInstruction)?.registerA == 2 &&
                    (instruction as? NarrowLiteralInstruction)?.narrowLiteral == 0
            }
            ?.index ?: throw PatchException(
            "Poweramp: signature-check invalid value not found in BaseApplication.onCreate.",
        )
        onCreate.addInstructions(invalidSignatureValueIndex + 1, "const/4 v2, 0x1")

        val featurePackageStateIndex = getIntState
            .singleStateFieldReferenceIndex("BaseApplication.getIntState", featurePackageStateFieldName)
        val featurePackageStateReadIndex = getIntState.instructions.withIndex()
            .drop(featurePackageStateIndex + 1)
            .firstOrNull { (_, instruction) ->
                opcodeName(instruction).startsWith("IGET", ignoreCase = true)
            }?.index ?: throw PatchException(
            "Poweramp: Feature Package #1 state read not found in BaseApplication.getIntState.",
        )
        getIntState.addInstructions(featurePackageStateReadIndex + 1, "const/4 p1, $FEATURE_PACKAGE_PURCHASED")

        val licenseResultHandler = baseApplication.methods.singleOrNull { method ->
            method.returnType == "V" &&
                method.stringLiterals().let { strings ->
                    "res" in strings &&
                        "store" in strings &&
                        "purchased" in strings &&
                        "apm_email" in strings
                }
        } ?: throw PatchException(
            "Poweramp: license result handler (res/store/purchased/apm_email) not found.",
        )
        licenseResultHandler.addInstructions(
            licenseResultHandler.singleLicenseStateReferenceIndex("license result handler") + 1,
            "const/16 v0, $FULL_VERIFIED_CACHED",
        )

        val featurePackagePreference = mutableClassDefByOrNull(
            "Lcom/maxmpz/audioplayer/preference/FeaturePackPref;",
        ) ?: throw PatchException("Poweramp: FeaturePackPref not found.")
        val updateFeaturePackagePreference = featurePackagePreference.methods.singleOrNull { method ->
            method.name == "update" &&
                method.returnType == "V" &&
                method.parameterTypes.isEmpty()
        } ?: throw PatchException("Poweramp: FeaturePackPref.update not found.")
        val purchasedFlagStoreIndex = updateFeaturePackagePreference.instructions.withIndex()
            .firstOrNull { (_, instruction) ->
                opcodeName(instruction).equals("IPUT_BOOLEAN", ignoreCase = true) &&
                    ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.let { field ->
                        field.definingClass == "Lcom/maxmpz/audioplayer/preference/BuyPrefBase;" &&
                            field.name == "n" &&
                            field.type == "Z"
                    } == true
            }?.index ?: throw PatchException(
            "Poweramp: FeaturePackPref purchased flag store not found.",
        )
        val featurePackagePurchasedField = updateFeaturePackagePreference.instructions.withIndex()
            .take(purchasedFlagStoreIndex)
            .mapNotNull { (_, instruction) ->
                (instruction as? ReferenceInstruction)?.reference as? FieldReference
            }
            .lastOrNull { field ->
                field.type == "Z" &&
                    field.definingClass != "Lcom/maxmpz/audioplayer/preference/BuyPrefBase;"
            } ?: throw PatchException(
            "Poweramp: FeaturePackPref source purchased flag not found.",
        )
        val purchaseControllerClass = mutableClassDefByOrNull(featurePackagePurchasedField.definingClass)
            ?: throw PatchException("Poweramp: purchase controller base class not found.")
        val featurePackageProductTextField = updateFeaturePackagePreference.instructions.withIndex()
            .mapNotNull { (_, instruction) ->
                (instruction as? ReferenceInstruction)?.reference as? FieldReference
            }
            .firstOrNull { field ->
                field.definingClass == purchaseControllerClass.type &&
                    field.type == "Ljava/lang/String;"
            } ?: throw PatchException("Poweramp: FeaturePackPref product text field not found.")
        val purchaseStateNotifier = purchaseControllerClass.methods.singleOrNull { method ->
            method.returnType == "V" &&
                method.parameterTypes == listOf(purchaseControllerClass.type)
        } ?: throw PatchException("Poweramp: purchase-state notifier method not found.")
        val startPurchaseRefresh = purchaseControllerClass.methods.singleOrNull { method ->
            method.returnType == "V" &&
                method.parameterTypes.isEmpty() &&
                "__Executors" in method.stringLiterals()
        } ?: throw PatchException("Poweramp: purchase refresh worker launcher not found.")
        startPurchaseRefresh.addInstructionsWithLabels(
            0,
            """
                iget v0, p0, ${purchaseControllerClass.type}->K:I
                const/16 v1, $PLAY_PURCHASE_CONTROLLER_KIND
                if-ne v0, v1, :original_purchase_refresh
                const/4 v0, $FEATURE_PACKAGE_REFRESH_DONE
                iput v0, p0, ${purchaseControllerClass.type}->f:I
                const/4 v0, $FEATURE_PACKAGE_PURCHASED
                iput-boolean v0, p0, ${purchaseControllerClass.type}->${featurePackagePurchasedField.name}:Z
                const-string v1, ""
                iput-object v1, p0, ${purchaseControllerClass.type}->${featurePackageProductTextField.name}:Ljava/lang/String;
                invoke-static {p0}, ${purchaseControllerClass.type}->${purchaseStateNotifier.name}(${purchaseControllerClass.type})V
                return-void
            """,
            ExternalLabel("original_purchase_refresh", startPurchaseRefresh.getInstruction(0)),
        )
        updateFeaturePackagePreference.addInstructions(
            purchasedFlagStoreIndex,
            "const/4 v3, $FEATURE_PACKAGE_PURCHASED",
        )
    }
}
