package com.cosmic.launcher.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

public class CosmicAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[CosmicAgent] Loaded");

        // 0. Native display buffer alignment, memory coordination & anti-tamper hardening
        try {
            BufferPipelineManager.initializeDevicePipeline(inst);
            inst.addTransformer(new HardeningClassTransformer(), true);
            AntiTamperGuard.initialize(inst);
        } catch (Throwable ignored) {
        }

        // 1. Ensure Agent classes are available to bootstrap classloader
        try {
            File agentJar = new File(CosmicAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            inst.appendToBootstrapClassLoaderSearch(new JarFile(agentJar));
        } catch (Throwable ignored) {
        }

        // 2. Hide -javaagent argument from RuntimeMXBean
        inst.addTransformer(new RuntimeImplTransformer(), true);
        try {
            inst.retransformClasses(Class.forName("sun.management.RuntimeImpl"));
        } catch (Throwable ignored) {
        }

        // 3. Client & Anti-cheat integrity bypasses + Audio crash protection
        inst.addTransformer(new UnpackTransformer(), true);
        inst.addTransformer(new VoiceRecorderFixTransformer(), true);
        inst.addTransformer(new JcefCheckTransformer(), true);
        inst.addTransformer(new CosmicGuardTransformer("cosmicclient/bu", "a", "(BLjava/net/SocketAddress;J)Z"), true);
        inst.addTransformer(new PacketSanitizerTransformer(), true);
        inst.addTransformer(new CdnRedirectTransformer());
        inst.addTransformer(new CreativeSearchFixTransformer());
        inst.addTransformer(new MiddleClickFixTransformer());

        // 4. 100% Off-Cloud Auth & CDN Interceptor (instant 200 OK for authentication.php and assets)
        inst.addTransformer(new AuthInterceptorTransformer(), true);
        inst.addTransformer(new InGameLoginTransformer(), true);

        // 5. Window title cosmetic rebrand
        inst.addTransformer(new RebrandTransformer());

        // 6. Main menu & In-Game Shift menu local asset interception + Official Gamemodes removal
        inst.addTransformer(new MainMenuTransformer());
        inst.addTransformer(new GamemodeTransformer());

        // 7. Voice chat & Plugin Channel system integration
        inst.addTransformer(new VoiceTickTransformer());
        inst.addTransformer(new PluginChannelTransformer());

        // 8. In-Client 2026 Performance & FastMath Optimization Engine
        inst.addTransformer(new FastMathTransformer(), true);
        inst.addTransformer(new InGameOptimizerTransformer(), true);
        // Note: DisplayTransformer disabled to prevent OpenGL state contention during splash loading

        try {
            FpsBoosterHelper.init();
        } catch (Throwable ignored) {
        }

        // 9. Window & Taskbar Icon Watcher (Applies authentic 3-crystals cosmic.ico)
        try {
            IconHelper.startWindowIconWatcher();
        } catch (Throwable ignored) {
        }
    }
}
