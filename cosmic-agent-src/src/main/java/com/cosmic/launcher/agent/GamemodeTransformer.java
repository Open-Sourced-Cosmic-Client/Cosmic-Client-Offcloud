package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.Label;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class GamemodeTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        if (className.equals("cosmicclient/v_") || className.equals("v_") || className.endsWith("/v_")) {
            return this.patchV_(loader, classfileBuffer);
        }
        if (className.equals("cosmicclient/Ea") || className.equals("Ea")) {
            return this.patchEa(classfileBuffer);
        }
        if (className.equals("cosmicclient/kN") || className.equals("kN")) {
            return this.patchKN(classfileBuffer);
        }
        if (className.equals("cosmicclient/E9") || className.equals("E9")) {
            return this.patchE9(classfileBuffer);
        }
        if (className.equals("cosmicclient/kR") || className.equals("kR")) {
            return this.patchKR(classfileBuffer);
        }
        if (className.equals("cosmicclient/Ev") || className.equals("Ev")) {
            return this.patchEv(classfileBuffer);
        }
        if (className.equals("cosmicclient/Es") || className.equals("Es")) {
            return this.patchEs(classfileBuffer);
        }
        if (className.equals("cosmicclient/Em") || className.equals("Em")) {
            return this.patchEm(classfileBuffer);
        }
        if (className.equals("cosmicclient/kW") || className.equals("kW")) {
            return this.patchKW(classfileBuffer);
        }
        if (className.equals("cosmicclient/kd") || className.equals("kd") || className.equals("cosmicclient/km") || className.equals("km")) {
            return this.patchKD(classfileBuffer);
        }
        if (className.equals("nn") || className.equals("cosmicclient/nn")) {
            return this.patchNN(classfileBuffer);
        }
        if (className.equals("fr") || className.equals("cosmicclient/fr") || className.endsWith("/fr")) {
            return this.patchFR(classfileBuffer);
        }
        if (className.equals("net/minecraft/client/j5") || className.equals("j5")) {
            return this.patchJ5(classfileBuffer);
        }
        if (className.equals("ao") || className.equals("cosmicclient/ao") || className.endsWith("/ao")) {
            return this.patchAO(classfileBuffer);
        }
        if (className.equals("net/minecraft/zQ") || className.equals("zQ")) {
            return this.patchZQ(classfileBuffer);
        }
        if (!className.startsWith("cosmicclient/")) {
            if (className.equals("net/minecraft/client/ko") || className.equals("ko") || className.endsWith("/ko")) {
                return this.patchKO(classfileBuffer);
            }
            if (className.equals("net/minecraft/client/dr") || className.equals("dr") || className.endsWith("/dr")) {
                return this.patchDR(classfileBuffer);
            }
            if (className.equals("net/minecraft/client/jR") || className.equals("jR") || className.endsWith("/jR")) {
                return this.patchJR(classfileBuffer);
            }
            if (className.equals("net/minecraft/client/jQ") || className.equals("jQ") || className.endsWith("/jQ")) {
                return this.patchJQ(classfileBuffer);
            }
            if (className.equals("net/minecraft/client/kh") || className.equals("kh") || className.endsWith("/kh")) {
                return this.patchKH(classfileBuffer);
            }
            if (className.equals("net/minecraft/client/jK") || className.equals("jK") || className.endsWith("/jK")) {
                return this.patchJK(classfileBuffer);
            }
            if (className.equals("net/minecraft/client/ld") || className.equals("ld") || className.endsWith("/ld")) {
                return this.patchLD(classfileBuffer);
            }
            if (className.equals("net/minecraft/client/gZ") || className.equals("gZ") || className.endsWith("/gZ")) {
                return this.patchGZ(classfileBuffer);
            }
            if (className.equals("net/minecraft/client/dp") || className.equals("dp") || className.endsWith("/dp")) {
                return this.patchDP(classfileBuffer);
            }
            if (className.equals("net/minecraft/client/fF") || className.equals("fF") || className.endsWith("/fF")) {
                return this.patchFF(classfileBuffer);
            }
            if (className.equals("net/minecraft/client/l") || className.equals("l") || className.endsWith("/l")) {
                return this.patchL(classfileBuffer);
            }
            if (className.equals("net/minecraft/client/l8") || className.equals("l8") || className.endsWith("/l8")) {
                return this.patchL8(classfileBuffer);
            }
            if (className.equals("net/minecraft/client/q") || className.equals("q") || className.endsWith("/q")) {
                return this.patchQ(classfileBuffer);
            }
            if (className.equals("net/minecraft/client/ly") || className.equals("ly") || className.endsWith("/ly")) {
                return this.patchLY(classfileBuffer);
            }
        }
        return null;

    }



    private byte[] patchEa(byte[] classfileBuffer) {
        try {
            try {
                new java.io.File("scratch").mkdirs();
                java.nio.file.Files.write(java.nio.file.Paths.get("scratch/Ea_dump.class"), classfileBuffer);
            } catch (Throwable ignored) {}
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

                    // 3. Kill Gamemode Header Banner: b(double, short, double, float, long) -> (DSDFJ)V
                    if (name.equals("b") && descriptor.equals("(DSDFJ)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(177); // RETURN immediately! Never draw OFFICIAL GAMEMODES banner!
                            mv.visitMaxs(0, 9);
                            mv.visitEnd();
                        }
                        return null;
                    }


                    // 4b. Kill Gamemode Card Container & Rendering: i(int, short, short) -> (ISS)V
                    if (name.equals("i") && descriptor.equals("(ISS)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(177); // RETURN immediately! Never compute offsets or query L.get(0)!
                            mv.visitMaxs(0, 4);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 5. Custom Nebula Background rendering method in Ea: a(int, int, double, short, double) -> (IIDSD)V
                    if (name.equals("a") && descriptor.equals("(IIDSD)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // this
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "renderCustomBackground", "(Ljava/lang/Object;)V", false);
                            mv.visitInsn(177); // RETURN immediately!
                            mv.visitMaxs(1, 7);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 6. Master drawScreen method in Ea: a(double, double, int, char, float, short) -> (DDICFS)V
                    if (name.equals("a") && descriptor.equals("(DDICFS)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitVarInsn(25, 0); // this
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "clearOfficialGamemodes", "(Ljava/lang/Object;)V", false);
                                this.mv.visitVarInsn(25, 0); // this
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "renderCustomBackground", "(Ljava/lang/Object;)V", false);
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/IconHelper", "applyWindowIcon", "()V", false);
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                // Neutralize "OFFICIAL GAMEMODES" text banner
                                if (name.equals("b") && descriptor.equals("(SLjava/lang/String;FICFII)F")) {
                                    super.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "suppressOfficialGamemodesText", "(Ljava/lang/Object;SLjava/lang/String;FICFII)F", false);
                                    return;
                                }
                                // Render authentic "Add Account" button text in accounts drawer
                                if (name.equals("b") && descriptor.equals("(Ljava/lang/String;DIDJI)F")) {
                                    super.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "renderAddAccountText", "(Ljava/lang/Object;Ljava/lang/String;DIDJI)F", false);
                                    return;
                                }
                                // Neutralize Forum ('ac') and Store ('C') drawing
                                if ((owner.equals("cosmicclient/kW") || owner.equals("kW")) && name.equals("a") && descriptor.equals("(DDICFS)V")) {
                                    super.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "suppressKW", "(Ljava/lang/Object;DDICFS)V", false);
                                    return;
                                }
                                // Neutralize Forum ('ac') and Store ('C') hover
                                if ((owner.equals("cosmicclient/kW") || owner.equals("kW")) && name.equals("c") && descriptor.equals("(DD)Z")) {
                                    super.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "suppressKWHover", "(Ljava/lang/Object;DD)Z", false);
                                    return;
                                }
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == 177) { // RETURN
                                    this.mv.visitVarInsn(25, 0); // this
                                    this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "renderCustomLogo", "(Ljava/lang/Object;)V", false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    // 7. Init method: l(int, byte, int) -> (IBI)V
                    if (name.equals("l") && descriptor.equals("(IBI)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitVarInsn(25, 0); // this
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "clearOfficialGamemodes", "(Ljava/lang/Object;)V", false);
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/IconHelper", "applyWindowIcon", "()V", false);
                            }
                        };
                    }

                    // 8. Mouse click method: a(double, double, int, long) -> (DDIJ)V
                    if (name.equals("a") && descriptor.equals("(DDIJ)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitVarInsn(25, 0); // this
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "clearOfficialGamemodes", "(Ljava/lang/Object;)V", false);
                                this.mv.visitVarInsn(25, 0); // this
                                this.mv.visitVarInsn(24, 1); // mouseX (double)
                                this.mv.visitVarInsn(24, 3); // mouseY (double)
                                this.mv.visitVarInsn(21, 5); // mouseButton (int)
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "handleMainMenuClick", "(Ljava/lang/Object;DDI)V", false);
                            }
                        };
                    }

                    // 8b. Mouse click method: a(long, int, double, double, int) -> (JIDDI)V
                    if (name.equals("a") && descriptor.equals("(JIDDI)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitVarInsn(25, 0); // ALOAD 0 (this eaInstance)
                                this.mv.visitVarInsn(24, 4); // DLOAD 4 (mouseX)
                                this.mv.visitVarInsn(24, 6); // DLOAD 6 (mouseY)
                                this.mv.visitVarInsn(21, 8); // ILOAD 8 (mouseButton)
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "handleMainMenuClick", "(Ljava/lang/Object;DDI)V", false);
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                if ((owner.equals("cosmicclient/kW") || owner.equals("kW")) && name.equals("a") && descriptor.equals("(JIDDI)V")) {
                                    super.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "suppressKWClick", "(Ljava/lang/Object;JIDDI)V", false);
                                    return;
                                }
                                if ((owner.equals("cosmicclient/kW") || owner.equals("kW")) && name.equals("c") && descriptor.equals("(DD)Z")) {
                                    super.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "suppressKWHover", "(Ljava/lang/Object;DD)Z", false);
                                    return;
                                }
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                            }
                        };
                    }

                    // Intercept account selection click in accounts drawer: b(short, int, cosmicclient.kN, int)
                    if (name.equals("b") && descriptor.contains("kN") && descriptor.contains("aPf")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // ALOAD 0 (this Ea)
                            mv.visitVarInsn(25, 3); // ALOAD 3 (kN kn)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "handleAccountSelectFromMenu", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
                            String returnType = descriptor.substring(descriptor.indexOf(")L") + 2, descriptor.length() - 1);
                            mv.visitTypeInsn(192, returnType);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(2, 5);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 9. Constructor: setup menu buttons (add Singleplayer)
                    if (name.equals("<init>") && descriptor.equals("()V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "ensureFullClientMode", "()V", false);
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == 177) { // RETURN
                                    this.mv.visitVarInsn(25, 0); // this
                                    this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "setupMainMenuButtons", "(Ljava/lang/Object;)V", false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched Ea - official gamemodes HARD REMOVED & custom nebula background enforced");
            byte[] transformed = cw.toByteArray();
            try {
                java.nio.file.Files.write(java.nio.file.Paths.get("scratch/Ea_transformed.class"), transformed);
            } catch (Throwable ignored) {}
            return transformed;
        } catch (Exception e) {
            System.err.println("[CosmicAgent] Ea patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchEG(byte[] classfileBuffer) {
        // EG is base GuiScreen; must remain unmodified for in-game menus
        return classfileBuffer;
    }

    private byte[] patchEv(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    // Default / dirt background method in Ev (GuiScreen): d(int, int, short, int) -> (IISI)V
                    if (name.equals("d") && descriptor.equals("(IISI)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // this
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "renderDefaultBackgroundHook", "(Ljava/lang/Object;)Z", false);
                            Label continueLabel = new Label();
                            mv.visitJumpInsn(153, continueLabel); // IFEQ -> if false, proceed with vanilla (in-game pause screen)
                            mv.visitInsn(177); // RETURN immediately (4K custom background already rendered)
                            mv.visitLabel(continueLabel);
                        }
                        return mv;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched Ev - default background hooked with custom 4K background");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] Ev patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchEs(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    // 1. Disable Mojang button click check in Es: a(double, double) -> always return false (0)
                    if (name.equals("a") && descriptor.equals("(DD)Z")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(3); // ICONST_0 (false)
                            mv.visitInsn(172); // IRETURN
                            mv.visitMaxs(1, 4);
                            return null;
                        }
                    }

                    // 2. Custom 4K background and status overlay in Es: a(double, double, int, char, float, short)
                    if (name.equals("a") && descriptor.equals("(DDICFS)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitVarInsn(25, 0); // this
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "configureEsScreen", "(Ljava/lang/Object;)V", false);
                                this.mv.visitVarInsn(25, 0); // this
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "renderCustomBackground", "(Ljava/lang/Object;)V", false);
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == 177) { // RETURN
                                    this.mv.visitVarInsn(25, 0); // this
                                    this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "renderEsOverlay", "(Ljava/lang/Object;)V", false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    // Disable 10-second spam/cooldown block in Es: c() -> always return false (0)
                    if (name.equals("c") && descriptor.equals("()Z")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(3); // ICONST_0 (false)
                            mv.visitInsn(172); // IRETURN
                            mv.visitMaxs(1, 0);
                            return null;
                        }
                    }

                    // 3. Intercept Microsoft button click in Es: a(long, int, double, double, int) -> (JIDDI)V
                    if (name.equals("a") && descriptor.equals("(JIDDI)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(25, 0); // ALOAD 0 (this)
                                super.visitVarInsn(24, 4); // DLOAD 4 (mouseX)
                                super.visitVarInsn(24, 6); // DLOAD 6 (mouseY)
                                super.visitVarInsn(21, 8); // ILOAD 8 (mouseButton)
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "handleEsClick", "(Ljava/lang/Object;DDI)V", false);
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name2, String desc, boolean itf) {
                                if ((owner.equals("cosmicclient/QA") || owner.equals("QA")) && name2.equals("d") && desc.equals("()V")) {
                                    // Discard QA on stack and invoke modern Microsoft Device Code flow
                                    super.visitInsn(87); // POP
                                    super.visitVarInsn(25, 0); // ALOAD 0 (this Es instance)
                                    super.visitMethodInsn(184, "com/cosmic/launcher/agent/InGameLoginHelper", "startMicrosoftDeviceLogin", "(Ljava/lang/Object;)V", false);
                                    return;
                                }
                                super.visitMethodInsn(opcode, owner, name2, desc, itf);
                            }
                        };
                    }

                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched Es - Microsoft login wired to device code flow, Mojang login disabled");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] Es patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchKN(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(589824, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name2, String desc, boolean itf) {
                            // Intercept equalsIgnoreCase in kN to support matching usernames and UUIDs with or without dashes
                            if (owner.equals("java/lang/String") && name2.equals("equalsIgnoreCase") && desc.equals("(Ljava/lang/String;)Z")) {
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "isAccountMatching", "(Ljava/lang/String;Ljava/lang/String;)Z", false);
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, name2, desc, itf);
                            // Intercept wb.G:()Lcosmicclient/Yw; and guarantee non-null / activeSession
                            if ((owner.equals("cosmicclient/wb") || owner.equals("wb")) && name2.equals("G") && (desc.equals("()Lcosmicclient/Yw;") || desc.equals("()LYw;"))) {
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "ensureSessionNotNullDirect", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                                String returnType = desc.substring(desc.indexOf(")L") + 2, desc.length() - 1);
                                super.visitTypeInsn(192, returnType);
                            }
                        }
                    };
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched kN - immunized wb.G() against NullPointerException");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] kN patch failed: " + e.getMessage());
            return null;
        }
    }



    private byte[] patchEm(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("l") && descriptor.equals("(IBI)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                this.mv.visitVarInsn(25, 0); // this
                                this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "suppressEmScreen", "(Ljava/lang/Object;)V", false);
                            }
                        };
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched Em - legacy Mojang screen suppressed");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] Em patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchE9(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ((name.equals("<init>") && descriptor.equals("()V")) || 
                        (name.equals("l") && descriptor.equals("(IBI)V"))) {
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == 177) { // RETURN
                                    this.mv.visitVarInsn(25, 0); // aload_0
                                    this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/ShiftMenuHelper", "cleanShiftMenu", "(Ljava/lang/Object;)V", false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    return mv;
                }
            }, 0);
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] E9 patch error: " + t.getMessage());
            return classfileBuffer;
        }
    }

    private byte[] patchKR(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("<init>")) {
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                                if (opcode == 181 && fieldName.equals("a") && fieldDescriptor.equals("Ljava/lang/String;")) { // PUTFIELD a
                                    this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/ShiftMenuHelper", "sanitizeCategoryName", "(Ljava/lang/String;)Ljava/lang/String;", false);
                                }
                                super.visitFieldInsn(opcode, owner, fieldName, fieldDescriptor);
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                                if (opcode == 184 && owner.contains("ImmutableList") && methodName.equals("copyOf")) {
                                    this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/ShiftMenuHelper", "filterSubcategoryArray", "([Ljava/lang/Object;)[Ljava/lang/Object;", false);
                                }
                                super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                            }
                        };
                    }
                    return mv;
                }
            }, 0);
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] kR patch error: " + t.getMessage());
            return classfileBuffer;
        }
    }

    private byte[] patchDf(byte[] classfileBuffer) {
        // Df is the UI box/panel drawing utility; must remain unmodified so mod cards and panels render.
        return classfileBuffer;
    }

    private byte[] patchKW(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals("<init>")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            if (descriptor.endsWith("V")) {
                                mv.visitInsn(177); // RETURN
                            } else if (descriptor.endsWith("Z") || descriptor.endsWith("I") || descriptor.endsWith("B") || descriptor.endsWith("C") || descriptor.endsWith("S")) {
                                mv.visitInsn(3); // ICONST_0
                                mv.visitInsn(172); // IRETURN
                            } else if (descriptor.endsWith("D")) {
                                mv.visitInsn(14); // DCONST_0
                                mv.visitInsn(175); // DRETURN
                            } else if (descriptor.endsWith("F")) {
                                mv.visitInsn(11); // FCONST_0
                                mv.visitInsn(174); // FRETURN
                            } else if (descriptor.endsWith("J")) {
                                mv.visitInsn(9); // LCONST_0
                                mv.visitInsn(173); // LRETURN
                            } else {
                                mv.visitInsn(1); // ACONST_NULL
                                mv.visitInsn(176); // ARETURN
                            }
                            mv.visitMaxs(2, 16);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched kW - Forum & Store buttons completely neutralized");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] kW patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchKD(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("b") && descriptor.equals("(IIB)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // ALOAD 0 (this kd instance)
                            mv.visitVarInsn(21, 1); // ILOAD 1 (i1)
                            mv.visitVarInsn(21, 2); // ILOAD 2 (i2)
                            mv.visitVarInsn(21, 3); // ILOAD 3 (b3)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "handleKdButtonClick", "(Ljava/lang/Object;IIB)V", false);
                            mv.visitInsn(177); // RETURN immediately
                            mv.visitMaxs(4, 4);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched kd - button click dispatched to MainMenuHelper");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] kd patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchNN(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            String superName = cr.getSuperName();
            final boolean is189AccountManager = superName != null && (superName.equals("nY") || superName.endsWith("/nY"));
            final boolean is112MainMenu = superName != null && (superName.contains("GuiScreen") || superName.endsWith("/Df") || superName.equals("Df")
                    || superName.endsWith("/Ev") || superName.equals("Ev")
                    || superName.endsWith("/EG") || superName.equals("EG")
                    || superName.endsWith("/bfl") || superName.equals("bfl"));

            if (!is189AccountManager && !is112MainMenu) {
                return null;
            }
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    // In 1.8.9, nn is the authentic Account Manager GUI.
                    // Hook nn.p(char, int, char) -> (CIC)V (initGui) to ensure accounts from accounts.json are synced into iR before rendering!
                    if (is189AccountManager) {
                        if (name.equals("p") && descriptor.equals("(CIC)V")) {
                            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                            return new MethodVisitor(589824, mv) {
                                @Override
                                public void visitCode() {
                                    super.visitCode();
                                    this.mv.visitVarInsn(25, 0); // ALOAD 0 (this nn instance)
                                    this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/InGameLoginHelper", "onNNInit", "(Ljava/lang/Object;)V", false);
                                }
                            };
                        }
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                    }

                    // 1. Hook nn.<init>()V -> call MainMenuHelper.initNN(this) before RETURN (for 1.12 fallback)
                    if (name.equals("<init>") && descriptor.equals("()V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return new MethodVisitor(589824, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == 177) { // RETURN
                                    super.visitVarInsn(25, 0); // ALOAD 0 (this)
                                    super.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "initNN", "(Ljava/lang/Object;)V", false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    // 2. Hook nn.a(IDJDF)V -> drawScreen: 4K bg + logo + buttons, then RETURN
                    if (name.equals("a") && descriptor.equals("(IDJDF)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // ALOAD 0 (this)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "renderCustomBackground", "(Ljava/lang/Object;)V", false);
                            mv.visitVarInsn(25, 0); // ALOAD 0 (this)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "renderCustomLogo", "(Ljava/lang/Object;)V", false);
                            mv.visitVarInsn(25, 0); // ALOAD 0 (this)
                            mv.visitVarInsn(24, 2); // DLOAD 2 (mouseX)
                            mv.visitVarInsn(24, 6); // DLOAD 6 (mouseY)
                            mv.visitVarInsn(23, 8); // FLOAD 8 (partialTicks)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "drawNNButtons", "(Ljava/lang/Object;DDF)V", false);
                            mv.visitInsn(177); // RETURN immediately! Never render gamemodes, store, or forums!
                            mv.visitMaxs(6, 9);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 3. Hook nn.b(DIDIJ)V -> mouseClicked: handle button click, then RETURN
                    if (name.equals("b") && descriptor.equals("(DIDIJ)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // ALOAD 0 (this)
                            mv.visitVarInsn(24, 1); // DLOAD 1 (mouseX)
                            mv.visitVarInsn(21, 3); // ILOAD 3 (p2)
                            mv.visitVarInsn(24, 4); // DLOAD 4 (mouseY)
                            mv.visitVarInsn(21, 6); // ILOAD 6 (mouseButton)
                            mv.visitVarInsn(22, 7); // LLOAD 7 (p5)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "handleNNClick", "(Ljava/lang/Object;DIDIJ)V", false);
                            mv.visitInsn(177); // RETURN immediately! Never trigger store/forum/gamemode clicks!
                            mv.visitMaxs(9, 9);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 4. Hook nn.p(CIC)V -> initGui / layout: layout buttons, then RETURN
                    if (name.equals("p") && descriptor.equals("(CIC)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // ALOAD 0 (this)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "layoutNNButtons", "(Ljava/lang/Object;)V", false);
                            mv.visitInsn(177); // RETURN
                            mv.visitMaxs(1, 4);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 5. Hook nn.a(IDSSD)V -> old background render method: RETURN immediately
                    if (name.equals("a") && descriptor.equals("(IDSSD)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(177); // RETURN immediately!
                            mv.visitMaxs(0, 7);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 6. Hook nn.c()Z -> auth check: return true
                    if (name.equals("c") && descriptor.equals("()Z")) {
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

                    // 7. Hook nn.a()V -> screen tick method: RETURN immediately (suppresses ticking ez.C)
                    if (name.equals("a") && descriptor.equals("()V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(177); // RETURN immediately
                            mv.visitMaxs(0, 1);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 8. Hook nn.a(double, short, short, int, double, int) -> (DSSIDI)V: mouse release/move, RETURN immediately!
                    if (name.equals("a") && descriptor.equals("(DSSIDI)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(177); // RETURN immediately!
                            mv.visitMaxs(0, 7);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 9. Hook nn.a(short, double, double, int, long, int, short) -> (SDDIJIS)V: mouse drag, RETURN immediately!
                    if (name.equals("a") && descriptor.equals("(SDDIJIS)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(177); // RETURN immediately!
                            mv.visitMaxs(0, 8);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 10. Hook nn.a(int, short, int, char, int) -> (ISICI)V: key typed, RETURN immediately!
                    if (name.equals("a") && descriptor.equals("(ISICI)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(177); // RETURN immediately!
                            mv.visitMaxs(0, 6);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    // 11. Hook nn.a(char, int, short, int) -> (CISI)V: mouse wheel, RETURN immediately!
                    if (name.equals("a") && descriptor.equals("(CISI)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(177); // RETURN immediately!
                            mv.visitMaxs(0, 5);
                            mv.visitEnd();
                        }
                        return null;
                    }

                    return super.visitMethod(access, name, descriptor, signature, exceptions);

                }
            }, 0);
            System.out.println("[CosmicAgent] Patched nn - Account Manager GUI initialized with account synchronization");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] nn patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchFR(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    // Hook fr.b(short, int, int) -> (SII)V: Quit Game action
                    if (name.equals("b") && descriptor.equals("(SII)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "quitGame", "()V", false);
                            mv.visitInsn(177); // RETURN
                            mv.visitMaxs(0, 4);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched fr - transformed to Quit Game action handler");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] fr patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchJ5(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    // Hook j5.c(int, int, long) -> (IIJ)V (drawBackground): render 4K space nebula background
                    if (name.equals("c") && descriptor.equals("(IIJ)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // ALOAD 0 (this)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "renderCustomBackground", "(Ljava/lang/Object;)V", false);
                            mv.visitInsn(177); // RETURN
                            mv.visitMaxs(1, 4);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    if (name.equals("f") && descriptor.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // lookup
                            mv.visitVarInsn(25, 1); // name
                            mv.visitVarInsn(25, 2); // type
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "safeBootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched j5 (GuiScreen) - 4K nebula background active for Singleplayer & Multiplayer");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] j5 patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchAO(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("run") && descriptor.equals("()V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(177); // RETURN
                            mv.visitMaxs(0, 1);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched ao (Forum RSS Worker) - safely neutralized run()V to pure offline return");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] ao patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchZQ(byte[] classfileBuffer) {

        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("a") && descriptor.equals("(Lnet/minecraft/nY;IICLnet/minecraft/s;)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(177); // RETURN immediately! Offline safe!
                            mv.visitMaxs(0, 5);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    if (name.equals("a") && descriptor.equals("(IILnet/minecraft/s;I)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(177); // RETURN immediately!
                            mv.visitMaxs(0, 4);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched zQ (Announcements / HTTP) - neutralized to pure offline");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] zQ patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchKO(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("c") && descriptor.equals("(IJ)Ljava/lang/String;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(21, 0); // ILOAD 0
                            mv.visitVarInsn(22, 1); // LLOAD 1
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "getKoString", "(IJ)Ljava/lang/String;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    if (name.equals("a") && descriptor.equals("(SJ)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // ALOAD 0
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "loadKoSaves", "(Ljava/lang/Object;)V", false);
                            mv.visitInsn(177); // RETURN
                            mv.visitMaxs(1, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/ko - string decryption & save loading mapped cleanly");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] KO patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchDR(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("a") && descriptor.equals("(IJ)Ljava/lang/String;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(21, 0); // ILOAD 0
                            mv.visitVarInsn(22, 1); // LLOAD 1
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "getDrString", "(IJ)Ljava/lang/String;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/dr - slot string decryption mapped cleanly");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] DR patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchJR(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("c") && descriptor.equals("(IJ)Ljava/lang/String;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(21, 0); // ILOAD 0
                            mv.visitVarInsn(22, 1); // LLOAD 1
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "getJRString", "(IJ)Ljava/lang/String;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    if (name.equals("d") && descriptor.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // lookup
                            mv.visitVarInsn(25, 1); // name
                            mv.visitVarInsn(25, 2); // type
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "safeBootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/jR (Multiplayer Screen) - clean string decryption & safe bootstrap");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] jR patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchJQ(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("c") && descriptor.equals("(IJ)Ljava/lang/String;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(21, 0); // ILOAD 0
                            mv.visitVarInsn(22, 1); // LLOAD 1
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "getJQString", "(IJ)Ljava/lang/String;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    if (name.equals("d") && descriptor.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // lookup
                            mv.visitVarInsn(25, 1); // name
                            mv.visitVarInsn(25, 2); // type
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "safeBootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/jQ (Options Screen) - clean string decryption & safe bootstrap");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] jQ patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchKH(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("c") && descriptor.equals("(IJ)Ljava/lang/String;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(21, 0); // ILOAD 0
                            mv.visitVarInsn(22, 1); // LLOAD 1
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "getKhString", "(IJ)Ljava/lang/String;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    if (name.equals("d") && descriptor.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // lookup
                            mv.visitVarInsn(25, 1); // name
                            mv.visitVarInsn(25, 2); // type
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "safeBootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/kh (Add Server) - clean string decryption & safe bootstrap");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] kh patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchJK(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("c") && descriptor.equals("(IJ)Ljava/lang/String;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(21, 0); // ILOAD 0
                            mv.visitVarInsn(22, 1); // LLOAD 1
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "getJKString", "(IJ)Ljava/lang/String;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    if (name.equals("d") && descriptor.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // lookup
                            mv.visitVarInsn(25, 1); // name
                            mv.visitVarInsn(25, 2); // type
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "safeBootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/jK (Direct Connect) - clean string decryption & safe bootstrap");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] jK patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchLD(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("a") && descriptor.equals("(SLjava/lang/String;IS[Ljava/lang/Object;)Ljava/lang/String;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 1); // ALOAD 1 (key)
                            mv.visitVarInsn(25, 4); // ALOAD 4 (args)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "formatI18n", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(2, 5);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/ld (I18n) - safe translation bridge");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] ld patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchGZ(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("a") && descriptor.equals("(IJ)Ljava/lang/String;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(21, 0); // ILOAD 0
                            mv.visitVarInsn(22, 1); // LLOAD 1
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "getGzString", "(IJ)Ljava/lang/String;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    if (name.equals("b") && descriptor.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // lookup
                            mv.visitVarInsn(25, 1); // name
                            mv.visitVarInsn(25, 2); // type
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "safeBootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/gZ (ServerList) - clean string decryption & safe bootstrap");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] gZ patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchDP(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("b") && descriptor.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // lookup
                            mv.visitVarInsn(25, 1); // name
                            mv.visitVarInsn(25, 2); // type
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "safeBootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/dp (ServerSelectionList) - safe bootstrap");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] dp patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchFF(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("b") && descriptor.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // lookup
                            mv.visitVarInsn(25, 1); // name
                            mv.visitVarInsn(25, 2); // type
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "safeBootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/fF (ServerListEntryNormal) - safe bootstrap");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] fF patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchL(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("b") && descriptor.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // lookup
                            mv.visitVarInsn(25, 1); // name
                            mv.visitVarInsn(25, 2); // type
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "safeBootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/l (ServerData) - safe bootstrap");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] l patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchL8(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("b") && descriptor.equals("(ISLnet/minecraft/client/j5;I)V")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // ALOAD 0 (this = mc)
                            mv.visitVarInsn(25, 3); // ALOAD 3 (targetScreen)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "displayGuiScreenClean", "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
                            mv.visitInsn(177); // RETURN
                            mv.visitMaxs(2, 5);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/l8 (Minecraft) - clean displayGuiScreen hooked");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] l8 patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchQ(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("c") && descriptor.equals("(ISCLnet/minecraft/client/lS;)Ljava/lang/String;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // ALOAD 0 (this = GameSettings)
                            mv.visitVarInsn(25, 4); // ALOAD 4 (options = EnumOptions)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "getKeyBinding", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(2, 5);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    if (name.equals("b") && descriptor.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // lookup
                            mv.visitVarInsn(25, 1); // name
                            mv.visitVarInsn(25, 2); // type
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "safeBootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/q (GameSettings) - clean getKeyBinding & safe bootstrap");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] q patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchLY(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("b") && descriptor.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // lookup
                            mv.visitVarInsn(25, 1); // name
                            mv.visitVarInsn(25, 2); // type
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/MainMenuHelper", "safeBootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
                            mv.visitInsn(176); // ARETURN
                            mv.visitMaxs(3, 3);
                            mv.visitEnd();
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched net/minecraft/client/ly (LanServerDetector) - safe bootstrap");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] ly patch failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] patchV_(final ClassLoader loader, byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    // Method h: public boolean h(long, char); descriptor: (JC)Z
                    // Token refresh & account switch entry point - invoke handleV_AccountSelect and return true
                    if (name.equals("h") && descriptor.equals("(JC)Z")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitVarInsn(25, 0); // ALOAD 0 (this v_)
                            mv.visitMethodInsn(184, "com/cosmic/launcher/agent/InGameLoginHelper", "handleV_AccountSelect", "(Ljava/lang/Object;)Z", false);
                            mv.visitInsn(172); // IRETURN
                            mv.visitMaxs(1, 4);
                            return null;
                        }
                    }

                    // Method j: private boolean j(long, short); descriptor: (JS)Z
                    // Microsoft token refresh - return true immediately without calling obsolete Xl.a endpoint
                    if (name.equals("j") && descriptor.equals("(JS)Z")) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            mv.visitCode();
                            mv.visitInsn(4); // ICONST_1 (true)
                            mv.visitInsn(172); // IRETURN
                            mv.visitMaxs(1, 4);
                            return null;
                        }
                    }

                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            System.out.println("[CosmicAgent] Patched cosmicclient/v_ - Account token refresh immunized against exceptions");
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] v_ patch failed: " + e.getMessage());
            return classfileBuffer;
        }
    }
}

