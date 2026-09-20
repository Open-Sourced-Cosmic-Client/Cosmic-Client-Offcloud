package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class DisplayTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) return null;
        if (!className.equals("org/lwjgl/opengl/Display") && !className.equals("org/lwjglx/opengl/Display")) {
            return null;
        }

        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("update") && descriptor.equals("()V") && (access & 8) != 0) {
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/StartupProgressHelper", "onDisplayUpdate", "()V", false);
                            }
                        };
                    }
                    return mv;
                }
            }, 0);

            System.out.println("[CosmicAgent] Patched " + className + " - hooked update() for in-game loading progress screen");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] DisplayTransformer error: " + t.getMessage());
            return null;
        }
    }
}
