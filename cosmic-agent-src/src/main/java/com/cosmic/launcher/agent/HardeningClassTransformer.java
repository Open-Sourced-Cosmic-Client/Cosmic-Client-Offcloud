package com.cosmic.launcher.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * HardeningClassTransformer intercepts unauthorized class definitions,
 * foreign transformers, and rogue injection payloads with zero plain-text signatures.
 */
public class HardeningClassTransformer implements ClassFileTransformer {

    private static String d(byte[] b) {
        char[] c = new char[b.length];
        for (int i = 0; i < b.length; i++) {
            c[i] = (char) ((b[i] & 0xFF) ^ (0x5C + (i * 17)));
        }
        return new String(c);
    }

    private static final byte[][] BLOCKED_CLASS_BYTES = new byte[][]{
        new byte[]{(byte)0x8,(byte)0x1F,(byte)0x1F,(byte)0xE1,(byte)0xD3,(byte)0xD7,(byte)0xAD,(byte)0xA1,(byte)0x89,(byte)0xB8,(byte)0x67,(byte)0x79,(byte)0x49,(byte)0x5E,(byte)0x2F,(byte)0x29}, // TransformManager
        new byte[]{(byte)0x19,(byte)0x1E,(byte)0xD,(byte)0xEA,(byte)0xCE,(byte)0xC5,(byte)0xAB,(byte)0xB2,(byte)0x88,(byte)0xB7,(byte)0x6A,(byte)0x78,(byte)0x4A}, // EssentialBlob
        new byte[]{(byte)0x38,(byte)0x15,(byte)0x19,(byte)0xE6,(byte)0xFF,(byte)0xD0,(byte)0xB7,(byte)0xAB}, // dxgi_aux
        new byte[]{(byte)0x3D,(byte)0x6,(byte)0x17,(byte)0xFD,(byte)0xC1}, // akira
        new byte[]{(byte)0x2F,(byte)0x1,(byte)0x17,(byte)0xE1,(byte)0xCB,(byte)0xC8}, // slinky
        new byte[]{(byte)0x2A,(byte)0xC,(byte)0xE,(byte)0xEA}, // vape
        new byte[]{(byte)0x38,(byte)0x1F,(byte)0x17,(byte)0xFF} // drip
    };

    private static final byte[][] BLOCKED_PREFIX_BYTES = new byte[][]{
        new byte[]{(byte)0x3F,(byte)0x1F,(byte)0x1F,(byte)0xF5,(byte)0xD9,(byte)0x9E}, // crazy/
        new byte[]{(byte)0x3F,(byte)0x2,(byte)0x13,(byte)0xA0,(byte)0xC3,(byte)0xC3,(byte)0xA3,(byte)0xA9,(byte)0x9D,(byte)0xDA}, // com/crazy/
        new byte[]{(byte)0x32,(byte)0x8,(byte)0xA,(byte)0xA0,(byte)0xC3,(byte)0xC3,(byte)0xA3,(byte)0xA9,(byte)0x9D,(byte)0xDA}, // net/crazy/
        new byte[]{(byte)0x39,(byte)0x1E,(byte)0xD,(byte)0xEA,(byte)0xCE,(byte)0xC5,(byte)0xAB,(byte)0xB2,(byte)0x88,(byte)0xAA,(byte)0x64,(byte)0x7B,(byte)0x47,(byte)0x5B} // essential_blob
    };

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null || AntiTamperGuard.isTrusted(className)) {
            return null;
        }

        // Check for unauthorized injection frameworks
        for (byte[] b : BLOCKED_CLASS_BYTES) {
            String name = d(b);
            if (className.equalsIgnoreCase(name) || className.endsWith("/" + name) || className.contains("/" + name + "/")) {
                ClientHardeningGuard.trap(0x55);
                throw new SecurityException();
            }
        }

        for (byte[] b : BLOCKED_PREFIX_BYTES) {
            String prefix = d(b);
            if (className.startsWith(prefix)) {
                ClientHardeningGuard.trap(0x56);
                throw new SecurityException();
            }
        }

        return null;
    }
}
