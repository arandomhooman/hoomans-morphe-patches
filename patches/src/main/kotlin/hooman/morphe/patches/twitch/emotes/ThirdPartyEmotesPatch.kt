package hooman.morphe.patches.twitch.emotes

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION = "Lapp/morphe/extension/twitch/emotes/EmoteSupport;"

@Suppress("unused")
val thirdPartyEmotesPatch = bytecodePatch(
    name = "7TV and BTTV emotes",
    description = "Displays 7TV and BTTV emotes directly in Twitch live chat. It loads the global " +
        "emote sets and the emotes for the channel you are watching, then replaces matching emote " +
        "codes in incoming messages with their emote images. Sending works through normal Twitch " +
        "chat: type an emote's text code and send it normally. The patch does not add an emote " +
        "picker, 7TV/BTTV login, or account linking.",
) {
    compatibleWith(
        Compatibility(
            name = "Twitch",
            packageName = "tv.twitch.android.app",
            appIconColor = 0x9147FF,
            targets = listOf(AppTarget("30.7.2")),
        ),
    )

    extendWith("extensions/twitch.mpe")

    execute {
        val channelConstructor = ChannelConnectionConstructorFingerprint.method
        val channelInstructions = channelConstructor.instructions
        if (channelInstructions.lastOrNull()?.opcode != Opcode.RETURN_VOID) {
            throw PatchException("Twitch emotes: channel connection constructor no longer ends in return-void.")
        }
        channelConstructor.addInstructions(
            channelInstructions.lastIndex,
            "invoke-static { p1, p2 }, $EXTENSION->onChannelChanged(Ljava/lang/String;Ljava/lang/String;)V",
        )

        val messageClass = MessageRecyclerItemClassFingerprint.classDef
        val toStringMethod = messageClass.methods.singleOrNull { method ->
            method.name == "toString" &&
                method.returnType == "Ljava/lang/String;" &&
                method.parameterTypes.isEmpty()
        } ?: throw PatchException("Twitch emotes: MessageRecyclerItem.toString was not found uniquely.")
        val toStringInstructions = toStringMethod.implementation?.instructions
            ?: throw PatchException("Twitch emotes: MessageRecyclerItem.toString has no implementation.")
        val sourceMarkerIndex = toStringInstructions.indexOfFirst { instruction ->
            ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string ==
                ", sourceChannelId="
        }
        if (sourceMarkerIndex < 0) {
            throw PatchException("Twitch emotes: sourceChannelId marker was not found.")
        }
        val nextLabelIndex = toStringInstructions
            .drop(sourceMarkerIndex + 1)
            .indexOfFirst { instruction ->
                (instruction as? ReferenceInstruction)?.reference is StringReference
            }
            .let { relativeIndex ->
                if (relativeIndex < 0) toStringInstructions.size
                else sourceMarkerIndex + 1 + relativeIndex
            }
        val sourceFields = toStringInstructions
            .subList(sourceMarkerIndex + 1, nextLabelIndex)
            .mapNotNull { instruction ->
                (instruction as? ReferenceInstruction)?.reference as? FieldReference
            }
            .filter { field ->
                field.definingClass == messageClass.type && field.type == "Ljava/lang/String;"
            }
            .distinctBy { it.toString() }
        val sourceChannelField = sourceFields.singleOrNull()
            ?: throw PatchException(
                "Twitch emotes: expected one String field after sourceChannelId, found " +
                    sourceFields.size + ".",
            )

        fun isChatBindMethod(method: Method): Boolean {
            val instructions = method.implementation?.instructions ?: return false
            return method.returnType == "V" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0].toString() == messageClass.type &&
                method.parameterTypes[1].toString() == "Z" &&
                instructions.count { instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    reference?.toString() == CHAT_TEXT_SETTER
                } == 1
        }

        val rowClassDef = classDefByStrings("glideTarget")
            .singleOrNull { classDef ->
                val hasContextPin = classDef.methods.any { method ->
                    method.implementation?.instructions?.any { instruction ->
                        ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string ==
                            "getApplicationContext(...)"
                    } == true
                }
                hasContextPin && classDef.methods.any(::isChatBindMethod)
            } ?: throw PatchException(
                "Twitch emotes: chat row holder pinned by its Glide cleanup was not found uniquely.",
            )
        val rowClass = mutableClassDefBy(rowClassDef)
        val bindMethod = rowClass.methods.singleOrNull(::isChatBindMethod)
            ?: throw PatchException("Twitch emotes: chat row bind method was not found uniquely.")
        val implementation = bindMethod.implementation
            ?: throw PatchException("Twitch emotes: chat row binder has no implementation.")
        val textCalls = bindMethod.instructions.withIndex().filter { (_, instruction) ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.toString() == CHAT_TEXT_SETTER
        }.toList()
        if (textCalls.size != 1) {
            throw PatchException(
                "Twitch emotes: expected one chat TextView.setText call, found ${textCalls.size}.",
            )
        }
        val textCall = textCalls.single()
        val registers = textCall.value as? FiveRegisterInstruction
            ?: throw PatchException("Twitch emotes: chat TextView.setText is not a 35c invoke.")
        if (registers.registerCount != 3 || implementation.registerCount != 20 ||
            registers.registerC != 3
        ) {
            throw PatchException(
                "Twitch emotes: chat row register layout changed; refusing to reuse scratch v4.",
            )
        }

        // v4 is dead after this setText and is overwritten before its next read in 29.9.1. p1 is above
        // the four-bit iget register range, so first move the row model down, then load its source ID.
        bindMethod.addInstructions(
            textCall.index + 1,
            """
                move-object/from16 v4, p1
                iget-object v4, v4, $sourceChannelField
                invoke-static { v${registers.registerC}, v4 }, $EXTENSION->bind(Landroid/widget/TextView;Ljava/lang/String;)V
            """,
        )
    }
}
