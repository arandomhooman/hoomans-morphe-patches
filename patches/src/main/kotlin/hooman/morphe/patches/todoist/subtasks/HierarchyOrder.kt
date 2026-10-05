package hooman.morphe.patches.todoist.subtasks

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/** Transform the submission, not the bound rows: adapter positions/notifications stay consistent. */
internal fun BytecodePatchContext.installHierarchyOrder(
    adapterType: String,
    holder: ClassDef,
    listField: FieldReference,
    itemGetter: MethodReference,
    parent: MethodReference,
    id: MethodReference,
) {
    val base = mutableClassDefBy(listField.definingClass)
    val submit = base.methods.singleOrNull { method ->
        !AccessFlags.STATIC.isSet(method.accessFlags) && method.returnType == "V" &&
            method.parameterTypes.map { it.toString() } == listOf(
                "Ljava/util/List;", "Lcom/todoist/model/Selection;", "Lcom/todoist/model/ViewOption;",
            ) && method.implementation?.instructions?.any {
                ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "adapterItems"
            } == true
    } ?: throw PatchException("Todoist: list submission method was not uniquely resolved.")
    // The holder's checkmark renderer consumes precisely one no-arg Item integer:
    // the priority (Todoist stores P1=4, P2=3, P3=2, P4=1). Resolve that call rather
    // than relying on a renamed getter or a Kotlin delegated-property field index.
    val priority = holder.interfaces.flatMap { type -> classDefBy(type).methods.toList() }
        .filter { method ->
            method.parameterTypes.map { it.toString() } == listOf(itemGetter.returnType, "Z", "Z")
        }.flatMap { method ->
            method.implementation?.instructions?.mapNotNull {
                (it as? ReferenceInstruction)?.reference as? MethodReference
            }?.toList().orEmpty()
        }.filter { it.definingClass == itemGetter.returnType && it.parameterTypes.isEmpty() && it.returnType == "I" }
        .distinctBy { it.toString() }.singleOrNull()
        ?: throw PatchException("Todoist: checkmark priority getter was not uniquely resolved.")
    val owner = base.type
    val orderName = "linkedSubtasksOrder"
    val emitName = "linkedSubtasksEmit"
    if (base.methods.any { it.name == orderName || it.name == emitName }) {
        throw PatchException("Todoist: hierarchy ordering already installed.")
    }
    fun addHelper(name: String, parameters: List<String>, result: String, registers: Int, body: String) {
        base.methods.add(ImmutableMethod(
            owner, name, parameters.map { ImmutableMethodParameter(it, null, null) }, result,
            AccessFlags.PRIVATE.value or AccessFlags.STATIC.value, null, null,
            MutableMethodImplementation(registers),
        ).toMutable().apply { addInstructions(0, body.trimIndent()) })
    }
    val emitRef = "$owner->$emitName(Ljava/lang/Object;Ljava/util/List;Ljava/util/Map;Ljava/util/Set;)V"
    val other = itemGetter.definingClass
    addHelper(orderName, listOf(owner, "Ljava/util/List;"), "Ljava/util/List;", 16, """
        instance-of v0, p0, $adapterType
        if-eqz v0, :unchanged
        new-instance v0, Ljava/util/HashMap;
        invoke-direct {v0}, Ljava/util/HashMap;-><init>()V
        new-instance v1, Ljava/util/HashMap;
        invoke-direct {v1}, Ljava/util/HashMap;-><init>()V
        new-instance v2, Ljava/util/ArrayList;
        invoke-direct {v2}, Ljava/util/ArrayList;-><init>()V
        new-instance v3, Ljava/util/HashSet;
        invoke-direct {v3}, Ljava/util/HashSet;-><init>()V
        invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
        move-result-object v4
        :collect
        invoke-interface {v4}, Ljava/util/Iterator;->hasNext()Z
        move-result v5
        if-eqz v5, :roots_start
        invoke-interface {v4}, Ljava/util/Iterator;->next()Ljava/lang/Object;
        move-result-object v5
        instance-of v6, v5, $other
        if-eqz v6, :collect
        check-cast v5, $other
        invoke-virtual {v5}, $itemGetter
        move-result-object v6
        invoke-interface {v6}, $id
        move-result-object v7
        if-eqz v7, :unchanged
        invoke-virtual {v0, v7, v5}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v8
        # Ambiguous duplicate IDs: preserve all original occurrences and positions.
        if-nez v8, :unchanged
        invoke-interface {v6}, $parent
        move-result-object v7
        if-eqz v7, :collect
        # Bucket direct siblings by descending stored priority; equal priority keeps
        # original order. Parent priorities never enter this comparison.
        invoke-virtual {v6}, $priority
        move-result v8
        neg-int v8, v8
        invoke-static {v8}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
        move-result-object v8
        invoke-virtual {v1, v7}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v9
        if-nez v9, :have_priorities
        new-instance v9, Ljava/util/TreeMap;
        invoke-direct {v9}, Ljava/util/TreeMap;-><init>()V
        invoke-virtual {v1, v7, v9}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        :have_priorities
        check-cast v9, Ljava/util/TreeMap;
        invoke-virtual {v9, v8}, Ljava/util/TreeMap;->get(Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v10
        if-nez v10, :have_bucket
        new-instance v10, Ljava/util/ArrayList;
        invoke-direct {v10}, Ljava/util/ArrayList;-><init>()V
        invoke-virtual {v9, v8, v10}, Ljava/util/TreeMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        :have_bucket
        check-cast v10, Ljava/util/ArrayList;
        invoke-virtual {v10, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
        goto :collect
        :roots_start
        invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
        move-result-object v4
        :roots
        invoke-interface {v4}, Ljava/util/Iterator;->hasNext()Z
        move-result v5
        if-eqz v5, :cycles_start
        invoke-interface {v4}, Ljava/util/Iterator;->next()Ljava/lang/Object;
        move-result-object v5
        instance-of v6, v5, $other
        if-eqz v6, :non_task
        check-cast v5, $other
        invoke-virtual {v5}, $itemGetter
        move-result-object v6
        invoke-interface {v6}, $parent
        move-result-object v6
        invoke-virtual {v0, v6}, Ljava/util/HashMap;->containsKey(Ljava/lang/Object;)Z
        move-result v6
        if-nez v6, :roots
        invoke-static {v5, v2, v1, v3}, $emitRef
        goto :roots
        :non_task
        invoke-virtual {v2, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
        goto :roots
        :cycles_start
        # Cyclic data has no root. Emit remaining nodes once, with a visited guard.
        invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
        move-result-object v4
        :cycles
        invoke-interface {v4}, Ljava/util/Iterator;->hasNext()Z
        move-result v5
        if-eqz v5, :done
        invoke-interface {v4}, Ljava/util/Iterator;->next()Ljava/lang/Object;
        move-result-object v5
        instance-of v6, v5, $other
        if-eqz v6, :cycles
        invoke-static {v5, v2, v1, v3}, $emitRef
        goto :cycles
        :done
        return-object v2
        :unchanged
        return-object p1
    """)
    // Iterative preorder traversal: no recursion limit and no dropping deep descendants.
    addHelper(emitName, listOf("Ljava/lang/Object;", "Ljava/util/List;", "Ljava/util/Map;", "Ljava/util/Set;"), "V", 16, """
        new-instance v0, Ljava/util/ArrayDeque;
        invoke-direct {v0}, Ljava/util/ArrayDeque;-><init>()V
        invoke-virtual {v0, p0}, Ljava/util/ArrayDeque;->push(Ljava/lang/Object;)V
        :next_node
        invoke-virtual {v0}, Ljava/util/ArrayDeque;->isEmpty()Z
        move-result v1
        if-nez v1, :done
        invoke-virtual {v0}, Ljava/util/ArrayDeque;->pop()Ljava/lang/Object;
        move-result-object v1
        check-cast v1, $other
        invoke-virtual {v1}, $itemGetter
        move-result-object v2
        invoke-interface {v2}, $id
        move-result-object v2
        invoke-interface {p3, v2}, Ljava/util/Set;->add(Ljava/lang/Object;)Z
        move-result v3
        if-eqz v3, :next_node
        invoke-interface {p1, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z
        invoke-interface {p2, v2}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v2
        if-eqz v2, :next_node
        check-cast v2, Ljava/util/TreeMap;
        invoke-virtual {v2}, Ljava/util/TreeMap;->descendingMap()Ljava/util/NavigableMap;
        move-result-object v2
        invoke-interface {v2}, Ljava/util/Map;->values()Ljava/util/Collection;
        move-result-object v2
        invoke-interface {v2}, Ljava/util/Collection;->iterator()Ljava/util/Iterator;
        move-result-object v2
        :next_bucket
        invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z
        move-result v3
        if-eqz v3, :next_node
        invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;
        move-result-object v3
        check-cast v3, Ljava/util/List;
        invoke-interface {v3}, Ljava/util/List;->size()I
        move-result v4
        :push_children
        add-int/lit8 v4, v4, -0x1
        if-ltz v4, :next_bucket
        invoke-interface {v3, v4}, Ljava/util/List;->get(I)Ljava/lang/Object;
        move-result-object v5
        invoke-virtual {v0, v5}, Ljava/util/ArrayDeque;->push(Ljava/lang/Object;)V
        goto :push_children
        :done
        return-void
    """)
    // Both the adapter backing list and its notifications consume the transformed input.
    // The guard in the helper keeps other adapter subclasses (e.g. boards) unchanged.
    submit.addInstructions(0, """
        invoke-static/range {p0 .. p1}, $owner->$orderName($owner${"Ljava/util/List;"})Ljava/util/List;
        move-result-object p1
    """.trimIndent())
}
