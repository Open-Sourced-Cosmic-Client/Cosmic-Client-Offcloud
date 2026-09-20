package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.Label;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Intercepts the legacy Cosmic Client voice recorder class (oI)
 * to safely neutralize startup microphone initialization (h and k methods)
 * that causes LineUnavailableException on machines with non-standard audio devices.
 */
public class VoiceRecorderFixTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) return null;
        if (!className.equals("oI") && !className.equals("cosmicclient/oI")) return null;

        System.out.println("[CosmicAgent] Immunizing voice recorder class " + className + " against LineUnavailableException...");
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 2);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ((name.equals("h") && descriptor.equals("()V")) || 
                        (name.equals("k") && descriptor.equals("(ICC)V"))) {
                        System.out.println("[CosmicAgent] Safely neutralizing " + className + "." + name + descriptor);
                        return new MethodVisitor(589824, mv) {
                            private boolean replaced = false;

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitInsn(177); // RETURN
                                this.replaced = true;
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (!this.replaced) super.visitInsn(opcode);
                            }

                            @Override
                            public void visitIntInsn(int opcode, int operand) {
                                if (!this.replaced) super.visitIntInsn(opcode, operand);
                            }

                            @Override
                            public void visitVarInsn(int opcode, int var) {
                                if (!this.replaced) super.visitVarInsn(opcode, var);
                            }

                            @Override
                            public void visitTypeInsn(int opcode, String type) {
                                if (!this.replaced) super.visitTypeInsn(opcode, type);
                            }

                            @Override
                            public void visitFieldInsn(int opcode, String owner, String name2, String descriptor2) {
                                if (!this.replaced) super.visitFieldInsn(opcode, owner, name2, descriptor2);
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name2, String descriptor2, boolean isInterface) {
                                if (!this.replaced) super.visitMethodInsn(opcode, owner, name2, descriptor2, isInterface);
                            }

                            @Override
                            public void visitJumpInsn(int opcode, Label label) {
                                if (!this.replaced) super.visitJumpInsn(opcode, label);
                            }

                            @Override
                            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                                // Entire method is replaced with single RETURN; suppress all try-catch blocks
                            }

                            @Override
                            public void visitLabel(Label label) {
                                if (!this.replaced) super.visitLabel(label);
                            }

                            @Override
                            public void visitLineNumber(int line, Label start) {
                                if (!this.replaced) super.visitLineNumber(line, start);
                            }

                            @Override
                            public void visitLocalVariable(String name2, String descriptor2, String signature2, Label start, Label end, int index) {
                                // Suppress local variables
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                super.visitMaxs(0, 0);
                            }
                        };
                    }
                    return mv;
                }
            }, 0);
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Error transforming " + className + ": " + t.getMessage());
            return null;
        }
    }
}
