package com.cosmic.launcher.agent;

import com.cosmic.launcher.agent.ServerRedirectHelper;
import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.Handle;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.net.URI;
import java.security.ProtectionDomain;

public class RebrandTransformer
implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        if (className.equals("cosmicclient/wb")) {
            return this.patchWithRebrand(classfileBuffer, "wb");
        }
        if (className.equals("org/lwjgl/opengl/Display") || className.equals("org/lwjglx/opengl/Display")) {
            return this.patchDisplay(classfileBuffer);
        }
        if (className.equals("cosmicclient/Aw")) {
            return this.patchAwUrlOpener(classfileBuffer);
        }
        if (className.equals("fH") || className.equals("cosmicclient/fH") ||
            className.equals("in") || className.equals("cosmicclient/in") ||
            className.equals("lb") || className.equals("cosmicclient/lb") ||
            className.equals("lj") || className.equals("cosmicclient/lj") ||
            className.equals("n8") || className.equals("cosmicclient/n8") ||
            className.equals("nP") || className.equals("cosmicclient/nP") ||
            className.equals("bh") || className.equals("cosmicclient/bh") ||
            className.equals("eW") || className.equals("cosmicclient/eW") ||
            className.equals("bc") || className.equals("bv") ||
            className.equals("jY") || className.equals("nJ")) {
            return this.patchDesktopBrowse(classfileBuffer, className);
        }
        return null;
    }

    private byte[] patchDisplay(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("setIcon") && descriptor.equals("([Ljava/nio/ByteBuffer;)I")) {
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitVarInsn(25, 0); // icons arg
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/IconHelper", "getOverrideIcons", "([Ljava/nio/ByteBuffer;)[Ljava/nio/ByteBuffer;", false);
                                this.mv.visitVarInsn(58, 0); // store back into icons
                            }
                        };
                    }
                    if (name.equals("create")) {
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == 177) { // RETURN
                                    this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/IconHelper", "applyWindowIcon", "()V", false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    return mv;
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched " + cr.getClassName() + " - 3-Crystals window icon hooked");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] Display patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchWithRebrand(byte[] classfileBuffer, final String label) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw){

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (label.equals("wb") && name.equals("G") && descriptor.equals("()Lcosmicclient/Yw;")) {
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == 176) { // ARETURN
                                    this.mv.visitVarInsn(25, 0); // ALOAD 0 (wb)
                                    this.mv.visitInsn(95);        // SWAP -> (wb, session)
                                    this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "ensureSessionNotNull", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
                                    this.mv.visitTypeInsn(192, "cosmicclient/Yw");
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    if (label.equals("wb") && name.equals("a") && descriptor.equals("(Lcosmicclient/Yw;)V")) {
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitVarInsn(25, 0); // ALOAD 0 (wb)
                                this.mv.visitVarInsn(25, 1); // ALOAD 1 (session)
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "ensureSessionNotNull", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
                                this.mv.visitTypeInsn(192, "cosmicclient/Yw");
                                this.mv.visitVarInsn(58, 1); // ASTORE 1 (session)
                            }
                        };
                    }
                    return new MethodVisitor(589824, mv){

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName, String desc, boolean itf) {
                            if ((owner.equals("org/lwjgl/opengl/Display") || owner.equals("org/lwjglx/opengl/Display")) && mName.equals("setTitle")) {
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/ServerRedirectHelper", "rebrand", "(Ljava/lang/String;)Ljava/lang/String;", false);
                            }
                            if (owner.equals("java/awt/Desktop") && mName.equals("browse")) {
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/RebrandTransformer", "safeBrowse", "(Ljava/awt/Desktop;Ljava/net/URI;)V", false);
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, mName, desc, itf);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String mName, String desc, Handle bsm, Object ... bsmArgs) {
                            super.visitInvokeDynamicInsn(mName, desc, bsm, bsmArgs);
                            if (!label.equals("wb") && desc.endsWith(")Ljava/lang/String;")) {
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/ServerRedirectHelper", "rebrand", "(Ljava/lang/String;)Ljava/lang/String;", false);
                            }
                        }
                    };
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched " + label + " - rebrand");
            return cw.toByteArray();
        }
        catch (Exception e) {
            System.err.println("[CosmicAgent] Rebrand " + label + " failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchAwUrlOpener(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw){

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("c") && descriptor.equals("(IILjava/lang/String;)Z")) {
                        return new MethodVisitor(589824, mv){

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
                        return new MethodVisitor(589824, mv){

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
                        return new MethodVisitor(589824, mv){

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
                        return new MethodVisitor(589824, mv){

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
        }
        catch (Exception e) {
            System.err.println("[CosmicAgent] Aw patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchDesktopBrowse(byte[] classfileBuffer, final String label) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(589824, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName, String desc, boolean itf) {
                            if (owner.equals("java/awt/Desktop") && mName.equals("browse")) {
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/RebrandTransformer", "safeBrowse", "(Ljava/awt/Desktop;Ljava/net/URI;)V", false);
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, mName, desc, itf);
                        }
                    };
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched " + label + " - safe Desktop.browse hooked");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] Desktop browse patch failed on " + label + ": " + e.getMessage());
            return null;
        }
    }

    public static void safeBrowse(java.awt.Desktop desktop, URI uri) {
        if (uri == null) return;
        String s = uri.toString();
        System.out.println("[CosmicAgent] In-game browse requested: " + s);
        String lower = s.toLowerCase();
        if (lower.contains("login.live.com") || lower.contains("microsoft.com") || lower.contains("xboxlive.com") || lower.contains("login.microsoftonline.com")) {
            if (lower.contains("/link") || lower.contains("link?otc=") || lower.contains("otc=")) {
                try {
                    if (desktop != null) {
                        desktop.browse(uri);
                        System.out.println("[CosmicAgent] Allowed device login browse: " + s);
                    }
                } catch (Throwable t) {
                    System.err.println("[CosmicAgent] Safe browse failed: " + t.getMessage());
                }
                return;
            }
            System.out.println("[CosmicAgent] Neutralized automatic in-game browser login popup: " + s);
            return;
        }
        try {
            if (desktop != null) {
                desktop.browse(uri);
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Safe browse failed: " + t.getMessage());
        }
    }

    public static URI rebrandURI(URI uri) {
        String r;
        if (uri == null) {
            return null;
        }
        String s = uri.toString();
        if (!s.equals(r = ServerRedirectHelper.rebrandURI(s))) {
            System.out.println("[CosmicAgent] Rebranded URI: " + s + " -> " + r);
            return URI.create(r);
        }
        return uri;
    }
}

