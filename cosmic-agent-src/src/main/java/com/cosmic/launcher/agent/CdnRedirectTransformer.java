package com.cosmic.launcher.agent;

import com.cosmic.launcher.asm.ClassReader;
import com.cosmic.launcher.asm.ClassVisitor;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class CdnRedirectTransformer implements ClassFileTransformer {
    static final String ORIGINAL_ASSETS_URL = "https://cdn.direct.cosmicclient.com/assets/1.8.json?";
    static final String PROP_ASSETS_URL = "cosmic.assets.url";

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!"com/cosmicclient/launcher/App".equals(className)) {
            return null;
        }
        final String replacement = System.getProperty(PROP_ASSETS_URL);
        if (replacement == null || replacement.isEmpty()) {
            System.out.println("[CosmicAgent] CdnRedirect: no cosmic.assets.url set, leaving original URL");
            return null;
        }
        System.out.println("[CosmicAgent] CdnRedirect: patching App.class");
        System.out.println("[CosmicAgent] CdnRedirect:   https://cdn.direct.cosmicclient.com/assets/1.8.json?");
        System.out.println("[CosmicAgent] CdnRedirect: -> " + replacement);
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 0);
            cr.accept(new ClassVisitor(589824, cw) {

                @Override
                public MethodVisitor visitMethod(int access, final String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(589824, mv) {

                        @Override
                        public void visitLdcInsn(Object value) {
                            if (CdnRedirectTransformer.ORIGINAL_ASSETS_URL.equals(value)) {
                                System.out.println("[CosmicAgent] CdnRedirect: replaced in " + name);
                                super.visitLdcInsn(replacement);
                            } else {
                                super.visitLdcInsn(value);
                            }
                        }
                    };
                }
            }, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            System.err.println("[CosmicAgent] CdnRedirect ERROR: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
