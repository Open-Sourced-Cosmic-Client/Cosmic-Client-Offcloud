package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class AuthInterceptorTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }

        // Avoid transforming agent or asm classes
        if (className.startsWith("com/cosmic/launcher/agent/") || className.startsWith("com/cosmic/launcher/asm/")) {
            return null;
        }

        final boolean isKH = className.equals("kH") || className.equals("cosmicclient/kH") || className.equals("net/minecraft/client/kH") || className.equals("net/minecraft/kH");
        final boolean isOT = className.equals("ot") || className.equals("cosmicclient/ot") || className.equals("net/minecraft/client/ot") || className.equals("net/minecraft/ot");

        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            final boolean[] patched = new boolean[]{false};

            cr.accept(new ClassVisitor(589824, cw) {

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    // 1. Direct offline mock for kH.a(String, long) -> returns valid ot JSON immediately
                    if (isKH && name.equals("a") && descriptor.equals("(Ljava/lang/String;J)Ljava/lang/String;")) {
                        patched[0] = true;
                        System.out.println("[CosmicAgent] Patched kH.a -> 100% pure offline auth mock");
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // ALOAD 0 (requestBody JSON)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/AuthHelper", "mockAuthRequest", "(Ljava/lang/String;)Ljava/lang/String;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(1, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 2. Bypass ot.class validation checks so offline auth is always 100% valid
                    if (isOT) {
                        // ot.b(IJ)V -> client field regex validation: no-op return
                        if (name.equals("b") && descriptor.equals("(IJ)V")) {
                            patched[0] = true;
                            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                            if (mv != null) {
                                mv.visitCode();
                                mv.visitInsn(177); // RETURN
                                mv.visitMaxs(0, 3);
                                mv.visitEnd();
                            }
                            return null;
                        }

                        // ot.a(SZIC)V -> server field validation: no-op return
                        if (name.equals("a") && descriptor.equals("(SZIC)V")) {
                            patched[0] = true;
                            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                            if (mv != null) {
                                mv.visitCode();
                                mv.visitInsn(177); // RETURN
                                mv.visitMaxs(0, 5);
                                mv.visitEnd();
                            }
                            return null;
                        }

                        // ot.c()Z -> lifespan validation: return true
                        if (name.equals("c") && descriptor.equals("()Z")) {
                            patched[0] = true;
                            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                            if (mv != null) {
                                mv.visitCode();
                                mv.visitInsn(4); // ICONST_1
                                mv.visitInsn(172); // IRETURN
                                mv.visitMaxs(1, 1);
                                mv.visitEnd();
                            }
                            return null;
                        }

                        // ot.a(Lot;)Z and ot.b(Lot;)Z -> response matching: return true
                        if ((name.equals("a") || name.equals("b")) && descriptor.endsWith(";Lot;)Z")) {
                            patched[0] = true;
                            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                            if (mv != null) {
                                mv.visitCode();
                                mv.visitInsn(4); // ICONST_1
                                mv.visitInsn(172); // IRETURN
                                mv.visitMaxs(1, 2);
                                mv.visitEnd();
                            }
                            return null;
                        }

                        // ot.a(Lot;)Z
                        if ((name.equals("a") || name.equals("b")) && descriptor.contains("ot") && descriptor.endsWith(")Z")) {
                            patched[0] = true;
                            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                            if (mv != null) {
                                mv.visitCode();
                                mv.visitInsn(4); // ICONST_1
                                mv.visitInsn(172); // IRETURN
                                mv.visitMaxs(1, 3);
                                mv.visitEnd();
                            }
                            return null;
                        }
                    }

                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    return new MethodVisitor(589824, mv) {

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName, String desc, boolean itf) {
                            // Intercept URL.openConnection()
                            if (opcode == 182 && owner.equals("java/net/URL") && mName.equals("openConnection") && desc.equals("()Ljava/net/URLConnection;")) {
                                patched[0] = true;
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/AuthHelper", "openConnection", "(Ljava/net/URL;)Ljava/net/URLConnection;", false);
                                return;
                            }

                            // Intercept URL.openConnection(Proxy)
                            if (opcode == 182 && owner.equals("java/net/URL") && mName.equals("openConnection") && desc.equals("(Ljava/net/Proxy;)Ljava/net/URLConnection;")) {
                                patched[0] = true;
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/AuthHelper", "openConnectionProxy", "(Ljava/net/URL;Ljava/net/Proxy;)Ljava/net/URLConnection;", false);
                                return;
                            }

                            // Safeguard iz.n() calls to return client version string
                            if (opcode == 182 && owner.equals("iz") && mName.equals("n") && desc.equals("()Ljava/lang/String;")) {
                                patched[0] = true;
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/AuthHelper", "getClientVersion", "(Ljava/lang/Object;)Ljava/lang/String;", false);
                                return;
                            }

                            super.visitMethodInsn(opcode, owner, mName, desc, itf);
                        }
                    };
                }
            }, 0);

            if (patched[0]) {
                if (isOT) {
                    System.out.println("[CosmicAgent] Patched ot validation -> 100% pure offline valid");
                }
                return cw.toByteArray();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
