package hooman.morphe.patches.todoist.subtasks

import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

// Discover the row binder from its payload strings, its holder from the type guard,
// and the tree accessors from the root-bucket construction. No R8 class/member names.
// Wrap the binder so full and partial binds finish before our style is applied. A
// separate method owns its registers: there are no assumptions about R8's liveness.
@Suppress("unused")
val linkedSubtasksPatch = bytecodePatch(
    name = "Linked subtasks",
    description = "Distinguishes subtasks in task lists with 28dp indentation, a gray guide " +
        "and red arrow, and 95% text size. Keeps parents in their original order and " +
        "groups descendants beneath them, sorting siblings by priority. Changes display only.",
) {
    compatibleWith(
        Compatibility(
            name = "Todoist",
            packageName = "com.todoist",
            appIconColor = 0xE44332,
            targets = listOf(AppTarget("v12190")),
        ),
    )
    execute {
        val adapterDef = classDefByStrings("grouped_background", StringComparisonType.EQUALS)
            .filter { def -> def.methods.any { it.isTaskBinder() } }.unique("task adapter")
        val binder = adapterDef.methods.filter { it.isTaskBinder() }.unique("task binder")
        val holderDef = binder.implementation!!.instructions.mapNotNull { insn ->
            if (insn.opcode != Opcode.INSTANCE_OF) return@mapNotNull null
            val type = ((insn as? ReferenceInstruction)?.reference as? TypeReference)?.type
            type?.let { classDefByOrNull(it) }
        }.filter { def ->
            def.fields.any { it.type == "Lcom/todoist/ui/widget/PriorityCheckmark;" } &&
                def.fields.any { it.type == "Landroid/view/ViewGroup;" }
        }.distinctBy { it.type }.unique("task holder")
        val views = holderViewFields(holderDef)
        val indentSetter = holderDef.methods.filter { method ->
            method.returnType == "V" && method.parameterTypes.size == 3 &&
                method.parameterTypes.take(2).map { it.toString() } == listOf("I", "Z") &&
                method.parameterTypes[2].startsWith("L")
        }.unique("holder indentation setter")
        val indentType = indentSetter.parameterTypes[2].toString()
        val indentDef = classDefBy(indentType)
        val applyIndent = indentDef.methods.filter { method ->
            method.parameterTypes.map { it.toString() } == listOf("I", "Landroid/view/View;") &&
                method.calls().any { it.name == "setPaddingRelative" }
        }.unique("indentation primitive")
        requireMatch(indentDef.methods.any {
            it.name == "<init>" && it.parameterTypes.map { p -> p.toString() } == listOf("I")
        }, "indentation constructor")
        val paddingKey = applyIndent.implementation!!.instructions.mapNotNull {
            if (it.opcode == Opcode.SGET) (it as? ReferenceInstruction)?.reference as? FieldReference else null
        }.filter { it.type == "I" }.distinct().unique("original padding tag")
        val otherType = "Lcom/todoist/model/ItemListAdapterItem${'$'}Item${'$'}Other;"
        val otherDef = classDefBy(otherType)
        val itemGetter = otherDef.methods.filter {
            it.parameterTypes.isEmpty() && it.returnType == "Lcom/todoist/model/Item;"
        }.unique("adapter item accessor")
        val listField = binder.implementation!!.instructions.mapNotNull {
            if (it.opcode == Opcode.IGET_OBJECT) (it as? ReferenceInstruction)?.reference as? FieldReference else null
        }.filter { it.type == "Ljava/util/ArrayList;" || it.type == "Ljava/util/List;" }
            .distinctBy { it.toString() }.unique("bound item list")
        val (parent, id) = treeGetters()
        requireMatch(parent.name != id.name, "distinct parent and identity getters")
        installHierarchyOrder(adapterDef.type, holderDef, listField, itemGetter, parent, id)
        val adapter = mutableClassDefBy(adapterDef)
        val holderType = holderDef.type
        val helperName = "linkedSubtasksStyle"
        val originalName = "linkedSubtasksOriginalBind"
        requireMatch(adapter.methods.none { it.name == helperName || it.name == originalName }, "unpatched binder")
        val helper = ImmutableMethod(
            adapter.type, helperName,
            listOf(
                ImmutableMethodParameter(adapter.type, null, "adapter"),
                ImmutableMethodParameter(binder.parameterTypes[0].toString(), null, "holder"),
                ImmutableMethodParameter("I", null, "position"),
            ),
            "V", AccessFlags.PRIVATE.value or AccessFlags.STATIC.value, null, null,
            MutableMethodImplementation(13),
        ).toMutable().apply {
            addInstructions(0, styleInstructions(
                holderType, views, listField, otherType, itemGetter, parent, id,
                indentType, applyIndent.name, paddingKey,
            ))
        }
        // Keep every original instruction/branch/try block intact in a private method.
        // The wrapper retains the override signature and annotations.
        val original = ImmutableMethod(
            adapter.type, originalName, binder.parameters, binder.returnType,
            AccessFlags.PRIVATE.value, null, binder.hiddenApiRestrictions, binder.implementation,
        ).toMutable()
        val wrapper = ImmutableMethod(
            adapter.type, binder.name, binder.parameters, binder.returnType,
            binder.accessFlags, binder.annotations, binder.hiddenApiRestrictions,
            MutableMethodImplementation(4),
        ).toMutable().apply {
            val params = binder.parameterTypes.joinToString("")
            addInstructions(0, """
                invoke-direct/range {p0 .. p3}, ${adapter.type}->$originalName($params)V
                invoke-static/range {p0 .. p2}, ${adapter.type}->$helperName(${adapter.type}${binder.parameterTypes[0]}I)V
                return-void
            """.trimIndent())
        }
        adapter.methods.removeAll { it.name == binder.name && it.parameterTypes == binder.parameterTypes }
        adapter.methods.addAll(listOf(original, helper, wrapper))
    }
}

