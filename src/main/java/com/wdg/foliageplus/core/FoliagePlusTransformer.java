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
    private static final String WORLD_DEOBF = "net.minecraft.world.World";
    private static final String WORLD_OBF = "ahb";
    private static final String COLLISION_DESC =
            "(Lnet/minecraft/world/World;IIILnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;)V";
    private static final String COLLISION_DESC_OBF = "(Lahb;IIILazt;Ljava/util/List;Lsa;)V";

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

        boolean blockClass = matches(name, transformedName, TARGET_CLASS_DEOBF, TARGET_CLASS_OBF);
        boolean worldClass = matches(name, transformedName, WORLD_DEOBF, WORLD_OBF);
        if (!blockClass && !worldClass) {
            return basicClass;
        }

        try {
            ClassNode cn = new ClassNode();
            ClassReader cr = new ClassReader(basicClass);
            cr.accept(cn, 0);

            boolean patched = false;

            for (MethodNode mn : cn.methods) {
                if (blockClass && isAddCollisionBoxesToListLike(mn)) {
                    patched = injectEarlyReturn(mn) || patched;
                } else if (worldClass && isEntityCollisionQuery(mn)) {
                    patched = guardCollisionCalls(mn) || patched;
                }
            }

            if (!patched) {
                System.out.println("[FoliagePlus] No new collision hook installed (already patched or signature mismatch). name=" + name + " transformed=" + transformedName);
                return basicClass;
            }

            System.out.println("[FoliagePlus] Patched collision hook. name=" + name + " transformed=" + transformedName);

            // The World rewrite preserves the exact operand stack and control
            // flow. Keep other coremods' frames instead of loading game classes
            // recursively while ASM tries to resolve their common superclass.
            ClassWriter cw = new ClassWriter(worldClass ? 0 : ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            return cw.toByteArray();
        } catch (Throwable t) {
            System.out.println("[FoliagePlus] Transformer error while patching " + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private boolean matches(String name, String transformedName, String deobf, String obf) {
        return deobf.equals(name) || deobf.equals(transformedName) || obf.equals(name) || obf.equals(transformedName);
    }

    private boolean isEntityCollisionQuery(MethodNode method) {
        return ("getCollidingBoundingBoxes".equals(method.name) || "func_72945_a".equals(method.name)
                || "a".equals(method.name))
                && ("(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;".equals(method.desc)
                || "(Lsa;Lazt;)Ljava/util/List;".equals(method.desc));
    }

    private boolean guardCollisionCalls(MethodNode method) {
        boolean patched = false;
        for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
            if (!(node instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) node;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !("net/minecraft/block/Block".equals(call.owner) || "aji".equals(call.owner))
                    || !isCollisionSignature(call.name, call.desc)) continue;
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = HOOK_OWNER;
            call.name = "addCollisionBoxes";
            call.desc = "(Ljava/lang/Object;Ljava/lang/Object;IIILjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V";
            call.itf = false;
            patched = true;
        }
        return patched;
    }

    private boolean isAddCollisionBoxesToListLike(MethodNode mn) {
        if (mn == null) {
            return false;
        }

        return (mn.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0
                && isCollisionSignature(mn.name, mn.desc);
    }

    private boolean isCollisionSignature(String name, String descriptor) {
        return ("addCollisionBoxesToList".equals(name) || "func_149743_a".equals(name) || "a".equals(name))
                && (COLLISION_DESC.equals(descriptor) || COLLISION_DESC_OBF.equals(descriptor));
    }

    private boolean injectEarlyReturn(MethodNode mn) {
        for (AbstractInsnNode node = mn.instructions.getFirst(); node != null; node = node.getNext()) {
            if (node instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) node;
                if (HOOK_OWNER.equals(call.owner) && HOOK_NAME.equals(call.name) && HOOK_DESC.equals(call.desc)) {
                    return false;
                }
            }
        }
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
