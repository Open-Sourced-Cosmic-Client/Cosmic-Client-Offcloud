package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.Label;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class CosmicGuardTransformer
implements ClassFileTransformer {
    private final String targetClass;
    private final String targetMethod;
    private final String targetDesc;

    public CosmicGuardTransformer(String targetClass, String targetMethod, String targetDesc) {
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.targetDesc = targetDesc;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        boolean matches = className.equals(this.targetClass)
                || className.equals(this.targetClass.replace("cosmicclient/", ""))
                || this.targetClass.equals(className.replace("cosmicclient/", ""));
        if (!matches) {
            return null;
        }
        System.out.println("[CosmicAgent] Intercepted class for guard bypass: " + className);
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 2);
            cr.accept(new ClassVisitor(589824, cw){

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals(CosmicGuardTransformer.this.targetMethod) && descriptor.equals(CosmicGuardTransformer.this.targetDesc)) {
                        System.out.println("[CosmicAgent] Patching " + CosmicGuardTransformer.this.targetClass + "." + name + descriptor);
                        return new MethodVisitor(589824, mv){
                            private boolean replaced;
                            {
                                this.replaced = false;
                            }

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitInsn(4);
                                this.mv.visitInsn(172);
                                this.replaced = true;
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (!this.replaced) {
                                    super.visitInsn(opcode);
                                }
                            }

                            @Override
                            public void visitIntInsn(int opcode, int operand) {
                                if (!this.replaced) {
                                    super.visitIntInsn(opcode, operand);
                                }
                            }

                            @Override
                            public void visitVarInsn(int opcode, int var) {
                                if (!this.replaced) {
                                    super.visitVarInsn(opcode, var);
                                }
                            }

                            @Override
                            public void visitTypeInsn(int opcode, String type) {
                                if (!this.replaced) {
                                    super.visitTypeInsn(opcode, type);
                                }
                            }

                            @Override
                            public void visitFieldInsn(int opcode, String owner, String name2, String descriptor2) {
                                if (!this.replaced) {
                                    super.visitFieldInsn(opcode, owner, name2, descriptor2);
                                }
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name2, String descriptor2, boolean isInterface) {
                                if (!this.replaced) {
                                    super.visitMethodInsn(opcode, owner, name2, descriptor2, isInterface);
                                }
                            }

                            @Override
                            public void visitJumpInsn(int opcode, Label label) {
                                if (!this.replaced) {
                                    super.visitJumpInsn(opcode, label);
                                }
                            }

                            @Override
                            public void visitLabel(Label label) {
                                if (!this.replaced) {
                                    super.visitLabel(label);
                                }
                            }

                            @Override
                            public void visitLdcInsn(Object value) {
                                if (!this.replaced) {
                                    super.visitLdcInsn(value);
                                }
                            }

                            @Override
                            public void visitIincInsn(int var, int increment) {
                                if (!this.replaced) {
                                    super.visitIincInsn(var, increment);
                                }
                            }

                            @Override
                            public void visitTableSwitchInsn(int min, int max, Label dflt, Label ... labels) {
                                if (!this.replaced) {
                                    super.visitTableSwitchInsn(min, max, dflt, labels);
                                }
                            }

                            @Override
                            public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                                if (!this.replaced) {
                                    super.visitLookupSwitchInsn(dflt, keys, labels);
                                }
                            }

                            @Override
                            public void visitMultiANewArrayInsn(String descriptor2, int numDimensions) {
                                if (!this.replaced) {
                                    super.visitMultiANewArrayInsn(descriptor2, numDimensions);
                                }
                            }

                            @Override
                            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                                if (!this.replaced) {
                                    super.visitTryCatchBlock(start, end, handler, type);
                                }
                            }

                            @Override
                            public void visitLocalVariable(String name2, String descriptor2, String signature2, Label start, Label end, int index) {
                            }

                            @Override
                            public void visitLineNumber(int line, Label start) {
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                super.visitMaxs(0, 0);
                            }

                            @Override
                            public void visitEnd() {
                                super.visitEnd();
                                System.out.println("[CosmicAgent] Patched " + CosmicGuardTransformer.this.targetClass + "." + CosmicGuardTransformer.this.targetMethod + CosmicGuardTransformer.this.targetDesc + " -> return true");
                            }
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

