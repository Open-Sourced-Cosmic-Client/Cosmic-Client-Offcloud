package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class JcefCheckTransformer
implements ClassFileTransformer {
    private static final String TARGET_DESC = "(Ljava/io/File;J)Z";

    @Override
    public byte[] transform(ClassLoader loader, final String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        if (!className.equals("cosmicclient/Go") && !className.equals("Go") &&
            !className.equals("cosmicclient/CW") && !className.equals("CW") &&
            !className.equals("cosmicclient/UQ") && !className.equals("UQ")) {
            return null;
        }
        System.out.println("[CosmicAgent] JCEF inspection on class: " + className);
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 2);
            cr.accept(new ClassVisitor(589824, cw){
                @Override
                public com.cosmic.launcher.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    System.out.println("[CosmicAgent] " + className + " FIELD: " + name + " " + descriptor);
                    return super.visitField(access, name, descriptor, signature, value);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    if (className.equals("cosmicclient/CW") || className.equals("CW")) {
                        // Keep constructors, static class initializer, and obfuscator static decoders intact
                        if (name.equals("<init>") || name.equals("<clinit>") || 
                            (name.equals("a") && descriptor.equals("(Ljava/lang/String;)Lcosmicclient/CW;")) ||
                            descriptor.contains("MethodHandles") || 
                            descriptor.equals("([B)Ljava/lang/String;") || 
                            descriptor.equals("(IJ)Ljava/lang/String;") || 
                            descriptor.equals("(Ljava/lang/Exception;)Ljava/lang/Exception;")) {
                            return mv;
                        }

                        // Neutralize all CEF operations in CW
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                if (descriptor.endsWith("V")) {
                                    this.mv.visitInsn(177); // RETURN
                                } else if (descriptor.endsWith("Z")) {
                                    this.mv.visitInsn(3); // ICONST_0 (false)
                                    this.mv.visitInsn(172); // IRETURN
                                } else if (descriptor.endsWith("I") || descriptor.endsWith("S") || descriptor.endsWith("B") || descriptor.endsWith("C")) {
                                    this.mv.visitInsn(3); // ICONST_0
                                    this.mv.visitInsn(172); // IRETURN
                                } else if (descriptor.endsWith("D")) {
                                    this.mv.visitInsn(14); // DCONST_0
                                    this.mv.visitInsn(175); // DRETURN
                                } else if (descriptor.endsWith("F")) {
                                    this.mv.visitInsn(11); // FCONST_0
                                    this.mv.visitInsn(174); // FRETURN
                                } else if (descriptor.endsWith("J")) {
                                    this.mv.visitInsn(9); // LCONST_0
                                    this.mv.visitInsn(173); // LRETURN
                                } else {
                                    this.mv.visitInsn(1); // ACONST_NULL
                                    this.mv.visitInsn(176); // ARETURN
                                }
                            }

                            @Override public void visitInsn(int opcode) {}
                            @Override public void visitIntInsn(int opcode, int operand) {}
                            @Override public void visitVarInsn(int opcode, int var) {}
                            @Override public void visitTypeInsn(int opcode, String type) {}
                            @Override public void visitFieldInsn(int opcode, String owner, String name2, String desc) {}
                            @Override public void visitMethodInsn(int opcode, String owner, String name2, String desc, boolean itf) {}
                            @Override public void visitJumpInsn(int opcode, com.cosmic.launcher.asm.Label label) {}
                            @Override public void visitTryCatchBlock(com.cosmic.launcher.asm.Label start, com.cosmic.launcher.asm.Label end, com.cosmic.launcher.asm.Label handler, String type) {}
                            @Override public void visitLabel(com.cosmic.launcher.asm.Label label) {}
                            @Override public void visitLineNumber(int line, com.cosmic.launcher.asm.Label start) {}
                            @Override public void visitLocalVariable(String name2, String desc, String sig, com.cosmic.launcher.asm.Label start, com.cosmic.launcher.asm.Label end, int idx) {}
                            @Override public void visitMaxs(int maxStack, int maxLocals) { super.visitMaxs(0, 0); }
                        };
                    }
                    return mv;
                }
            }, 0);
            return cw.toByteArray();
        }
        catch (Exception e) {
            System.err.println("[CosmicAgent] ERROR patching " + className + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}

