package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class MainMenuTransformer implements ClassFileTransformer {
    public MainMenuTransformer() {
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        if (className.equals("cosmicclient/HK")) {
            return this.patchHK(classfileBuffer);
        }
        return null;
    }

    private byte[] patchHK(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("processRequest")) {
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitVarInsn(25, 0); // ALOAD 0
                                this.mv.visitVarInsn(25, 1); // ALOAD 1
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "interceptResource", "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
                            }
                        };
                    }
                    return mv;
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched HK - main menu resource interception");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] HK patch failed: " + e.getMessage());
            return null;
        }
    }
}