private fun <T> Iterable<T>.unique(what: String): T = singleOrNull()
    ?: throw PatchException("Todoist: could not uniquely resolve $what; APK structure is unsupported.")

private fun requireMatch(condition: Boolean, what: String) {
    if (!condition) throw PatchException("Todoist: could not resolve $what; APK structure is unsupported.")
}

private fun Method.strings(): Set<String> = implementation?.instructions?.mapNotNull {
    ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
}?.toSet().orEmpty()

private fun Method.calls(): List<MethodReference> = implementation?.instructions?.mapNotNull {
    ((it as? ReferenceInstruction)?.reference as? MethodReference)
}?.toList().orEmpty()

private fun Method.isTaskBinder(): Boolean = returnType == "V" &&
    !AccessFlags.STATIC.isSet(accessFlags) && parameterTypes.size == 3 &&
    parameterTypes[1].toString() == "I" && parameterTypes[2].toString() == "Ljava/util/List;" &&
    strings().containsAll(listOf("payloads", "indent", "grouped_background", "sound_effect"))

private fun BytecodePatchContext.treeGetters(): Pair<MethodReference, MethodReference> {
    // The first map groups nodes by nullable parent, using the root sentinel for null.
    // R8 can hoist that string before the getter, so use the grouping boundary rather
    // than a fixed forward/backward distance from the string instruction.
    val treeInit = classDefByStrings("-9223372036854775808", StringComparisonType.EQUALS)
        .flatMap { it.methods.toList() }.filter {
            it.name == "<init>" && it.strings().containsAll(listOf("treeNodes", "comparator", "-9223372036854775808"))
        }.unique("tree grouping constructor")
    val instructions = treeInit.implementation!!.instructions.toList()
    val groupingEnd = instructions.indexOfFirst {
        val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
        ref?.definingClass == "Ljava/util/Collections;" && ref.name == "unmodifiableMap"
    }
    requireMatch(groupingEnd > 0, "tree grouping boundary")
    fun stringGetter(insn: com.android.tools.smali.dexlib2.iface.instruction.Instruction): MethodReference? {
        if (insn.opcode != Opcode.INVOKE_INTERFACE) return null
        return ((insn as? ReferenceInstruction)?.reference as? MethodReference)?.takeIf {
            it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;"
        }
    }
    val parent = instructions.take(groupingEnd).mapNotNull { stringGetter(it) }
        .distinctBy { it.toString() }.unique("tree parent getter")
    val id = instructions.mapNotNull { stringGetter(it) }
        .filter { it.toString() != parent.toString() }.distinctBy { it.toString() }
        .unique("tree identity getter")
    return parent to id
}

private data class HolderViews(
    val container: String,
    val containerType: String,
    val title: String,
    val titleType: String,
    val desc: String,
    val descType: String,
)

