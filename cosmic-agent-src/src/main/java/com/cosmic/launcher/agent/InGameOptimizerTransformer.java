package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import com.cosmic.launcher.asm.Opcodes;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * In-game client performance transformer:
 * - Injects memory pressure watcher into Minecraft main game loop
 * - Hooks unfocused FPS throttle to avoid GPU waste when tabbed out
 */
public class InGameOptimizerTransformer implements ClassFileTransformer, Opcodes {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) return null;

        // Minecraft class is "ave" in 1.8.9 obfuscation or "net/minecraft/client/Minecraft"
        if (className.equals("ave") || className.equals("net/minecraft/client/Minecraft")) {
            return patchMinecraftLoop(classfileBuffer, className);
        }

        return null;
    }

    private byte[] patchMinecraftLoop(byte[] classfileBuffer, String className) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);

            cr.accept(new ClassVisitor(ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // Hook runTick (s or runTick) to periodically check memory
                    if ((name.equals("s") || name.equals("runTick")) && descriptor.equals("()V")) {
                        return new MethodVisitor(ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                // Inject FpsBoosterHelper.checkMemoryPressure();
                                mv.visitMethodInsn(INVOKESTATIC, "com/cosmic/launcher/agent/FpsBoosterHelper", "checkMemoryPressure", "()V", false);
                            }
                        };
                    }

                    return mv;
                }
            }, 0);

            System.out.println("[CosmicAgent] Injected in-game performance engine into " + className);
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] InGameOptimizerTransformer failed: " + t.getMessage());
            return null;
        }
    }
}
