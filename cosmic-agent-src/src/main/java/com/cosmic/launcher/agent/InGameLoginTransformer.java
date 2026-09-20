package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Bytecode Transformer for In-Game Authentication & Account Selection:
 * 1. Patches bq.run() to process in-game logins cleanly and offline-friendly.
 * 2. Patches oL.m(int, long) to safely switch accounts in-game without throwing auth errors or deleting accounts.
 * 3. Patches iR.<init>() to sync accounts from launcher accounts.json.
 */
public class InGameLoginTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) return null;

        // Skip agent classes
        if (className.startsWith("com/cosmic/launcher/agent/") || className.startsWith("com/cosmic/launcher/asm/")) {
            return null;
        }

        final boolean isBQ = className.equals("bq") || className.equals("cosmicclient/bq") || className.endsWith("/bq");
        final boolean isOL = className.equals("oL") || className.equals("cosmicclient/oL") || className.endsWith("/oL");
        final boolean isIR = className.equals("iR") || className.equals("cosmicclient/iR") || className.endsWith("/iR");
        final boolean isBH = className.equals("bh") || className.equals("cosmicclient/bh") || className.endsWith("/bh");

        if (!isBQ && !isOL && !isIR && !isBH) {
            return null;
        }

        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            final boolean[] patched = new boolean[]{false};
            final String normalizedClassName = className;

            cr.accept(new ClassVisitor(589824, cw) {

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    // 0. Neutralize bh.a(J)V -> prevent background Microsoft OAuth popup
                    if (isBH && name.equals("a") && descriptor.equals("(J)V")) {
                        patched[0] = true;
                        System.out.println("[CosmicAgent] Neutralizing bh.a(J)V -> Microsoft OAuth popup permanently disabled");
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(177); // RETURN immediately
                            mv.visitMaxs(0, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 1. Hook bq.run()V -> in-game Add Account worker
                    if (isBQ && name.equals("run") && descriptor.equals("()V")) {
                        patched[0] = true;
                        System.out.println("[CosmicAgent] Hooking bq.run() -> InGameLoginHelper.handleInGameLogin");
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // ALOAD 0 (this bq instance)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/InGameLoginHelper", "handleInGameLogin", "(Ljava/lang/Thread;)V", false);
                            mv.visitInsn(177); // RETURN
                            mv.visitMaxs(1, 1);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 2. Hook oL.m(IJ)Z -> in-game account select / activate
                    if (isOL && name.equals("m") && descriptor.equals("(IJ)Z")) {
                        patched[0] = true;
                        System.out.println("[CosmicAgent] Hooking oL.m(IJ)Z -> InGameLoginHelper.handleAccountSwitch");
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // ALOAD 0 (this oL instance)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/InGameLoginHelper", "handleAccountSwitch", "(Ljava/lang/Object;)Z", false);
                            mv.visitInsn(172); // IRETURN
                            mv.visitMaxs(1, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 3. Hook iR.<init>()V -> sync accounts on AccountManager creation
                    if (isIR && name.equals("<init>") && descriptor.equals("()V")) {
                        patched[0] = true;
                        System.out.println("[CosmicAgent] Hooking iR.<init>()V -> InGameLoginHelper.syncAccountsFile");
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == 177) { // RETURN
                                    super.visitVarInsn(25, 0); // ALOAD 0
                                    super.visitFieldInsn(180, normalizedClassName, "e", "Ljava/io/File;");
                                    super.visitMethodInsn(184, "com/cosmic/launcher/agent/InGameLoginHelper", "syncAccountsFile", "(Ljava/io/File;)V", false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);

            if (patched[0]) {
                return cw.toByteArray();
            }
            return null;
        } catch (Exception e) {
            System.err.println("[CosmicAgent] Error transforming in-game login (" + className + "): " + e.getMessage());
            return null;
        }
    }
}