private fun BytecodePatchContext.holderViewFields(holderDef: ClassDef): HolderViews {
    val constructor = holderDef.methods.singleOrNull { it.name == "<init>" }
        ?: throw PatchException("Todoist: task holder constructor not found. Re-derive.")
    val instructions = constructor.implementation?.instructions?.toList()
        ?: throw PatchException("Todoist: task holder constructor has no implementation. Re-derive.")

    data class Binding(val field: String, val type: String)
    val bindings = mutableListOf<Binding>()
    val h0aName = holderDef.type
    instructions.forEachIndexed { index, instruction ->
        val methodRef = ((instruction as? ReferenceInstruction)?.reference as? MethodReference)
        if (instruction.opcode != Opcode.INVOKE_VIRTUAL || methodRef?.name != "findViewById") {
            return@forEachIndexed
        }
        for (fwd in 1..8) {
            val next = instructions.getOrNull(index + fwd) ?: break
            val fieldRef = ((next as? ReferenceInstruction)?.reference as? FieldReference)
            val isStore = next.opcode == Opcode.IPUT_OBJECT || next.opcode == Opcode.IPUT
            if (isStore && fieldRef?.definingClass == h0aName) {
                bindings.add(Binding(fieldRef.name, fieldRef.type.toString()))
                break
            }
            if (next.opcode == Opcode.INVOKE_VIRTUAL || next.opcode == Opcode.INVOKE_DIRECT) {
                break
            }
        }
    }
    if (bindings.size < 6) {
        throw PatchException(
            "Todoist: expected the task holder constructor to bind 6+ views, found " +
                "${bindings.size}. The row layout changed; re-derive.",
        )
    }
    val container = bindings.singleOrNull { it.type == "Landroid/view/ViewGroup;" }
        ?: throw PatchException(
            "Todoist: task holder container (ViewGroup target) not found uniquely. Re-derive.",
        )
    fun isTextView(type: String): Boolean {
        var current: String? = type
        val visited = mutableSetOf<String>()
        while (current != null && visited.add(current)) {
            if (current == "Landroid/widget/TextView;") return true
            current = classDefByOrNull(current)?.superclass
        }
        return false
    }
    val textTargets = bindings.filter { isTextView(it.type) }
    if (textTargets.size < 2 || textTargets[0].type != textTargets[1].type) {
        throw PatchException(
            "Todoist: task holder title/description pair not found (first two same-typed " +
                "TextView targets). The row layout changed; re-derive.",
        )
    }
    return HolderViews(
        container.field,
        container.type,
        textTargets[0].field,
        textTargets[0].type,
        textTargets[1].field,
        textTargets[1].type,
    )
}

