package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.Label;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Neutralizes console spam and lag caused by:
 * 1. Unknown armor set enums (e.g. CONQUEROR) throwing IllegalArgumentException in uL.valueOf
 * 2. Missing/Invalid HMAC exceptions in TD token validation on offline/custom servers
 * 3. Packets queued before session key is negotiated in HO and Hp
 * 4. Obsolete/broken forum RSS XML parser in ajR
 */
public class PacketSanitizerTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) return null;

        if (className.equals("cosmicclient/uL") || className.equals("uL")) {
            return patchUL(classfileBuffer);
        }
        if (className.equals("cosmicclient/TD") || className.equals("TD")) {
            return patchTD(classfileBuffer);
        }
        if (className.equals("cosmicclient/HO") || className.equals("HO")) {
            return patchHO(classfileBuffer);
        }
        if (className.equals("cosmicclient/Hp") || className.equals("Hp")) {
            return patchHp(classfileBuffer);
        }
        if (className.equals("cosmicclient/ajR") || className.equals("ajR")) {
            return patchAjR(classfileBuffer);
        }
        if (className.equals("cosmicclient/YX") || className.equals("YX")) {
            return patchYX(classfileBuffer);
        }

        return null;
    }

    private byte[] patchUL(byte[] buffer) {
        try {
            ClassReader cr = new ClassReader(buffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // 1. In uL.valueOf(String): redirect Enum.valueOf to PacketSanitizerHelper.safeValueOf
                    if (name.equals("valueOf") && descriptor.equals("(Ljava/lang/String;)Lcosmicclient/uL;")) {
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean itf) {
                                if (opcode == 184 && owner.equals("java/lang/Enum") && mName.equals("valueOf")) {
                                    super.visitMethodInsn(184, "com/cosmic/launcher/agent/PacketSanitizerHelper", "safeValueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;", false);
                                    return;
                                }
                                super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                            }
                        };
                    }

                    // 2. In all other methods of uL (such as uL.a): suppress printStackTrace() calls
                    return new MethodVisitor(589824, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean itf) {
                            if (opcode == 182 && mName.equals("printStackTrace") && mDesc.equals("()V") &&
                                (owner.equals("java/lang/Exception") || owner.equals("java/lang/Throwable"))) {
                                super.visitInsn(87); // POP exception off stack
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                        }
                    };
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched uL - Armor set enum sanitizer active (safe valueOf + silenced printStackTrace)");
            return cw.toByteArray();
        } catch (Throwable e) {
            System.err.println("[CosmicAgent] uL patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchTD(byte[] buffer) {
        try {
            ClassReader cr = new ClassReader(buffer);
            ClassWriter cw = new ClassWriter(cr, 2);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    // Token validation methods that throw "Missing / Invalid hmac"
                    if (name.equals("a") && (descriptor.equals("(ICC)V") || descriptor.equals("(BZJ)V"))) {
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
            System.out.println("[CosmicAgent] Patched TD - Token HMAC verification neutralized for offline/custom servers");
            return cw.toByteArray();
        } catch (Throwable e) {
            System.err.println("[CosmicAgent] TD patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchHO(byte[] buffer) {
        try {
            ClassReader cr = new ClassReader(buffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(589824, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean itf) {
                            if (opcode == 182 && mName.equals("printStackTrace") && mDesc.equals("()V") &&
                                (owner.equals("java/lang/Exception") || owner.equals("java/lang/Throwable"))) {
                                super.visitInsn(87); // POP exception
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                        }
                    };
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched HO - Outgoing packet send exceptions silenced");
            return cw.toByteArray();
        } catch (Throwable e) {
            System.err.println("[CosmicAgent] HO patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchHp(byte[] buffer) {
        try {
            ClassReader cr = new ClassReader(buffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(589824, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean itf) {
                            if (opcode == 182 && mName.equals("printStackTrace") && mDesc.equals("()V") &&
                                (owner.equals("java/lang/Exception") || owner.equals("java/lang/Throwable"))) {
                                super.visitInsn(87); // POP exception
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                        }
                    };
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched Hp - Incoming packet dispatch exceptions silenced");
            return cw.toByteArray();
        } catch (Throwable e) {
            System.err.println("[CosmicAgent] Hp patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchAjR(byte[] buffer) {
        try {
            ClassReader cr = new ClassReader(buffer);
            ClassWriter cw = new ClassWriter(cr, 2);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("run") && descriptor.equals("()V")) {
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
            System.out.println("[CosmicAgent] Patched ajR - Discarded obsolete online forum RSS fetcher");
            return cw.toByteArray();
        } catch (Throwable e) {
            System.err.println("[CosmicAgent] ajR patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchYX(byte[] buffer) {
        try {
            ClassReader cr = new ClassReader(buffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(589824, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean itf) {
                            // 1. Intercept ug.i().z() to prevent NPE during early handshake token generation
                            if (opcode == 184 && owner.equals("cosmicclient/ug") && mName.equals("i") && mDesc.equals("()Lcosmicclient/ug;")) {
                                return; // Skip pushing ug singleton (stack retains target TD)
                            }
                            if (opcode == 182 && owner.equals("cosmicclient/ug") && mName.equals("z") && mDesc.equals("()Ljava/lang/String;")) {
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/PacketSanitizerHelper", "getSafeCosmicVersion", "()Ljava/lang/String;", false);
                                return;
                            }
                            // 2. Silence log4j Logger.catching calls in YX background worker
                            if (opcode == 185 && owner.equals("org/apache/logging/log4j/Logger") && mName.equals("catching")) {
                                super.visitInsn(87); // pop Throwable
                                super.visitInsn(87); // pop Level
                                super.visitInsn(87); // pop Logger
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                        }
                    };
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched YX - Handshake token manager immunized against early NPE");
            return cw.toByteArray();
        } catch (Throwable e) {
            System.err.println("[CosmicAgent] YX patch failed: " + e.getMessage());
            return null;
        }
    }
}
