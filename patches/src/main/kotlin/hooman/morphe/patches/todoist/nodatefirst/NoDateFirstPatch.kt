package hooman.morphe.patches.todoist.nodatefirst

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.smali.ExternalLabel

// Todoist groups tasks in views (Upcoming, Project Date Grouping, Deadline Grouping) using a
// null-aware Comparator wrapper (Kotlin's nullsLast comparator class `gz.b`). When grouping items by
// date in a TreeMap, null keys represent undated tasks ("No date" / "Sans date").
//
// The standard nullsLast comparator returns 1 when the first object is null, causing TreeMap to
// place the "No date" section at the very END of the list.
//
// This patch locates the nullsLast Comparator class (Lgz/b;) and prepends nullsFirst logic to its
// compare() method to return -1 when the first object is null. As a result, TreeMap places the
// undated section at the TOP of the list.
@Suppress("unused")
val noDateFirstPatch = bytecodePatch(
    name = "No date first",
    description = "Moves undated tasks to the top of the task list in grouped views, so " +
        "tasks without a due date appear before dated ones. The reordering is applied on the " +
        "client side when the section list is built; synced data and server behaviour are " +
        "untouched.",
) {
    compatibleWith(
        Compatibility(
            name = "Todoist",
            packageName = "com.todoist",
            // Todoist's brand red.
            appIconColor = 0xE44332,
            targets = listOf(AppTarget("v12190")),
        ),
    )

    execute {
        // Locate the nullsLast Comparator wrapper class (Lgz/b;) or find it by its single
        // Comparator field signature.
        val nullsLastClassDef = classDefByOrNull("Lgz/b;")
            ?: classDefByStrings("Ljava/util/Comparator;")
                .singleOrNull { classDef ->
                    classDef.interfaces.contains("Ljava/util/Comparator;") &&
                        classDef.fields.count() == 1 &&
                        classDef.fields.first().type == "Ljava/util/Comparator;" &&
                        classDef.methods.any { method ->
                            method.name == "compare" &&
                                method.parameterTypes.size == 2 &&
                                method.parameterTypes[0] == "Ljava/lang/Object;" &&
                                method.parameterTypes[1] == "Ljava/lang/Object;"
                        }
                }
            ?: throw PatchException(
                "Todoist: nullsLast Comparator class (Lgz/b;) was not found. " +
                    "The comparator structure changed; re-derive.",
            )

        patchNullsLastComparator(mutableClassDefBy(nullsLastClassDef))
    }
}

// Prepends nullsFirst logic at compare(p1, p2) entry:
//   if p1 == p2 -> return 0
//   if p1 == null (if-eqz p1) -> return -1  (nullsFirst: null comes before non-null)
//   if p2 == null (if-eqz p2) -> return 1   (nullsFirst: non-null comes before null)
//   else -> goto original comparator.compare(p1, p2)
private fun BytecodePatchContext.patchNullsLastComparator(comparatorClass: MutableClass) {
    val compareMethod = comparatorClass.methods.singleOrNull { method ->
        method.name == "compare" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == "Ljava/lang/Object;" &&
            method.parameterTypes[1] == "Ljava/lang/Object;"
    } ?: throw PatchException("Todoist: compare(Object, Object) method not found on nullsLast Comparator.")

    compareMethod.addInstructionsWithLabels(
        0,
        """
            if-eq p1, p2, :equal
            if-eqz p1, :p1_null
            if-eqz p2, :p2_null
            goto :original
            :p1_null
            const/4 v0, -0x1
            return v0
            :p2_null
            const/4 v0, 0x1
            return v0
            :equal
            const/4 v0, 0x0
            return v0
        """,
        ExternalLabel("original", compareMethod.getInstruction(0)),
    )
}
