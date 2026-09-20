package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.Label;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class CreativeSearchFixTransformer
implements ClassFileTransformer {
    @Override
    public byte[] transform(final ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        if (!className.equals("cosmicclient/F0")) {
            return null;
        }
        System.out.println("[CosmicAgent] Intercepted F0 (GuiContainerCreative) for search key fix");
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 2){

                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    if (loader != null) {
                        try {
                            Class<?> c1 = Class.forName(type1.replace('/', '.'), false, loader);
                            Class<?> c2 = Class.forName(type2.replace('/', '.'), false, loader);
                            if (c1.isAssignableFrom(c2)) {
                                return type1;
                            }
                            if (c2.isAssignableFrom(c1)) {
                                return type2;
                            }
                            if (c1.isInterface() || c2.isInterface()) {
                                return "java/lang/Object";
                            }
                            while (!(c1 = c1.getSuperclass()).isAssignableFrom(c2)) {
                            }
                            return c1.getName().replace('.', '/');
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                    }
                    return "java/lang/Object";
                }
            };
            final boolean[] patched = new boolean[]{false};
            cr.accept(new ClassVisitor(589824, cw){

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("b") && descriptor.equals("(CCICI)V")) {
                        patched[0] = true;
                        System.out.println("[CosmicAgent] Patching F0.b(CCICI)V - injecting search key fix");
                        return new MethodVisitor(589824, mv){

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label skip = new Label();
                                super.visitVarInsn(25, 0);
                                super.visitVarInsn(21, 1);
                                super.visitVarInsn(21, 3);
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/CreativeSearchFix", "handle", "(Ljava/lang/Object;CI)Z", false);
                                super.visitJumpInsn(153, skip);
                                super.visitInsn(177);
                                super.visitLabel(skip);
                            }
                        };
                    }
                    return mv;
                }
            }, 8);
            if (!patched[0]) {
                System.out.println("[CosmicAgent] WARNING: F0 intercepted but b(CCICI)V not found!");
                return null;
            }
            System.out.println("[CosmicAgent] F0 patch applied successfully");
            return cw.toByteArray();
        }
        catch (Exception e) {
            System.err.println("[CosmicAgent] ERROR patching F0: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}

