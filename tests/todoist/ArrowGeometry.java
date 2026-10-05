package hooman.morphe.patches.todoist.subtasks;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import java.util.*;
import static hooman.morphe.patches.todoist.subtasks.LinkedSubtasksRegression.*;

/** Executes the actual geometry instructions, including register reuse and RTL branches. */
final class ArrowGeometry {
    static void verify(Method style) {
        for (float density : new float[]{1, 1.5f, 2, 2.75f, 3, 4}) {
            for (int rtl : new int[]{0, 1}) verify(style, density, rtl);
        }
    }
    static void verify(Method style, float density, int rtl) {
        var code = instructions(style);
        int start = -1, offset = 0;
        Map<Integer, Instruction> byAddress = new HashMap<>();
        for (var insn : code) {
            byAddress.put(offset, insn);
            if (insn instanceof ReferenceInstruction ri) {
                if (ri.getReference() instanceof FieldReference f && f.getDefiningClass().equals("Landroid/util/DisplayMetrics;") && f.getName().equals("density")) {
                    start = offset + insn.getCodeUnits();
                }
                if (insn.getOpcode() == Opcode.NEW_INSTANCE && ri.getReference() instanceof TypeReference t && t.getType().equals("Landroid/graphics/Path;")) break;
            }
            offset += insn.getCodeUnits();
        }
        // Populate the rest of the method's address map too.
        offset = 0;
        for (var insn : code) { byAddress.put(offset, insn); offset += insn.getCodeUnits(); }
        check(start >= 0, "geometry density anchor");
        Object[] v = new Object[13];
        v[3] = 20; // Guide X: all arrow insets are relative to this value.
        v[6] = Float.floatToIntBits(density);
        Object result = null;
        int width = -1, height = -1, inset = -1, arrowColor = 0;
        int curves = 0;
        Set<String> paintSettings = new HashSet<>();
        List<List<Float>> points = new ArrayList<>();
        offset = start;
        for (int steps = 0; steps < 300; steps++) {
            var insn = byAddress.get(offset);
            var op = insn.getOpcode();
            int a = insn instanceof OneRegisterInstruction i ? i.getRegisterA() : -1;
            int b = insn instanceof TwoRegisterInstruction i ? i.getRegisterB() : -1;
            int c = insn instanceof ThreeRegisterInstruction i ? i.getRegisterC() : -1;
            boolean branch = false;
            switch (op) {
                case CONST, CONST_4, CONST_16 -> v[a] = ((NarrowLiteralInstruction) insn).getNarrowLiteral();
                case MUL_FLOAT -> v[a] = Float.floatToIntBits(Float.intBitsToFloat((int) v[b]) * Float.intBitsToFloat((int) v[c]));
                case INT_TO_FLOAT -> v[a] = Float.floatToIntBits((int) v[b]);
                case SGET_OBJECT -> v[a] = ((FieldReference) ((ReferenceInstruction) insn).getReference()).getName();
                case FLOAT_TO_INT -> v[a] = (int) Float.intBitsToFloat((int) v[b]);
                case MUL_INT_LIT8 -> v[a] = (int) v[b] * ((NarrowLiteralInstruction) insn).getNarrowLiteral();
                case ADD_INT -> v[a] = (int) v[b] + (int) v[c];
                case NEW_INSTANCE -> v[a] = ((TypeReference) ((ReferenceInstruction) insn).getReference()).getType();
                case IGET_OBJECT -> v[a] = "container";
                case NEW_ARRAY -> v[a] = new Object[(int) v[b]];
                case APUT_OBJECT -> ((Object[]) v[b])[(int) v[c]] = v[a];
                case MOVE_RESULT, MOVE_RESULT_OBJECT -> v[a] = result;
                case IF_EQZ -> branch = Objects.equals(v[a], 0);
                case GOTO, GOTO_16, GOTO_32 -> branch = true;
                case INVOKE_DIRECT, INVOKE_VIRTUAL -> {
                    var call = (FiveRegisterInstruction) insn;
                    var ref = (MethodReference) ((ReferenceInstruction) insn).getReference();
                    Object target = v[call.getRegisterC()];
                    Object arg = v[call.getRegisterD()];
                    Object arg2 = v[call.getRegisterE()];
                    switch (ref.getName()) {
                        case "getLayoutDirection" -> result = rtl;
                        case "getPaint" -> result = "paint";
                        case "setIntrinsicWidth" -> width = (int) arg;
                        case "setIntrinsicHeight" -> height = (int) arg;
                        case "setSize", "setCornerRadius" -> {}
                        case "setStyle", "setStrokeCap", "setStrokeJoin" -> paintSettings.add(ref.getName() + ":" + arg);
                        case "setStrokeWidth" -> check(Math.abs(Float.intBitsToFloat((int) arg) - 1.6f) < .001f, "consistent stroke weight");
                        case "quadTo" -> {
                            curves++;
                            check(Float.intBitsToFloat((int) arg) == (rtl == 0 ? 1f : 13f), "mirrored elbow control");
                            check(Float.intBitsToFloat((int) arg2) == 8f, "elbow tangent height");
                            check(Float.intBitsToFloat((int) v[call.getRegisterF()]) == (rtl == 0 ? 6f : 8f), "elbow endpoint");
                            check(Float.intBitsToFloat((int) v[call.getRegisterG()]) == 8f, "horizontal elbow exit");
                        }
                        case "setColor" -> { if (Objects.equals(target, "paint")) arrowColor = (int) arg; }
                        case "moveTo", "lineTo" -> points.add(List.of(Float.intBitsToFloat((int) arg), Float.intBitsToFloat((int) arg2)));
                        case "setLayerInsetStart" -> {
                            if (!Objects.equals(arg, 1)) break;
                            inset = (int) arg2;
                            int thickness = (int) (2 * density);
                            check(width == 7 * thickness && height == 8 * thickness,
                                "arrow size must be positive and density-scaled: " + width + "x" + height);
                            check(inset == 20, "guide and curved connector share the same origin");
                            check(curves == 1, "one smooth quadratic elbow");
                            check(paintSettings.containsAll(Set.of("setStyle:STROKE", "setStrokeCap:ROUND", "setStrokeJoin:ROUND")), "rounded open chevron");
                            check(arrowColor == 0xffdb4c3f, "red arrow paint");
                            check(points.equals(rtl == 0
                                ? List.of(List.of(1f, 1f), List.of(1f, 3f), List.of(12f, 8f), List.of(9f, 5f), List.of(12f, 8f), List.of(9f, 11f))
                                : List.of(List.of(13f, 1f), List.of(13f, 3f), List.of(2f, 8f), List.of(5f, 5f), List.of(2f, 8f), List.of(5f, 11f))), "triangle direction");
                            return;
                        }
                        case "<init>", "close", "setAntiAlias", "setLayerGravity" -> {}
                        default -> throw new AssertionError("Unexpected geometry call " + ref);
                    }
                }
                default -> throw new AssertionError("Unexpected geometry opcode " + op);
            }
            offset += branch ? ((OffsetInstruction) insn).getCodeOffset() : insn.getCodeUnits();
        }
        throw new AssertionError("Geometry did not position the arrow head");
    }
}
