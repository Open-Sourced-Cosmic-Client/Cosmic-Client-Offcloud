package com.cosmic.launcher.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

public class CosmicAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[CosmicAgent] Loaded");

        try {
            File agentJar = new File(CosmicAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            inst.appendToBootstrapClassLoaderSearch(new JarFile(agentJar));
        } catch (Throwable ignored) {
        }

        inst.addTransformer(new RuntimeImplTransformer(), true);
        try {
            inst.retransformClasses(Class.forName("sun.management.RuntimeImpl"));
        } catch (Throwable ignored) {
        }

        inst.addTransformer(new JcefCheckTransformer(), true);
        inst.addTransformer(new CosmicGuardTransformer("cosmicclient/bu", "a", "(BLjava/net/SocketAddress;J)Z"), true);
        inst.addTransformer(new CdnRedirectTransformer());
        inst.addTransformer(new CreativeSearchFixTransformer());
        inst.addTransformer(new MiddleClickFixTransformer());
        inst.addTransformer(new AuthInterceptorTransformer(), true);
        inst.addTransformer(new RebrandTransformer());
        inst.addTransformer(new MainMenuTransformer());
        inst.addTransformer(new VoiceTickTransformer());
        inst.addTransformer(new PluginChannelTransformer());
        inst.addTransformer(new FastMathTransformer(), true);
        inst.addTransformer(new InGameOptimizerTransformer(), true);

        try {
            FpsBoosterHelper.init();
        } catch (Throwable ignored) {
        }
    }
}
