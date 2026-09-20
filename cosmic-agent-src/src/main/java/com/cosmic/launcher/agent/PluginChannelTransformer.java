package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class PluginChannelTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        
        boolean match = className.equals("cosmicclient/r6") || className.equals("r6");
        if (!match) {
            return null;
        }

        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            final boolean[] patched = new boolean[]{false};

            cr.accept(new ClassVisitor(589824, cw) {

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("a") && descriptor.contains("z6") && descriptor.endsWith(")V")) {
                        patched[0] = true;
                        return new MethodVisitor(589824, mv) {

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(25, 4);
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/PluginChannelHelper", "onCustomPayload", "(Ljava/lang/Object;)V", false);
                            }
                        };
                    }
                    return mv;
                }
            }, 0);

            if (patched[0]) {
                System.out.println("[CosmicAgent/PluginChannel] Hooked custom payload handler in " + className);
                return cw.toByteArray();
            }
            return null;
        } catch (Exception e) {
            System.err.println("[CosmicAgent/PluginChannel] ERROR: " + e.getMessage());
            return null;
        }
    }
}
