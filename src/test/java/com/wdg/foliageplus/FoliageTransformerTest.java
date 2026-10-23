package com.wdg.foliageplus;

import com.wdg.foliageplus.core.FoliagePlusTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import static com.wdg.foliageplus.RegressionAssertions.*;

public class FoliageTransformerTest {
    private static final String OWNER = "com/wdg/foliageplus/core/FoliagePlusHooks";
    private static final String DEOBF = "(Lnet/minecraft/world/World;IIILnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;)V";
    private static final String OBF = "(Lahb;IIILazt;Ljava/util/List;Lsa;)V";

    public void test_modMetadataIsValidJson() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("mcmod.info");
        assertNotNull(stream);
        try (InputStreamReader reader = new InputStreamReader(stream,"UTF-8")) {
            com.google.gson.JsonArray mods = new com.google.gson.JsonParser().parse(reader).getAsJsonArray();
            assertEquals(1,mods.size());
            assertTrue("foliageplus".equals(mods.get(0).getAsJsonObject().get("modid").getAsString()));
            assertTrue(mods.get(0).getAsJsonObject().get("description").getAsString().length() > 0);
        }
    }

    public void test_blockHooksCoverMcpSrgAndObfuscatedNames() throws Exception {
        for (String name : new String[] {"addCollisionBoxesToList", "func_149743_a", "a"}) {
            boolean obf = "a".equals(name);
            String owner = obf ? "aji" : "net/minecraft/block/Block";
            byte[] original = block(owner, name, obf ? OBF : DEOBF, Opcodes.ACC_PUBLIC);
            FoliagePlusTransformer transformer = new FoliagePlusTransformer();
            byte[] transformed = transformer.transform(owner.replace('/','.'),owner.replace('/','.'),original);
            assertEquals(1,count(transformed,"shouldSkipCollision"));
            verify(transformed);
            assertTrue(Arrays.equals(transformed,transformer.transform(owner.replace('/','.'),owner.replace('/','.'),transformed)));
        }
    }

    public void test_unrelatedOverloadsStaticAndUnrelatedClassesStayUnchanged() {
        FoliagePlusTransformer transformer = new FoliagePlusTransformer();
        byte[] overload = block("net/minecraft/block/Block", "a", "(Ljava/lang/String;IIILjava/lang/String;Ljava/util/List;Ljava/lang/String;)V", Opcodes.ACC_PUBLIC);
        assertTrue(Arrays.equals(overload,transformer.transform("aji","net.minecraft.block.Block",overload)));
        byte[] staticMethod = block("net/minecraft/block/Block", "addCollisionBoxesToList", DEOBF, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        assertTrue(Arrays.equals(staticMethod,transformer.transform("aji","net.minecraft.block.Block",staticMethod)));
        byte[] unrelated = block("example/AnotherMod", "addCollisionBoxesToList", DEOBF, Opcodes.ACC_PUBLIC);
        assertTrue(Arrays.equals(unrelated,transformer.transform("example.AnotherMod","example.AnotherMod",unrelated)));
        assertNull(transformer.transform("aji","net.minecraft.block.Block",null));
    }

    public void test_worldDispatchGuardsCustomOverridesInEveryNamingEnvironment() throws Exception {
        for (String name : new String[] {"getCollidingBoundingBoxes", "func_72945_a", "a"}) {
            boolean obf = "a".equals(name);
            String owner = obf ? "ahb" : "net/minecraft/world/World";
            byte[] original = world(owner,name,obf);
            FoliagePlusTransformer transformer = new FoliagePlusTransformer();
            byte[] transformed = transformer.transform(owner.replace('/','.'),owner.replace('/','.'),original);
            assertEquals(1,count(transformed,"addCollisionBoxes"));
            verify(transformed);
            assertTrue(Arrays.equals(transformed,transformer.transform(owner.replace('/','.'),owner.replace('/','.'),transformed)));
        }
    }

    public void test_realForgeBlockAndWorldBytecodeTransformsAndVerifies() throws Exception {
        for (String path : new String[] {"net/minecraft/block/Block", "net/minecraft/world/World"}) {
            InputStream stream = getClass().getClassLoader().getResourceAsStream(path + ".class");
            assertNotNull(stream);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read=stream.read(buffer)) != -1) output.write(buffer,0,read);
            } finally { stream.close(); }
            byte[] transformed = new FoliagePlusTransformer().transform(path.replace('/','.'),path.replace('/','.'),output.toByteArray());
            assertEquals(1,count(transformed,path.endsWith("Block") ? "shouldSkipCollision" : "addCollisionBoxes"));
            verify(transformed);
        }
    }

    private static byte[] block(String owner,String name,String descriptor,int access) {
        ClassNode node = base(owner);
        MethodNode method = new MethodNode(access,name,descriptor,null,null);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(method);
        return bytes(node);
    }

    private static byte[] world(String owner,String name,boolean obf) {
        ClassNode node = base(owner);
        String descriptor = obf ? "(Lsa;Lazt;)Ljava/util/List;"
                : "(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC,name,descriptor,null,null);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL)); // block receiver
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,2));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL)); // collision list
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,obf ? "aji" : "net/minecraft/block/Block",
                obf ? "a" : "addCollisionBoxesToList",obf ? OBF : DEOBF,false));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(method);
        return bytes(node);
    }

    private static ClassNode base(String owner) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_7;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = owner;
        node.superName = "java/lang/Object";
        return node;
    }

    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static int count(byte[] bytes,String name) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node,0);
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (OWNER.equals(call.owner) && name.equals(call.name)) count++;
                }
            }
        }
        return count;
    }

    private static void verify(byte[] bytes) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node,0);
        for (MethodNode method : node.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                new Analyzer(new BasicVerifier()).analyze(node.name,method);
            }
        }
    }
}
