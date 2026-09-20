package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class UnpackTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) return null;
        if (className.equals("aL")) {
            try {
                ClassReader cr = new ClassReader(classfileBuffer);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
                cr.accept(new ClassVisitor(589824, cw) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (name.equals("a") && descriptor.equals("(Ljava/nio/file/Path;Ljava/nio/ByteBuffer;)V")) {
                            return new MethodVisitor(589824, mv) {
                                @Override
                                public void visitCode() {
                                    super.visitCode();
                                    this.mv.visitVarInsn(25, 0); // Path
                                    this.mv.visitVarInsn(25, 1); // ByteBuffer
                                    this.mv.visitMethodInsn(184, "com/cosmic/launcher/agent/UnpackInterceptor", "onUnpack", "(Ljava/nio/file/Path;Ljava/nio/ByteBuffer;)V", false);
                                }
                            };
                        }
                        return mv;
                    }
                }, 0);
                System.out.println("[CosmicAgent] Patched aL.a to intercept unpacked client JAR");
                return cw.toByteArray();
            } catch (Throwable t) {
                System.err.println("[CosmicAgent] Failed to patch aL: " + t.getMessage());
            }
        }
        return null;
    }
}