private fun styleInstructions(
    holderType: String,
    views: HolderViews,
    listField: FieldReference,
    otherType: String,
    itemGetter: MethodReference,
    parent: MethodReference,
    id: MethodReference,
    indentType: String,
    applyName: String,
    paddingKey: FieldReference,
): String = """
        move-object v0, p0
        move-object v4, p1
        instance-of v1, v4, $holderType
        if-eqz v1, :original
        check-cast v4, $holderType
        iget-object v1, v0, $listField
        move v2, p2
        invoke-interface {v1, v2}, Ljava/util/List;->get(I)Ljava/lang/Object;
        move-result-object v5
        instance-of v2, v5, $otherType
        if-eqz v2, :original
        check-cast v5, $otherType
        invoke-virtual {v5}, $itemGetter
        move-result-object v5
        invoke-interface {v5}, $parent
        move-result-object v2
        const/4 v3, 0x0
        if-eqz v2, :depth_done
        # Always count the direct parent, even when it is outside the current filter.
        const/4 v3, 0x1
        # Rebuild from current items, including for same-size edits/reparenting. No
        # RecyclerView parent is needed: initial binds normally happen detached.
        invoke-interface {v1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
        move-result-object v6
        new-instance v1, Ljava/util/HashMap;
        invoke-direct {v1}, Ljava/util/HashMap;-><init>()V
        :map_loop
        invoke-interface {v6}, Ljava/util/Iterator;->hasNext()Z
        move-result v7
        if-eqz v7, :depth_loop
        invoke-interface {v6}, Ljava/util/Iterator;->next()Ljava/lang/Object;
        move-result-object v7
        instance-of v8, v7, $otherType
        if-eqz v8, :map_loop
        check-cast v7, $otherType
        invoke-virtual {v7}, $itemGetter
        move-result-object v7
        invoke-interface {v7}, $id
        move-result-object v8
        invoke-interface {v7}, $parent
        move-result-object v7
        invoke-virtual {v1, v8, v7}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        goto :map_loop
        :depth_loop
        const/4 v7, 0x6
        if-ge v3, v7, :depth_done
        invoke-virtual {v1, v2}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v2
        if-eqz v2, :depth_done
        add-int/lit8 v3, v3, 0x1
        goto :depth_loop
        :depth_done
        move v1, v3
        # Use an independent 28dp delegate; do not widen project/drag indentation globally.
        iget-object v2, v4, $holderType->${views.container}:${views.containerType}
        invoke-virtual {v2}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
        move-result-object v6
        invoke-virtual {v6}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;
        move-result-object v6
        iget v6, v6, Landroid/util/DisplayMetrics;->density:F
        const v7, 0x41e00000
        mul-float v6, v6, v7
        float-to-int v6, v6
        new-instance v7, $indentType
        invoke-direct {v7, v6}, $indentType-><init>(I)V
        invoke-virtual {v7, v1, v2}, $indentType->$applyName(ILandroid/view/View;)V
        if-eqz v1, :sub_zero
        iget-object v2, v4, $holderType->${views.container}:${views.containerType}
        invoke-virtual {v2}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
        move-result-object v3
        invoke-virtual {v3}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;
        move-result-object v6
        iget v6, v6, Landroid/util/DisplayMetrics;->density:F
        const v7, 0x41e00000
        mul-float v6, v6, v7
        float-to-int v6, v6
        sget v3, ${paddingKey.definingClass}->${paddingKey.name}:I
        invoke-virtual {v2, v3}, Landroid/view/View;->getTag(I)Ljava/lang/Object;
        move-result-object v3
        if-eqz v3, :sub_notag
        check-cast v3, Ljava/lang/Integer;
        invoke-virtual {v3}, Ljava/lang/Integer;->intValue()I
        move-result v3
        goto :sub_havepad
        :sub_notag
        invoke-virtual {v2}, Landroid/view/View;->getPaddingStart()I
        move-result v3
        :sub_havepad
        add-int/lit8 v7, v1, -0x1
        mul-int v7, v7, v6
        add-int v3, v3, v7
        invoke-virtual {v2}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
        move-result-object v6
        invoke-virtual {v6}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;
        move-result-object v6
        iget v6, v6, Landroid/util/DisplayMetrics;->density:F
        const v7, 0x41000000
        mul-float v7, v7, v6
        float-to-int v7, v7
        add-int v3, v3, v7
        invoke-virtual {v2}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
        move-result-object v6
        invoke-virtual {v6}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;
        move-result-object v6
        iget v6, v6, Landroid/util/DisplayMetrics;->density:F
        const v7, 0x40000000
        mul-float v7, v7, v6
        float-to-int v7, v7
        mul-int/lit8 v6, v7, 0x5
        new-instance v1, Landroid/graphics/drawable/GradientDrawable;
        invoke-direct {v1}, Landroid/graphics/drawable/GradientDrawable;-><init>()V
        invoke-virtual {v1, v6, v7}, Landroid/graphics/drawable/GradientDrawable;->setSize(II)V
        const v6, 0xffdb4c3f
        invoke-virtual {v1, v6}, Landroid/graphics/drawable/GradientDrawable;->setColor(I)V
        new-instance v6, Landroid/graphics/drawable/GradientDrawable;
        invoke-direct {v6}, Landroid/graphics/drawable/GradientDrawable;-><init>()V
        invoke-virtual {v6, v7, v7}, Landroid/graphics/drawable/GradientDrawable;->setSize(II)V
        const v7, 0x8c8c8c8c
        invoke-virtual {v6, v7}, Landroid/graphics/drawable/GradientDrawable;->setColor(I)V
        # Arrow head: logical 4x8 triangle, scaled with the same density as the shaft.
        new-instance v8, Landroid/graphics/Path;
        invoke-direct {v8}, Landroid/graphics/Path;-><init>()V
        iget-object v0, v4, $holderType->${views.container}:${views.containerType}
        invoke-virtual {v0}, Landroid/view/View;->getLayoutDirection()I
        move-result v0
        const/4 v9, 0x0
        const v5, 0x40800000
        if-eqz v0, :arrow_ltr
        invoke-virtual {v8, v5, v9}, Landroid/graphics/Path;->moveTo(FF)V
        invoke-virtual {v8, v9, v5}, Landroid/graphics/Path;->lineTo(FF)V
        const v9, 0x41000000
        invoke-virtual {v8, v5, v9}, Landroid/graphics/Path;->lineTo(FF)V
        goto :arrow_shape
        :arrow_ltr
        invoke-virtual {v8, v9, v9}, Landroid/graphics/Path;->moveTo(FF)V
        invoke-virtual {v8, v5, v5}, Landroid/graphics/Path;->lineTo(FF)V
        const v5, 0x41000000
        invoke-virtual {v8, v9, v5}, Landroid/graphics/Path;->lineTo(FF)V
        :arrow_shape
        invoke-virtual {v8}, Landroid/graphics/Path;->close()V
        new-instance v0, Landroid/graphics/drawable/shapes/PathShape;
        const v5, 0x40800000
        const v9, 0x41000000
        invoke-direct {v0, v8, v5, v9}, Landroid/graphics/drawable/shapes/PathShape;-><init>(Landroid/graphics/Path;FF)V
        new-instance v8, Landroid/graphics/drawable/ShapeDrawable;
        invoke-direct {v8, v0}, Landroid/graphics/drawable/ShapeDrawable;-><init>(Landroid/graphics/drawable/shapes/Shape;)V
        mul-int/lit8 v0, v7, 0x2
        invoke-virtual {v8, v0}, Landroid/graphics/drawable/ShapeDrawable;->setIntrinsicWidth(I)V
        mul-int/lit8 v0, v7, 0x4
        invoke-virtual {v8, v0}, Landroid/graphics/drawable/ShapeDrawable;->setIntrinsicHeight(I)V
        add-int v5, v3, v0
        invoke-virtual {v8}, Landroid/graphics/drawable/ShapeDrawable;->getPaint()Landroid/graphics/Paint;
        move-result-object v0
        const v9, 0xffdb4c3f
        invoke-virtual {v0, v9}, Landroid/graphics/Paint;->setColor(I)V
        const/4 v9, 0x1
        invoke-virtual {v0, v9}, Landroid/graphics/Paint;->setAntiAlias(Z)V
        const/4 v2, 0x3
        new-array v2, v2, [Landroid/graphics/drawable/Drawable;
        const/4 v7, 0x0
        aput-object v6, v2, v7
        const/4 v7, 0x1
        aput-object v1, v2, v7
        const/4 v7, 0x2
        aput-object v8, v2, v7
        new-instance v6, Landroid/graphics/drawable/LayerDrawable;
        invoke-direct {v6, v2}, Landroid/graphics/drawable/LayerDrawable;-><init>([Landroid/graphics/drawable/Drawable;)V
        const/4 v1, 0x0
        const v7, 0x800073
        invoke-virtual {v6, v1, v7}, Landroid/graphics/drawable/LayerDrawable;->setLayerGravity(II)V
        invoke-virtual {v6, v1, v3}, Landroid/graphics/drawable/LayerDrawable;->setLayerInsetStart(II)V
        const/4 v1, 0x1
        const v7, 0x800013
        invoke-virtual {v6, v1, v7}, Landroid/graphics/drawable/LayerDrawable;->setLayerGravity(II)V
        invoke-virtual {v6, v1, v3}, Landroid/graphics/drawable/LayerDrawable;->setLayerInsetStart(II)V
        const/4 v1, 0x2
        invoke-virtual {v6, v1, v7}, Landroid/graphics/drawable/LayerDrawable;->setLayerGravity(II)V
        invoke-virtual {v6, v1, v5}, Landroid/graphics/drawable/LayerDrawable;->setLayerInsetStart(II)V
        iget-object v1, v4, $holderType->${views.container}:${views.containerType}
        const v2, 0x7e711004
        invoke-virtual {v1, v2}, Landroid/view/View;->getTag(I)Ljava/lang/Object;
        move-result-object v3
        if-nez v3, :foreground_saved
        invoke-virtual {v1}, Landroid/view/View;->getForeground()Landroid/graphics/drawable/Drawable;
        move-result-object v3
        const v7, 0x7e711003
        invoke-virtual {v1, v7, v3}, Landroid/view/View;->setTag(ILjava/lang/Object;)V
        sget-object v3, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
        invoke-virtual {v1, v2, v3}, Landroid/view/View;->setTag(ILjava/lang/Object;)V
        :foreground_saved
        invoke-virtual {v1, v6}, Landroid/view/View;->setForeground(Landroid/graphics/drawable/Drawable;)V
        iget-object v1, v4, $holderType->${views.title}:${views.titleType}
        const v2, 0x7e711001
        invoke-virtual {v1, v2}, Landroid/view/View;->getTag(I)Ljava/lang/Object;
        move-result-object v3
        if-nez v3, :sub_title_cached
        invoke-virtual {v1}, Landroid/widget/TextView;->getTextSize()F
        move-result v3
        invoke-static {v3}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
        move-result-object v3
        invoke-virtual {v1, v2, v3}, Landroid/view/View;->setTag(ILjava/lang/Object;)V
        :sub_title_cached
        check-cast v3, Ljava/lang/Float;
        invoke-virtual {v3}, Ljava/lang/Float;->floatValue()F
        move-result v3
        const v6, 0x3f733333
        mul-float v3, v3, v6
        const/4 v6, 0x0
        invoke-virtual {v1, v6, v3}, Landroid/widget/TextView;->setTextSize(IF)V
        iget-object v1, v4, $holderType->${views.desc}:${views.descType}
        const v2, 0x7e711002
        invoke-virtual {v1, v2}, Landroid/view/View;->getTag(I)Ljava/lang/Object;
        move-result-object v3
        if-nez v3, :sub_desc_cached
        invoke-virtual {v1}, Landroid/widget/TextView;->getTextSize()F
        move-result v3
        invoke-static {v3}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
        move-result-object v3
        invoke-virtual {v1, v2, v3}, Landroid/view/View;->setTag(ILjava/lang/Object;)V
        :sub_desc_cached
        check-cast v3, Ljava/lang/Float;
        invoke-virtual {v3}, Ljava/lang/Float;->floatValue()F
        move-result v3
        const v6, 0x3f733333
        mul-float v3, v3, v6
        const/4 v6, 0x0
        invoke-virtual {v1, v6, v3}, Landroid/widget/TextView;->setTextSize(IF)V
        goto :original
        :sub_zero
        iget-object v1, v4, $holderType->${views.container}:${views.containerType}
        const v2, 0x7e711004
        invoke-virtual {v1, v2}, Landroid/view/View;->getTag(I)Ljava/lang/Object;
        move-result-object v3
        if-eqz v3, :foreground_restored
        const v2, 0x7e711003
        invoke-virtual {v1, v2}, Landroid/view/View;->getTag(I)Ljava/lang/Object;
        move-result-object v3
        check-cast v3, Landroid/graphics/drawable/Drawable;
        invoke-virtual {v1, v3}, Landroid/view/View;->setForeground(Landroid/graphics/drawable/Drawable;)V
        const/4 v3, 0x0
        invoke-virtual {v1, v2, v3}, Landroid/view/View;->setTag(ILjava/lang/Object;)V
        const v2, 0x7e711004
        invoke-virtual {v1, v2, v3}, Landroid/view/View;->setTag(ILjava/lang/Object;)V
        :foreground_restored
        iget-object v1, v4, $holderType->${views.title}:${views.titleType}
        const v2, 0x7e711001
        invoke-virtual {v1, v2}, Landroid/view/View;->getTag(I)Ljava/lang/Object;
        move-result-object v3
        if-eqz v3, :sub_zero_desc
        check-cast v3, Ljava/lang/Float;
        invoke-virtual {v3}, Ljava/lang/Float;->floatValue()F
        move-result v3
        const/4 v6, 0x0
        invoke-virtual {v1, v6, v3}, Landroid/widget/TextView;->setTextSize(IF)V
        invoke-virtual {v1, v2, v6}, Landroid/view/View;->setTag(ILjava/lang/Object;)V
        :sub_zero_desc
        iget-object v1, v4, $holderType->${views.desc}:${views.descType}
        const v2, 0x7e711002
        invoke-virtual {v1, v2}, Landroid/view/View;->getTag(I)Ljava/lang/Object;
        move-result-object v3
        if-eqz v3, :original
        check-cast v3, Ljava/lang/Float;
        invoke-virtual {v3}, Ljava/lang/Float;->floatValue()F
        move-result v3
        const/4 v6, 0x0
        invoke-virtual {v1, v6, v3}, Landroid/widget/TextView;->setTextSize(IF)V
        invoke-virtual {v1, v2, v6}, Landroid/view/View;->setTag(ILjava/lang/Object;)V
        :original
        return-void
    """.trimIndent()
