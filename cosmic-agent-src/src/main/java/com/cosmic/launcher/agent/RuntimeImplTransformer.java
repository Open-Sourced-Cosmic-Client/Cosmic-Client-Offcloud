package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class RuntimeImplTransformer
implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!"sun/management/RuntimeImpl".equals(className)) {
            return null;
        }
        System.out.println("[CosmicAgent] Transforming RuntimeImpl to hide agent");
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 2);
            cr.accept(new ClassVisitor(589824, cw){

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("getInputArguments".equals(name) && "()Ljava/util/List;".equals(descriptor)) {
                        System.out.println("[CosmicAgent] Patching RuntimeImpl.getInputArguments()");
                        return new MethodVisitor(589824, mv){

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == 176) {
                                    super.visitMethodInsn(184, "com/cosmic/launcher/agent/ArgFilter", "filterAgentArgs", "(Ljava/util/List;)Ljava/util/List;", false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    return mv;
                }
            }, 0);
            return cw.toByteArray();
        }
        catch (Exception e) {
            System.err.println("[CosmicAgent] ERROR transforming RuntimeImpl: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}

