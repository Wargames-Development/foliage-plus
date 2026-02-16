package com.wdg.foliageplus.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class FoliagePlusTransformer implements IClassTransformer {

    // Block class name in 1.7.10:
    // - Deobf: net.minecraft.block.Block
    // - Obf:   aji
    private static final String TARGET_CLASS_DEOBF = "net.minecraft.block.Block";
    private static final String TARGET_CLASS_OBF = "aji";

    // Use Object-based hook signature so this works regardless of deobf stage
    private static final String HOOK_OWNER = "com/wdg/foliageplus/core/FoliagePlusHooks";
    private static final String HOOK_NAME = "shouldSkipCollision";
    private static final String HOOK_DESC =
            "(Ljava/lang/Object;Ljava/lang/Object;IIILjava/lang/Object;)Z";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }

        // Match either pre-deobf (obf) or post-deobf (deobf)
        if (!TARGET_CLASS_DEOBF.equals(transformedName) && !TARGET_CLASS_OBF.equals(transformedName)
                && !TARGET_CLASS_DEOBF.equals(name) && !TARGET_CLASS_OBF.equals(name)) {
            return basicClass;
        }

        try {
            ClassNode cn = new ClassNode();
            ClassReader cr = new ClassReader(basicClass);
            cr.accept(cn, 0);

            boolean patched = false;

            for (MethodNode mn : cn.methods) {
                if (isAddCollisionBoxesToListLike(mn)) {
                    patched = injectEarlyReturn(mn) || patched;
                }
            }

            if (!patched) {
                System.out.println("[FoliagePlus] Transformer found Block but did not patch (method signature mismatch). name=" + name + " transformed=" + transformedName);
                return basicClass;
            }

            System.out.println("[FoliagePlus] Patched Block collision method. name=" + name + " transformed=" + transformedName);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            return cw.toByteArray();
        } catch (Throwable t) {
            System.out.println("[FoliagePlus] Transformer error while patching Block: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private boolean isAddCollisionBoxesToListLike(MethodNode mn) {
        if (mn == null) {
            return false;
        }

        // Names:
        // - MCP: addCollisionBoxesToList
        // - SRG: func_149743_a
        // - OBF: a   (common in 1.7.10)
        boolean nameMatch = "addCollisionBoxesToList".equals(mn.name)
                || "func_149743_a".equals(mn.name)
                || "a".equals(mn.name);

        if (!nameMatch) {
            return false;
        }

        // Descriptor can be deobf or obf. Instead of exact string match, validate shape:
        // (World, int, int, int, AABB, List, Entity) -> void
        String d = mn.desc;
        if (d == null || !d.endsWith(")V")) {
            return false;
        }

        // Must include:
        // - three ints "III"
        // - a List
        // - 3 object refs + List + Entity-ish
        if (!d.contains("III") || !d.contains("Ljava/util/List;")) {
            return false;
        }

        // Must have 7 args total; quick heuristic: count parameter separators by parsing
        // We'll accept both:
        // Deobf: (Lnet/minecraft/world/World;IIILnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;)V
        // Obf:   (Lahb;IIILazt;Ljava/util/List;Lsa;)V
        int args = countArgs(d);
        return args == 7;
    }

    private int countArgs(String desc) {
        int i = desc.indexOf('(') + 1;
        int end = desc.indexOf(')');
        int count = 0;
        while (i > 0 && i < end) {
            char c = desc.charAt(i);
            if (c == 'L') {
                int semi = desc.indexOf(';', i);
                if (semi < 0 || semi > end) break;
                count++;
                i = semi + 1;
            } else if (c == '[') {
                // skip arrays
                i++;
            } else {
                // primitives: I, Z, D, F, J, B, S, C
                count++;
                i++;
            }
        }
        return count;
    }

    private boolean injectEarlyReturn(MethodNode mn) {
        InsnList insn = new InsnList();

        // if (FoliagePlusHooks.shouldSkipCollision(this, world, x, y, z, entity)) return;
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
        insn.add(new VarInsnNode(Opcodes.ALOAD, 1)); // world
        insn.add(new VarInsnNode(Opcodes.ILOAD, 2)); // x
        insn.add(new VarInsnNode(Opcodes.ILOAD, 3)); // y
        insn.add(new VarInsnNode(Opcodes.ILOAD, 4)); // z
        insn.add(new VarInsnNode(Opcodes.ALOAD, 7)); // entity

        insn.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOK_OWNER,
                HOOK_NAME,
                HOOK_DESC,
                false
        ));

        LabelNode continueLabel = new LabelNode();
        insn.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
        insn.add(new InsnNode(Opcodes.RETURN));
        insn.add(continueLabel);

        mn.instructions.insert(insn);
        return true;
    }
}
