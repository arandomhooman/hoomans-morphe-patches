package hooman.morphe.patches.todoist.subtasks;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import java.util.*;
import static hooman.morphe.patches.todoist.subtasks.LinkedSubtasksRegression.*;

/** Executes the generated ordering DEX, not a Java reimplementation of its algorithm. */
@SuppressWarnings({"rawtypes", "unchecked"})
final class OrderingVm {
    record Row(String id, String parent, int priority) {}
    static final Object TASK_ADAPTER = new Object();
    final Method order, emit;
    final String parentGetter, idGetter, otherType;
    OrderingVm(Method order, Method emit, Method style) {
        this.order = order;
        this.emit = emit;
        var getters = instructions(style).stream()
            .filter(i -> i.getOpcode() == Opcode.INVOKE_INTERFACE)
            .map(i -> (MethodReference) ((ReferenceInstruction) i).getReference())
            .filter(r -> r.getReturnType().equals("Ljava/lang/String;")).toList();
        parentGetter = getters.get(0).getName();
        idGetter = getters.stream().filter(r -> !r.getName().equals(parentGetter)).findFirst().orElseThrow().getName();
        otherType = instructions(order).stream().filter(i -> i.getOpcode() == Opcode.CHECK_CAST)
            .map(i -> ((TypeReference) ((ReferenceInstruction) i).getReference()).getType())
            .filter(t -> t.contains("ItemListAdapterItem")).findFirst().orElseThrow();
    }
    void verify() {
        var p = new Row("parentP2", null, 3);
        var top = new Row("parentP1", null, 4);
        var c1 = new Row("childP1", p.id(), 4);
        var c3 = new Row("childP3", p.id(), 2);
        var c3b = new Row("childP3b", p.id(), 2);
        var grand = new Row("grandP1", c3.id(), 4);
        var orphan = new Row("orphan", "filteredParent", 4);
        assertOrder(List.of(c1, top, grand, p, c3, c3b), List.of(top, p, c1, c3, grand, c3b));
        // Parent order remains as supplied even when it is not priority order.
        assertOrder(List.of(c1, p, top, c3), List.of(p, c1, c3, top));
        assertOrder(List.of(orphan, top, c1, p), List.of(orphan, top, p, c1));
        // Children move to the parent's section; headers/controls are retained.
        assertOrder(List.of("P1 header", c1, top, "P2 header", p, "add"),
            List.of("P1 header", top, "P2 header", p, c1, "add"));
        var changed = new Row(c1.id(), top.id(), 4);
        assertOrder(List.of(changed, p, top, c3), List.of(p, c3, top, changed));
        var cycleA = new Row("a", "b", 4);
        var cycleB = new Row("b", "a", 2);
        assertOrder(List.of(cycleA, cycleB), List.of(cycleA, cycleB));
        assertOrder(List.of(new Row("self", "self", 4)), List.of(new Row("self", "self", 4)));
        var duplicate = List.of(p, p, c1);
        check(run(order, TASK_ADAPTER, duplicate) == duplicate, "duplicate IDs preserve original list");
        var board = List.of(c1, p);
        check(run(order, new Object(), board) == board, "other adapter types remain untouched");
        assertOrder(List.of(), List.of());
        assertOrder(List.of("header", "placeholder", "add"), List.of("header", "placeholder", "add"));
        // No recursion or visual-depth cap in the actual hierarchy traversal.
        var deep = new ArrayList<Row>();
        for (int n = 0; n < 1000; n++) deep.add(new Row("n" + n, n == 0 ? null : "n" + (n - 1), n % 4 + 1));
        var reversed = new ArrayList<>(deep);
        Collections.reverse(reversed);
        assertOrder(reversed, deep);
    }
    void assertOrder(List<?> input, List<?> expected) {
        var snapshot = new ArrayList<>(input);
        Object actual = run(order, TASK_ADAPTER, input);
        check(expected.equals(actual), "hierarchy expected " + expected + ", got " + actual);
        check(snapshot.equals(input), "must not mutate caller's list");
        check(expected.equals(run(order, TASK_ADAPTER, actual)), "ordering is idempotent");
    }
    Object run(Method method, Object... args) {
        var code = instructions(method);
        Map<Integer, Instruction> addresses = new HashMap<>();
        int offset = 0;
        for (var insn : code) { addresses.put(offset, insn); offset += insn.getCodeUnits(); }
        Object[] v = new Object[method.getImplementation().getRegisterCount()];
        System.arraycopy(args, 0, v, v.length - args.length, args.length);
        Object result = null;
        offset = 0;
        for (int steps = 0; steps < 1000000; steps++) {
            var insn = addresses.get(offset);
            var op = insn.getOpcode();
            int a = insn instanceof OneRegisterInstruction i ? i.getRegisterA() : -1;
            int b = insn instanceof TwoRegisterInstruction i ? i.getRegisterB() : -1;
            boolean branch = false;
            switch (op) {
                case INSTANCE_OF -> {
                    String type = ((TypeReference) ((ReferenceInstruction) insn).getReference()).getType();
                    v[a] = (type.equals(otherType) ? v[b] instanceof Row : v[b] == TASK_ADAPTER) ? 1 : 0;
                }
                case CHECK_CAST -> {}
                case NEW_INSTANCE -> v[a] = switch (((TypeReference) ((ReferenceInstruction) insn).getReference()).getType()) {
                    case "Ljava/util/HashMap;" -> new HashMap<>();
                    case "Ljava/util/TreeMap;" -> new TreeMap<>();
                    case "Ljava/util/ArrayList;" -> new ArrayList<>();
                    case "Ljava/util/HashSet;" -> new HashSet<>();
                    case "Ljava/util/ArrayDeque;" -> new ArrayDeque<>();
                    default -> throw new AssertionError("Unsupported instance");
                };
                case MOVE_RESULT, MOVE_RESULT_OBJECT -> v[a] = result;
                case NEG_INT -> v[a] = -(int) v[b];
                case ADD_INT_LIT8 -> v[a] = (int) v[b] + ((NarrowLiteralInstruction) insn).getNarrowLiteral();
                case IF_EQZ -> branch = zero(v[a]);
                case IF_NEZ -> branch = !zero(v[a]);
                case IF_LTZ -> branch = (int) v[a] < 0;
                case GOTO, GOTO_16, GOTO_32 -> branch = true;
                case RETURN_VOID -> { return null; }
                case RETURN_OBJECT -> { return v[a]; }
                case INVOKE_DIRECT, INVOKE_STATIC, INVOKE_INTERFACE, INVOKE_VIRTUAL -> {
                    var call = (FiveRegisterInstruction) insn;
                    var ref = (MethodReference) ((ReferenceInstruction) insn).getReference();
                    Object target = v[call.getRegisterC()];
                    Object arg = v[call.getRegisterD()];
                    String name = ref.getName();
                    if (name.equals("<init>")) {}
                    else if (name.equals(emit.getName())) run(emit, target, arg, v[call.getRegisterE()], v[call.getRegisterF()]);
                    else if (name.equals("valueOf")) result = target;
                    else if (target instanceof Row row) {
                        if (ref.getReturnType().equals("I")) result = row.priority();
                        else if (name.equals(parentGetter)) result = row.parent();
                        else if (name.equals(idGetter)) result = row.id();
                        else result = row; // Other -> Item.
                    } else if (target instanceof NavigableMap map && name.equals("descendingMap")) result = map.descendingMap();
                    else if (target instanceof Map map) result = switch (name) {
                        case "put" -> map.put(arg, v[call.getRegisterE()]);
                        case "get" -> map.get(arg);
                        case "containsKey" -> map.containsKey(arg) ? 1 : 0;
                        case "values" -> map.values();
                        default -> throw new AssertionError(ref);
                    };
                    else if (target instanceof Iterator it) result = name.equals("hasNext") ? (it.hasNext() ? 1 : 0) : it.next();
                    else if (target instanceof Deque stack && name.equals("push")) stack.push(arg);
                    else if (target instanceof Deque stack && name.equals("pop")) result = stack.pop();
                    else if (target instanceof Collection collection) result = switch (name) {
                        case "iterator" -> collection.iterator();
                        case "add" -> collection.add(arg) ? 1 : 0;
                        case "get" -> ((List) collection).get((int) arg);
                        case "size" -> collection.size();
                        case "isEmpty" -> collection.isEmpty() ? 1 : 0;
                        default -> throw new AssertionError(ref);
                    };
                    else throw new AssertionError("Unexpected call " + ref);
                }
                default -> throw new AssertionError("Unexpected ordering opcode " + op);
            }
            offset += branch ? ((OffsetInstruction) insn).getCodeOffset() : insn.getCodeUnits();
        }
        throw new AssertionError("Ordering failed to terminate");
    }
    static boolean zero(Object value) { return value == null || Objects.equals(value, 0); }
}
