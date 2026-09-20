package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class VoiceTickTransformer
implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || !className.equals("cosmicclient/wb")) {
            return null;
        }
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 1);
            cr.accept(new ClassVisitor(589824, cw){

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (name.equals("B") && descriptor.equals("(JI)V")) {
                        return new MethodVisitor(589824, mv){

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(25, 0);
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/VoiceAudioEngine", "tick", "(Ljava/lang/Object;)V", false);
                                super.visitVarInsn(25, 0);
                                super.visitMethodInsn(184, "com/cosmic/launcher/agent/InGameMenuHelper", "handleInGameTick", "(Ljava/lang/Object;)V", false);
                            }
                        };
                    }
                    return mv;
                }
            }, 8);
            return cw.toByteArray();
        }
        catch (Exception e) {
            System.err.println("[CosmicAgent/Voice] ERROR hooking wb.B: " + e.getMessage());
            return null;
        }
    }
}

