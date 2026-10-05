package hooman.morphe.patches.todoist.subtasks;

import app.morphe.patcher.*;
import app.morphe.patcher.dex.*;
import app.morphe.patcher.patch.BytecodePatchContext;
import app.morphe.patcher.util.PatchClasses;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.rewriter.*;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Runs against a user-supplied original APK. No APK or account data is committed. */
public final class LinkedSubtasksRegression {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply the original Todoist v12190 APK");
        var apk = new File(args[0]);
        var container = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
        Set<ClassDef> classes = new HashSet<>();
        for (var entry : container.getDexEntryNames()) classes.addAll(container.getEntry(entry).getDexFile().getClasses());
        verify(apk, classes, "original");
        // Rename R8 types, methods and fields, including the TreeCache nested class
        // and RecyclerView nested types. Stable Android/Java APIs remain unchanged.
        Set<String> appTypes = new HashSet<>();
        for (var c : classes) if (c.getType().matches("L[a-z]{1,3}/.*") ||
            c.getType().startsWith("Lcom/todoist/") || c.getType().startsWith("Landroidx/recyclerview/")) appTypes.add(c.getType());
        var rewriter = new DexRewriter(new RewriterModule() {
            public Rewriter<String> getTypeRewriter(Rewriters r) {
                return new TypeRewriter() {
                    protected String rewriteUnwrappedType(String t) {
                        return (t.matches("L[a-z]{1,3}/.*") && !t.startsWith("Lcom/") && !t.startsWith("Lorg/")) || t.startsWith("Landroidx/recyclerview/widget/RecyclerView$") ||
                            t.startsWith("Lcom/todoist/storage/cache/util/TreeCache$")
                            ? "Lrenamed/" + t.substring(1) : t;
                    }
                };
            }
            public Rewriter<MethodReference> getMethodReferenceRewriter(Rewriters r) {
                return ref -> new ImmutableMethodReference(r.getTypeRewriter().rewrite(ref.getDefiningClass()),
                    appTypes.contains(ref.getDefiningClass()) && !ref.getName().startsWith("<") ? "renamed_" + ref.getName() : ref.getName(),
                    ref.getParameterTypes().stream().map(p -> r.getTypeRewriter().rewrite(p.toString())).toList(),
                    r.getTypeRewriter().rewrite(ref.getReturnType()));
            }
            public Rewriter<FieldReference> getFieldReferenceRewriter(Rewriters r) {
                return ref -> new ImmutableFieldReference(r.getTypeRewriter().rewrite(ref.getDefiningClass()),
                    appTypes.contains(ref.getDefiningClass()) ? "renamed_" + ref.getName() : ref.getName(),
                    r.getTypeRewriter().rewrite(ref.getType()));
            }
        });
        Set<ClassDef> renamed = new HashSet<>();
        for (var c : classes) renamed.add(rewriter.getClassDefRewriter().rewrite(c));
        verify(apk, renamed, "renamed");
    }

    static void verify(File apk, Set<ClassDef> classes, String label) throws Exception {
        Path output = Files.createTempDirectory("linked-subtasks-" + label);
        var config = new PatcherConfig(apk, output.toFile(), null, null, false, Collections.emptySet(), BytecodeMode.FULL, NoOpDexVerifier.INSTANCE);
        var context = new BytecodePatchContext(config, new PackageMetadata("com.todoist", "v12190", "12190"));
        context.setPatchClasses$morphe_patcher(new PatchClasses(classes));
        context.setOpcodes$morphe_patcher(Opcodes.getDefault());
        LinkedSubtasksPatchKt.getLinkedSubtasksPatch().execute(context);
        var adapter = context.classDefBy(c -> {
            for (var m : c.getMethods()) if (m.getName().equals("linkedSubtasksStyle")) return true;
            return false;
        });
        Method helper = null, original = null, wrapper = null;
        for (var m : adapter.getMethods()) {
            if (m.getName().equals("linkedSubtasksStyle")) helper = m;
            if (m.getName().equals("linkedSubtasksOriginalBind")) original = m;
            for (var i : instructions(m)) if (i instanceof ReferenceInstruction ri && ri.getReference() instanceof MethodReference ref &&
                ref.getName().equals("linkedSubtasksOriginalBind")) wrapper = m;
        }
        check(helper != null && original != null && wrapper != null, "wrapper and helpers");
        check(instructions(wrapper).size() == 3, "original renderer must run before styling");
        check(helper.getImplementation().getRegisterCount() == 13, "isolated helper registers");
        // Serialize and re-read the patched DEX, exercising smali assembly, branch offsets,
        // method references, register formats, and the original method's debug information.
        DexPool pool = new DexPool(Opcodes.getDefault());
        pool.internClass(adapter);
        var base = context.classDefBy(c -> {
            for (var m : c.getMethods()) if (m.getName().equals("linkedSubtasksOrder")) return true;
            return false;
        });
        pool.internClass(base);
        Method order = null, emit = null;
        boolean submissionHooked = false;
        for (var m : base.getMethods()) {
            if (m.getName().equals("linkedSubtasksOrder")) order = m;
            if (m.getName().equals("linkedSubtasksEmit")) emit = m;
            var code = instructions(m);
            if (!code.isEmpty() && code.get(0) instanceof ReferenceInstruction ri &&
                ri.getReference() instanceof MethodReference ref && ref.getName().equals("linkedSubtasksOrder")) {
                check(code.get(1).getOpcode() == Opcode.MOVE_RESULT_OBJECT, "submission uses transformed list");
                submissionHooked = true;
            }
        }
        check(submissionHooked, "reorder before adapter submission");
        new OrderingVm(order, emit, helper).verify();
        check(instructions(helper).stream().anyMatch(i -> i instanceof ReferenceInstruction ri &&
            ri.getReference() instanceof TypeReference t && t.getType().equals("Landroid/graphics/drawable/shapes/PathShape;")),
            "arrow head shape installed");
        Path dex = output.resolve("adapter.dex");
        pool.writeTo(new com.android.tools.smali.dexlib2.writer.io.FileDataStore(dex.toFile()));
        DexFileFactory.loadDexFile(dex.toFile(), Opcodes.getDefault());
        var rows = new ArrayList<Node>(List.of(new Node("p", null), new Node("c", "p"), new Node("g", "c")));
        depth(helper, rows, 0, 0);
        depth(helper, rows, 1, 1);
        depth(helper, rows, 2, 2);
        depth(helper, List.of(new Node("filtered", "absent")), 0, 1);
        rows.set(1, new Node("c", null)); // Reparent without changing list size.
        depth(helper, rows, 1, 0);
        depth(helper, rows, 2, 1);
        var deep = new ArrayList<Node>();
        for (int n = 0; n < 10; n++) deep.add(new Node("n" + n, n == 0 ? null : "n" + (n - 1)));
        depth(helper, deep, 9, 6);
        depth(helper, List.of(new Node("self", "self")), 0, 6);
        System.out.println("PASS " + label + ": patch application, DEX round trip, wrapper, depth, hierarchy/priority/stability/filter/cycle/1000-level cases; " + dex);
    }

    record Node(String id, String parent) {}
    static List<Instruction> instructions(Method m) {
        List<Instruction> out = new ArrayList<>();
        if (m.getImplementation() != null) m.getImplementation().getInstructions().forEach(out::add);
        return out;
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    // Execute the actual injected DEX up to padding application with detached holder
    // and in-memory tasks. This is a limited interpreter, not a second depth algorithm.
    // Android drawing remains a device-level manual check.
    static void depth(Method helper, List<Node> rows, int position, int expected) {
        var code = instructions(helper);
        Map<Integer, Integer> offsets = new HashMap<>();
        int address = 0;
        for (int i = 0; i < code.size(); i++) { offsets.put(address, i); address += code.get(i).getCodeUnits(); }
        Object[] v = new Object[13];
        v[10] = rows; v[11] = new Object(); v[12] = position;
        Object result = null;
        address = 0;
        for (int steps = 0; steps < 10000; steps++) {
            var insn = code.get(offsets.get(address));
            var op = insn.getOpcode();
            int a = insn instanceof OneRegisterInstruction x ? x.getRegisterA() : -1;
            int b = insn instanceof TwoRegisterInstruction x ? x.getRegisterB() : -1;
            boolean branch = false;
            switch (op) {
                case MOVE_OBJECT, MOVE -> v[a] = v[b];
                case INSTANCE_OF -> v[a] = 1;
                case CHECK_CAST -> {}
                case IGET_OBJECT -> {
                    // The first field read is the adapter list; the second starts rendering.
                    if (v[b] == rows) v[a] = rows;
                    else {
                        check(Objects.equals(v[1], expected), "depth at " + position + ": " + v[1] + " != " + expected);
                        return;
                    }
                }
                case CONST_4, CONST_16, CONST -> v[a] = ((NarrowLiteralInstruction) insn).getNarrowLiteral();
                case MOVE_RESULT, MOVE_RESULT_OBJECT -> v[a] = result;
                case NEW_INSTANCE -> v[a] = new HashMap<>();
                case IF_EQZ -> branch = v[a] == null || Objects.equals(v[a], 0);
                case IF_GE -> branch = (int) v[a] >= (int) v[b];
                case GOTO, GOTO_16, GOTO_32 -> branch = true;
                case ADD_INT_LIT8 -> v[a] = (int) v[b] + ((NarrowLiteralInstruction) insn).getNarrowLiteral();
                case INVOKE_DIRECT, INVOKE_INTERFACE, INVOKE_VIRTUAL -> {
                    var call = (FiveRegisterInstruction) insn;
                    var ref = (MethodReference) ((ReferenceInstruction) insn).getReference();
                    Object target = v[call.getRegisterC()];
                    if (ref.getName().equals("<init>")) {}
                    else if (target instanceof List<?> list) result = ref.getName().equals("get") ? list.get((int) v[call.getRegisterD()]) : list.iterator();
                    else if (target instanceof Iterator<?> it) result = ref.getName().equals("hasNext") ? (it.hasNext() ? 1 : 0) : it.next();
                    else if (target instanceof Map map) {
                        if (ref.getName().equals("put")) result = map.put(v[call.getRegisterD()], v[call.getRegisterE()]);
                        else result = map.get(v[call.getRegisterD()]);
                    } else if (target instanceof Node node) {
                        if (op == Opcode.INVOKE_VIRTUAL) result = node; // Other's item accessor.
                        else {
                            // The first interface accessor on a task is the parent getter.
                            String parentName = null;
                            for (var candidate : code) if (candidate.getOpcode() == Opcode.INVOKE_INTERFACE &&
                                ((ReferenceInstruction) candidate).getReference() instanceof MethodReference r &&
                                r.getReturnType().equals("Ljava/lang/String;")) { parentName = r.getName(); break; }
                            result = ref.getName().equals(parentName) ? node.parent() : node.id();
                        }
                    } else throw new AssertionError("Unexpected call " + ref);
                }
                default -> throw new AssertionError("Unexpected depth opcode " + op);
            }
            address += branch ? ((OffsetInstruction) insn).getCodeOffset() : insn.getCodeUnits();
        }
        throw new AssertionError("Depth computation did not terminate");
    }
}
