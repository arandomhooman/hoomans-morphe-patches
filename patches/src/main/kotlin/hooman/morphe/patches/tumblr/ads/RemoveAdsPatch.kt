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
            targets = listOf(AppTarget("45.8.0.110")),
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

        // Part 2 -- server-inserted sponsored/promoted items the ad gate above does not catch. Part 1
        // disables the client ad SDKs (Nimbus and the others check UserInfo.v0() at startup), but the
        // dashboard response still carries ad/sponsored objects the server places directly, so those
        // keep rendering:
        //   - Blaze/promoted posts. A promoted regular Post carries isBlazed=true (or
        //     isTumblrSponsoredPost=true for Tumblr's own). The "blazed_post" type instead wraps the
        //     Post in a BlazedPost, which implements Timelineable directly (it does NOT extend Post), so
        //     an instance-of Post test alone misses it.
        //   - Server ad/sponsored cards. sponsored_day (RichBanner) and takeover_banner (Banner) extend
        //     SimpleAd; backfill_ad (BackFillAdResponse) and the SDK ad payloads (Nimbus, Facebook,
        //     Vungle, DisplayIO) are SimpleAd/ServerSideAd; client_side_ad and client_side_ad_waterfall
        //     are ClientAd / ClientSideAdMediation. None of these pass through the client SDK gate, so
        //     they render as in-feed sponsored posts.
        // Every dashboard item is built by the one timeline-object factory (pinned by the Vungle cast
        // string); returning null there drops the item before the feed builder collects it. The rumblr
        // model is unobfuscated and every subtype of SimpleAd / ServerSideAd / ClientAd is a dedicated
        // ad class (never an organic post), so an instance-of on the base is a safe, R8-stable drop that
        // leaves the following feed and reblogs untouched.
        //
        // The class descriptors are blind-injected refs, so verify each one is present -- a model rename
        // then fails loud at apply instead of silently matching nothing and letting ads back in. The
        // getters are the exception R8 does rename: q1()=getIsBlazed and K1()=getIsTumblrSponsoredPost
        // were re-derived from the Moshi ctor params (is_blazed -> field z1, is_tumblr_sponsored_post ->
        // field N0) for 45.8.0.110. The shape check below only catches a rename/removal; re-verify the
        // semantics against the decoded model on every bump.
        val post = mutableClassDefByOrNull("Lcom/tumblr/rumblr/model/post/Post;")
            ?: throw PatchException("Tumblr: com.tumblr.rumblr.model.post.Post not found -- model changed.")

        val requiredModelClasses = listOf(
            "Lcom/tumblr/rumblr/model/ClientAd;",
            "Lcom/tumblr/rumblr/model/ClientSideAdMediation;",
            "Lcom/tumblr/rumblr/model/advertising/ServerSideAd;",
            "Lcom/tumblr/rumblr/model/advertising/SimpleAd;",
            "Lcom/tumblr/rumblr/model/BlazedPost;",
        )
        val missing = requiredModelClasses.filter { mutableClassDefByOrNull(it) == null }
        if (missing.isNotEmpty()) {
            throw PatchException(
                "Tumblr: ad/sponsored model classes missing, the timeline model changed -- re-derive: " +
                    missing.joinToString(),
            )
        }

        val hasBlazedGetter = post.methods.any {
            it.name == "q1" && it.returnType == "Z" && it.parameterTypes.isEmpty()
        }
        val hasSponsoredGetter = post.methods.any {
            it.name == "K1" && it.returnType == "Ljava/lang/Boolean;" && it.parameterTypes.isEmpty()
        }
        if (!hasBlazedGetter || !hasSponsoredGetter) {
            throw PatchException(
                "Tumblr: Post blaze getters q1()Z / K1()Ljava/lang/Boolean; not found with the expected " +
                    "shape -- R8 renamed them; re-derive from the Moshi model.",
            )
        }

        val factory = TimelineObjectFactoryFingerprint.method
        factory.addInstructionsWithLabels(
            0,
            """
                if-eqz p1, :original
                invoke-virtual {p1}, Lcom/tumblr/rumblr/model/TimelineObject;->getData()Lcom/tumblr/rumblr/model/Timelineable;
                move-result-object v0
                instance-of v1, v0, Lcom/tumblr/rumblr/model/ClientAd;
                if-nez v1, :drop
                instance-of v1, v0, Lcom/tumblr/rumblr/model/ClientSideAdMediation;
                if-nez v1, :drop
                instance-of v1, v0, Lcom/tumblr/rumblr/model/advertising/ServerSideAd;
                if-nez v1, :drop
                instance-of v1, v0, Lcom/tumblr/rumblr/model/advertising/SimpleAd;
                if-nez v1, :drop
                instance-of v1, v0, Lcom/tumblr/rumblr/model/BlazedPost;
                if-nez v1, :drop
                instance-of v1, v0, Lcom/tumblr/rumblr/model/post/Post;
                if-eqz v1, :original
                check-cast v0, Lcom/tumblr/rumblr/model/post/Post;
                invoke-virtual {v0}, Lcom/tumblr/rumblr/model/post/Post;->q1()Z
                move-result v1
                invoke-virtual {v0}, Lcom/tumblr/rumblr/model/post/Post;->K1()Ljava/lang/Boolean;
                move-result-object v0
                sget-object v2, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                invoke-virtual {v2, v0}, Ljava/lang/Boolean;->equals(Ljava/lang/Object;)Z
                move-result v0
                or-int v0, v1, v0
                if-eqz v0, :original
                :drop
                const/4 v0, 0x0
                return-object v0
            """,
            ExternalLabel("original", factory.getInstruction(0)),
        )
    }
}
