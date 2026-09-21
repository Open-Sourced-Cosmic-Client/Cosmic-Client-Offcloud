package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.Handle;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.net.URI;
import java.security.ProtectionDomain;

public class RebrandTransformer implements ClassFileTransformer {
    public RebrandTransformer() {
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        if (className.equals("cosmicclient/wb")) {
            return this.patchWithRebrand(classfileBuffer, "wb");
        }
        if (className.equals("cosmicclient/Aw")) {
            return this.patchAwUrlOpener(classfileBuffer);
        }
        return null;
    }

    private byte[] patchWithRebrand(byte[] classfileBuffer, final String label) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(589824, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name2, String descriptor2, boolean isInterface) {
                            if (owner.equals("org/lwjgl/opengl/Display") && name2.equals("setTitle")) {
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/ServerRedirectHelper", "rebrand", "(Ljava/lang/String;)Ljava/lang/String;", false);
                            }
                            if (owner.equals("java/awt/Desktop") && name2.equals("browse")) {
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/RebrandTransformer", "rebrandURI", "(Ljava/net/URI;)Ljava/net/URI;", false);
                            }
                            super.visitMethodInsn(opcode, owner, name2, descriptor2, isInterface);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String name2, String descriptor2, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                            super.visitInvokeDynamicInsn(name2, descriptor2, bootstrapMethodHandle, bootstrapMethodArguments);
                            if (!label.equals("wb") && descriptor2.endsWith(")Ljava/lang/String;")) {
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/ServerRedirectHelper", "rebrand", "(Ljava/lang/String;)Ljava/lang/String;", false);
                            }
                        }
                    };
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched " + label + " - rebrand");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] Rebrand " + label + " failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchAwUrlOpener(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("c") && descriptor.equals("(IILjava/lang/String;)Z")) {
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitVarInsn(25, 2);
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/ServerRedirectHelper", "rebrand", "(Ljava/lang/String;)Ljava/lang/String;", false);
                                this.mv.visitVarInsn(58, 2);
                            }
                        };
                    }
                    if (name.equals("f") && descriptor.startsWith("(Ljava/lang/String;")) {
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitVarInsn(25, 0);
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/ServerRedirectHelper", "rebrand", "(Ljava/lang/String;)Ljava/lang/String;", false);
                                this.mv.visitVarInsn(58, 0);
                            }
                        };
                    }
                    if (name.equals("e") && descriptor.equals("(ILjava/lang/String;CS)Z")) {
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitVarInsn(25, 1);
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/ServerRedirectHelper", "rebrand", "(Ljava/lang/String;)Ljava/lang/String;", false);
                                this.mv.visitVarInsn(58, 1);
                            }
                        };
                    }
                    if (name.equals("a") && descriptor.equals("(Ljava/lang/String;SJ)Z")) {
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitVarInsn(25, 0);
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/ServerRedirectHelper", "rebrand", "(Ljava/lang/String;)Ljava/lang/String;", false);
                                this.mv.visitVarInsn(58, 0);
                            }
                        };
                    }
                    return mv;
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched Aw - URL opener rebrand (method entry injection)");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] Aw patch failed: " + e.getMessage());
            return null;
        }
    }

    public static URI rebrandURI(URI uri) {
        if (uri == null) {
            return null;
        }
        String original = uri.toString();
        String rebranded = ServerRedirectHelper.rebrandURI(original);
        if (!rebranded.equals(original)) {
            System.out.println("[CosmicAgent] Rebranded URI: " + original + " -> " + rebranded);
            return URI.create(rebranded);
        }
        return uri;
    }
}
