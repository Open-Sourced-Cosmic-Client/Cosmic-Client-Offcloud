package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class JcefCheckTransformer implements ClassFileTransformer {
    private static final String TARGET_DESC = "(Ljava/io/File;J)Z";

    @Override
    public byte[] transform(ClassLoader loader, final String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        if (!className.equals("cosmicclient/Go") && !className.equals("Go")) {
            return null;
        }
        System.out.println("[CosmicAgent] Intercepted class: " + className);
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 2);
            final boolean[] patched = new boolean[]{false};
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("a") && descriptor.equals(TARGET_DESC)) {
                        patched[0] = true;
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitInsn(4); // ICONST_1 (true)
                                this.mv.visitInsn(172); // IRETURN
                            }
                        };
                    }
                    return mv;
                }
            }, 0);
            if (!patched[0]) {
                System.out.println("[CosmicAgent] WARNING: " + className + " intercepted but a(File,long)Z not found");
                return null;
            }
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] ERROR patching " + className + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
