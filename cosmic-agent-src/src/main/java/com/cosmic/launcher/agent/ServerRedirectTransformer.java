package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class ServerRedirectTransformer
implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        if (!className.equals("java/net/InetAddress")) {
            return null;
        }
        System.out.println("[CosmicAgent] Intercepted InetAddress for server redirect");
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            final boolean[] patched = new boolean[]{false};
            cr.accept(new ClassVisitor(589824, cw){

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("getAllByName") && descriptor.equals("(Ljava/lang/String;)[Ljava/net/InetAddress;")) {
                        patched[0] = true;
                        System.out.println("[CosmicAgent] Patching InetAddress.getAllByName - adding server redirect");
                        return new MethodVisitor(589824, mv){

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(25, 0);
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/ServerRedirectHelper", "maybeRedirect", "(Ljava/lang/String;)Ljava/lang/String;", false);
                                super.visitVarInsn(58, 0);
                            }
                        };
                    }
                    if (name.equals("getByName") && descriptor.equals("(Ljava/lang/String;)Ljava/net/InetAddress;")) {
                        System.out.println("[CosmicAgent] Patching InetAddress.getByName - adding server redirect");
                        return new MethodVisitor(589824, mv){

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(25, 0);
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/ServerRedirectHelper", "maybeRedirect", "(Ljava/lang/String;)Ljava/lang/String;", false);
                                super.visitVarInsn(58, 0);
                            }
                        };
                    }
                    return mv;
                }
            }, 8);
            if (!patched[0]) {
                System.out.println("[CosmicAgent] WARNING: InetAddress.getAllByName not found");
                return null;
            }
            return cw.toByteArray();
        }
        catch (Exception e) {
            System.err.println("[CosmicAgent] ERROR patching InetAddress: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}

