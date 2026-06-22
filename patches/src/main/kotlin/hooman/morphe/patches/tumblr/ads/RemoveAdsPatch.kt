package hooman.morphe.patches.tumblr.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Removes the ads in Tumblr: the dashboard ads from the ad networks and the Blaze " +
        "posts (the paid-promoted posts other people pay to inject into the feed). It leaves your " +
        "own following feed and reblogs untouched. Premium content like ad-free badges and TumblrMart " +
        "items are sold by Tumblr's servers and still need a purchase.",
) {
    compatibleWith(
        Compatibility(
            name = "Tumblr",
            packageName = "com.tumblr",
            appIconColor = 0x001935,
            targets = listOf(AppTarget("45.0.0.109")),
        ),
    )

    execute {
        // Part 1 -- network ads. UserInfo.v0() is the ad-enablement gate: true unless the account is
        // premium/ad-free. Force it false so the client behaves ad-free everywhere (no ad SDK init, no
        // feed ad slots) without faking the premium entitlement, which stays server-checked. UserInfo
        // isn't obfuscated; pin by descriptor and match v0() by shape.
        val userInfo = mutableClassDefByOrNull("Lcom/tumblr/UserInfo;")
            ?: throw PatchException(
                "Tumblr: com.tumblr.UserInfo not found -- package layout changed.",
            )

        val adGate = userInfo.methods.filter { method ->
            method.name == "v0" &&
                AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "Z" &&
                method.parameterTypes.isEmpty()
        }
        if (adGate.size != 1) {
            throw PatchException(
                "Tumblr: expected exactly one static no-arg UserInfo.v0()Z ad-enablement gate, " +
                    "found ${adGate.size}. The gate shape changed; re-derive.",
            )
        }
        adGate.single().addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )

        // Part 2 -- Blaze posts: regular dashboard Posts someone paid to promote (isBlazed=true, or
        // isTumblrSponsoredPost=true for Tumblr's own), so the ad gate above doesn't catch them. Every
        // item runs through the timeline-object factory nf0.c0.b(); returning null there drops it before
        // the feed builder collects it. Here Post.p1()=getIsBlazed and I1()=getIsTumblrSponsoredPost
        // (both R8-renamed) -- drop the item when either is set, else fall through to the original factory.
        val factory = TimelineObjectFactoryFingerprint.method
        factory.addInstructionsWithLabels(
            0,
            """
                if-eqz p1, :original
                invoke-virtual {p1}, Lcom/tumblr/rumblr/model/TimelineObject;->getData()Lcom/tumblr/rumblr/model/Timelineable;
                move-result-object v0
                instance-of v1, v0, Lcom/tumblr/rumblr/model/post/Post;
                if-eqz v1, :original
                check-cast v0, Lcom/tumblr/rumblr/model/post/Post;
                invoke-virtual {v0}, Lcom/tumblr/rumblr/model/post/Post;->p1()Z
                move-result v1
                invoke-virtual {v0}, Lcom/tumblr/rumblr/model/post/Post;->I1()Ljava/lang/Boolean;
                move-result-object v0
                sget-object v2, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                invoke-virtual {v2, v0}, Ljava/lang/Boolean;->equals(Ljava/lang/Object;)Z
                move-result v0
                or-int v0, v1, v0
                if-eqz v0, :original
                const/4 v0, 0x0
                return-object v0
            """,
            ExternalLabel("original", factory.getInstruction(0)),
        )
    }
}
