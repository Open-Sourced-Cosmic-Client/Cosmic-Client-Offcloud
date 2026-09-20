package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class PacketStringRedirectTransformer
implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        if (!className.equals("cosmicclient/cB")) {
            return null;
        }
        System.out.println("[CosmicAgent] Intercepted cB (PacketBuffer) for hostname redirect");
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            final boolean[] patched = new boolean[]{false};
            cr.accept(new ClassVisitor(589824, cw){

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("a") && descriptor.equals("(SIILjava/lang/String;)Lcosmicclient/cB;")) {
                        patched[0] = true;
                        System.out.println("[CosmicAgent] Patching cB.a(SIIString) - adding hostname redirect");
                        return new MethodVisitor(589824, mv){

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(25, 4);
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/ServerRedirectHelper", "replaceInString", "(Ljava/lang/String;)Ljava/lang/String;", false);
                                super.visitVarInsn(58, 4);
                            }
                        };
                    }
                    return mv;
                }
            }, 8);
            if (!patched[0]) {
                System.out.println("[CosmicAgent] WARNING: cB.a(SIIString) not found");
                return null;
            }
            return cw.toByteArray();
        }
        catch (Exception e) {
            System.err.println("[CosmicAgent] ERROR patching cB: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}

