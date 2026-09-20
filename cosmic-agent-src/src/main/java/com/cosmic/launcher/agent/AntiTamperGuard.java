package com.cosmic.launcher.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 2026 Anti-Cheat & Anti-Tamper Hardening Engine.
 * Protects Cosmic Client against:
 * 1. Ghost client & aimassist injection (DLL/Java agent dynamic attach).
 * 2. Unauthorized bytecode manipulation of combat, reach, and movement classes.
 * 3. AI / ML / script-based runtime hooks and external class tampering.
 */
public class AntiTamperGuard implements ClassFileTransformer {

    private static final String[] TRUSTED_PREFIXES = new String[] {
        "java/",
        "javax/",
        "sun/",
        "com/sun/",
        "jdk/",
        "org/w3c/",
        "org/xml/",
        "org/ietf/",
        "net/minecraft/",
        "cosmicclient/",
        "com/cosmic/",
        "com/google/",
        "org/apache/",
        "io/netty/",
        "org/lwjgl/",
        "paulscode/",
        "org/spongepowered/",
        "com/mojang/",
        "gnu/trove/",
        "org/objectweb/asm/",
        "com/ibm/icu/",
        "com/sun/jna/"
    };

    private static final String[] ROGUE_FRAMEWORKS = new String[] {
        "bytebuddy/agent",
        "javassist/bytecode",
        "kawatun"
    };

    private static final String[] CHEAT_BRANDS = new String[] {
        "vape",
        "drip",
        "slinky",
        "raven",
        "liquidbounce",
        "doomsday",
        "futureclient",
        "ghostclient",
        "keystrokesmod"
    };

    private static final String[] CHEAT_MODULES = new String[] {
        "reach",
        "velocity",
        "hitbox",
        "fastplace",
        "wtap",
        "w-tap",
        "killaura",
        "aimassist",
        "autoclicker"
    };

    private static final Set<String> CRITICAL_COMBAT_CLASSES = new HashSet<>(Arrays.asList(
        "net/minecraft/client/entity/EntityPlayerSP",
        "net/minecraft/client/multiplayer/PlayerControllerMP",
        "net/minecraft/client/renderer/EntityRenderer",
        "net/minecraft/client/settings/KeyBinding",
        "net/minecraft/client/Minecraft",
        "cosmicclient/wb",
        "cosmicclient/aP",
        "cosmicclient/uA"
    ));

    private static volatile boolean active = false;
    private static Instrumentation instrumentation;

    public static boolean isTrusted(String className) {
        if (className == null || className.isEmpty()) return true;
        String n = className.replace('.', '/').toLowerCase();

        // Obfuscated root classes in Minecraft 1.8.9 (e.g. ao, wb, kN, aPf, Ea, Ev, Es)
        if (!n.contains("/") && n.length() <= 4) {
            return true;
        }

        for (String prefix : TRUSTED_PREFIXES) {
            if (n.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSecurityViolation(String className) {
        if (className == null || isTrusted(className)) {
            return false;
        }

        String n = className.replace('.', '/').toLowerCase();

        // 1. Rogue injection / bytecode agent frameworks
        for (String fw : ROGUE_FRAMEWORKS) {
            if (n.contains(fw)) {
                return true;
            }
        }

        // 2. Known ghost client packages & namespaces
        for (String brand : CHEAT_BRANDS) {
            if (n.equals(brand) || n.startsWith(brand + "/") || n.contains("/" + brand + "/") || n.endsWith("/" + brand)) {
                return true;
            }
            if (brand.equals("liquidbounce") || brand.equals("doomsday") || brand.equals("futureclient") ||
                brand.equals("ghostclient") || brand.equals("keystrokesmod")) {
                if (n.contains(brand)) {
                    return true;
                }
            }
        }

        // 3. Known combat / movement cheat modules (matched on package segments)
        for (String mod : CHEAT_MODULES) {
            if (n.contains("/" + mod + "/") || n.startsWith(mod + "/")) {
                return true;
            }
        }

        // 4. Exact simple class name matching for cheat modules
        int lastSlash = n.lastIndexOf('/');
        String simpleName = lastSlash >= 0 ? n.substring(lastSlash + 1) : n;
        int dollar = simpleName.indexOf('$');
        if (dollar >= 0) {
            simpleName = simpleName.substring(0, dollar);
        }

        for (String mod : CHEAT_MODULES) {
            if (simpleName.equals(mod) || simpleName.equals(mod + "module") ||
                simpleName.equals(mod + "mod") || simpleName.equals(mod + "cheat")) {
                return true;
            }
        }

        return false;
    }

    public static void initialize(Instrumentation inst) {
        if (active) return;
        active = true;
        instrumentation = inst;

        // 1. Lock down JVM attachment mechanism
        try {
            System.setProperty("jdk.attach.allowAttachSelf", "false");
            System.setProperty("sun.tools.attach.enable", "false");
            System.setProperty("cosmic.anti_tamper", "active");
        } catch (Throwable ignored) {}

        // 2. Register anti-tamper bytecode transformer
        try {
            inst.addTransformer(new AntiTamperGuard(), true);
        } catch (Throwable ignored) {}

        // 3. Start high-priority anti-cheat watchdog daemon
        startIntegrityWatchdog();

        System.out.println("[CosmicGuard] Anti-Tamper & Anti-Ghost-Client Engine active.");
    }

    private static void startIntegrityWatchdog() {
        Thread watchdog = new Thread(() -> {
            while (active) {
                try {
                    Thread.sleep(3500L);
                    performSecurityScan();
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable ignored) {}
            }
        }, "CosmicGuard-Watchdog");
        watchdog.setDaemon(true);
        watchdog.setPriority(Thread.MAX_PRIORITY);
        watchdog.start();
    }

    public static void performSecurityScan() {
        if (instrumentation == null) return;
        try {
            Class<?>[] loaded = instrumentation.getAllLoadedClasses();
            if (loaded == null) return;

            for (Class<?> clazz : loaded) {
                if (clazz == null) continue;
                String name = clazz.getName();
                if (isSecurityViolation(name)) {
                    System.err.println("[CosmicGuard] SECURITY ALERT: Rogue module blocked: " + clazz.getName());
                    ClientHardeningGuard.trap(0x77);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null) return null;

        // 1. Block known ghost client & cheat injectors
        if (isSecurityViolation(className)) {
            System.err.println("[CosmicGuard] Blocked unauthorized class definition: " + className);
            ClientHardeningGuard.trap(0x78);
            throw new SecurityException("CosmicGuard: Access denied for " + className);
        }

        // 2. Protect critical combat and movement classes against unauthorized foreign retransformation
        if (classBeingRedefined != null && CRITICAL_COMBAT_CLASSES.contains(className)) {
            ClassLoader agentLoader = AntiTamperGuard.class.getClassLoader();
            if (loader != null && loader != agentLoader && loader != ClassLoader.getSystemClassLoader()) {
                System.err.println("[CosmicGuard] Blocked unauthorized redefinition attempt on: " + className);
                throw new SecurityException("CosmicGuard: Redefinition locked for " + className);
            }
        }

        return null;
    }
}
