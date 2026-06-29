package hooman.morphe.patches.batteryguru.pro

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock PRO",
    description = "Unlocks Battery Guru's PRO and removes the ads. PRO is decided on the device, so " +
        "the deep sleep stats, charge history, themes, and the ad-free view all open up.",
) {
    compatibleWith(
        Compatibility(
            name = "Battery Guru",
            packageName = "com.paget96.batteryguru",
            appIconColor = 0x1B7080,
            targets = listOf(
                AppTarget("2.4.8.1"),
                AppTarget("2.5.0.2-beta1"),
            ),
        ),
    )

    execute {
        // PRO is one local Play-Billing computation in the billing repository (R8-obfuscated to cs4,
        // injected app-wide as billingProvider). premium = purchaseOwned || rewardedActive: the live
        // "active now" check reads the "video_time" pref and returns video_time > now, and the single
        // writer pushes that result into a LiveData every gate (and the banner-ad container) observes.
        // A free account never owns a purchase and has no rewarded time, so both the live read and the
        // stored flag sit at false. Force the live read true and pin the stored flag true so PRO reads
        // active from launch regardless of the billing/rewarded result. Both are durable because only
        // the billing grant and the rewarded grant ever write premium-true, and the patch ignores them.

        fun Method.stringLiterals(): Set<String> =
            implementation?.instructions?.mapNotNull { instruction ->
                (instruction as? ReferenceInstruction)?.reference
                    ?.let { it as? StringReference }?.string
            }?.toSet().orEmpty()

        // cs4 holds the subscription product IDs and the DataStore pref keys; "last_product_id" is the
        // one literal unique to it (the product list and "video_time"/"rewarded_ad_count" recur in the
        // billing helpers), so it pins the class through R8. Confirm the other two keys are present so a
        // future rename can't bind the wrong class silently.
        val billingRepo = classDefByStrings("last_product_id").singleOrNull()
            ?: throw PatchException(
                "Battery Guru: billing repository (\"last_product_id\") not found or ambiguous — the " +
                    "premium layout changed.",
            )
        val classStrings = billingRepo.methods.flatMap { it.stringLiterals() }.toSet()
        if ("video_time" !in classStrings || "rewarded_ad_count" !in classStrings) {
            throw PatchException(
                "Battery Guru: the class matched on \"last_product_id\" is missing the expected pref " +
                    "keys — wrong class, re-derive the pin.",
            )
        }

        val mutableBillingRepo = mutableClassDefBy(billingRepo)

        // The live "premium active now" query: returns Object (a boxed Boolean), one param, reads
        // "video_time" and compares it against System.currentTimeMillis(). The two rewarded-grant
        // helpers also read "video_time" and call currentTimeMillis, but they additionally write
        // "rewarded_ad_count"; the live query does not, which separates it cleanly.
        val activeNow = mutableBillingRepo.methods.filter { method ->
            method.returnType == "Ljava/lang/Object;" &&
                method.parameterTypes.size == 1 &&
                method.stringLiterals().let { "video_time" in it && "rewarded_ad_count" !in it } &&
                method.implementation?.instructions?.any { instruction ->
                    (instruction as? ReferenceInstruction)?.reference?.toString()
                        ?.contains("currentTimeMillis") == true
                } == true
        }
        if (activeNow.size != 1) {
            throw PatchException(
                "Battery Guru: expected exactly 1 live premium query (reads \"video_time\", compares " +
                    "currentTimeMillis, no \"rewarded_ad_count\"), found ${activeNow.size}. Re-derive.",
            )
        }
        activeNow.single().addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                return-object v0
            """,
        )

        // The single writer of the app-wide premium state: void, one Boolean param, body just pushes
        // the value into the observed LiveData. Overwrite its argument with TRUE so every write path
        // (billing connect, rewarded grant, the init seed) stores premium active, which keeps the
        // LiveData every gate observes true even before any live read runs.
        val premiumWriter = mutableBillingRepo.methods.filter { method ->
            method.returnType == "V" && method.parameterTypes == listOf("Ljava/lang/Boolean;")
        }
        if (premiumWriter.size != 1) {
            throw PatchException(
                "Battery Guru: expected exactly 1 premium-state writer ((Ljava/lang/Boolean;)V), " +
                    "found ${premiumWriter.size}. The writer shape changed; re-derive.",
            )
        }
        premiumWriter.single().addInstructions(
            0,
            "sget-object p1, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;",
        )
    }
}
