package hooman.morphe.patches.replaio.premium

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
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks Replaio's premium and removes the ads, so the equalizer, sleep timer, " +
        "alarm, recording, and favorites all open up. Data Replaio keeps on its servers (synced " +
        "favorites past the free cap) still needs an account that paid.",
) {
    compatibleWith(
        Compatibility(
            name = "Replaio",
            packageName = "com.hv.replaio",
            appIconColor = 0x2196F3,
            targets = listOf(AppTarget("3.3.9")),
        ),
    )

    execute {
        // Premium is one cached Play-Billing flag. The local billing cache (R8-renamed; was xb.a, now
        // ff.a, so pin by the persisted token, not the name) stores "DSW3-JJK" in the "fjsdhfjh4" pref
        // when an account paid and "DSW3-JJX" otherwise, and the reader d()Z returns whether the pref
        // equals "DSW3-JJK". Every gate reads that cache directly: the ad-enable predicate ANDs in
        // !d(), and the equalizer/sleep timer/alarm/recording/favorites checks all call d(). The pref
        // token can't change across versions without wiping paying users, so it survives R8 churn.
        fun Method.readsString(value: String): Boolean =
            implementation?.instructions?.any { instruction ->
                (instruction as? ReferenceInstruction)?.reference
                    ?.let { it as? StringReference }?.string == value
            } == true

        // "DSW3-JJK" is unique to this class app-wide, but it sits in two methods: the reader d()Z and
        // the writer (boolean, t0)V. The no-arg ()Z shape singles out the reader (the writer takes
        // args and returns void), and isEmpty()Z is excluded because it never touches "DSW3-JJK".
        val cache = classDefByStrings("DSW3-JJK").singleOrNull()
            ?: throw PatchException(
                "Replaio: billing cache holding \"DSW3-JJK\" not found or ambiguous — the premium " +
                    "gate moved.",
            )
        val mutableCache = mutableClassDefBy(cache)

        val readers = mutableCache.methods.filter { method ->
            method.returnType == "Z" &&
                method.parameterTypes.isEmpty() &&
                method.readsString("DSW3-JJK")
        }
        if (readers.size != 1) {
            throw PatchException(
                "Replaio: expected exactly 1 premium reader (no-arg ()Z reading \"DSW3-JJK\"), " +
                    "found ${readers.size}. Re-derive the gate.",
            )
        }

        // Force the reader true. It's immune to clear()/the false-account write, since those only
        // touch the pref the reader no longer consults.
        readers.single().addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
