package com.cosmic.launcher.agent;

/**
 * Lunar & Badlion style Performance & FPS Booster Engine.
 * Features:
 * - Unfocused / Background FPS Throttler (reduces GPU/CPU usage when tabbed out)
 * - Smart Memory Cleaner (flushes garbage and OpenGL resources during world switches)
 * - Hit Delay Optimizer (1.7/1.8 instant attack responsiveness)
 */
public final class FpsBoosterHelper {
    private static volatile boolean unfocusedFpsLimitEnabled = true;
    private static volatile int unfocusedFpsTarget = 30;
    private static volatile boolean hitDelayFixEnabled = true;
    private static volatile boolean memoryCleanerEnabled = true;
    private static long lastGcCheck = 0L;

    public static void init() {
        System.out.println("[CosmicAgent] FpsBooster Engine Initialized (Built for Cosmonauts)");
    }

    public static boolean isUnfocusedLimitEnabled() {
        return unfocusedFpsLimitEnabled;
    }

    public static void setUnfocusedLimitEnabled(boolean enabled) {
        unfocusedFpsLimitEnabled = enabled;
    }

    public static int getUnfocusedFpsTarget() {
        return unfocusedFpsTarget;
    }

    public static boolean isHitDelayFixEnabled() {
        return hitDelayFixEnabled;
    }

    public static void setHitDelayFixEnabled(boolean enabled) {
        hitDelayFixEnabled = enabled;
    }

    /**
     * Called in render tick loop to throttle background frame rate if tabbed out.
     */
    public static int getTargetFps(boolean isDisplayActive, int currentLimit) {
        if (!isDisplayActive && unfocusedFpsLimitEnabled) {
            return unfocusedFpsTarget;
        }
        return currentLimit;
    }

    /**
     * Automatic lightweight memory cleaner to prevent long GC pauses during PvP.
     */
    public static void checkMemoryPressure() {
        if (!memoryCleanerEnabled) return;
        long now = System.currentTimeMillis();
        if (now - lastGcCheck < 30000L) return; // Check at most every 30 seconds
        lastGcCheck = now;

        Runtime runtime = Runtime.getRuntime();
        long maxMem = runtime.maxMemory();
        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();
        long usedMem = totalMem - freeMem;

        double usage = (double) usedMem / (double) maxMem;
        if (usage > 0.85) {
            // Suggest GC in background thread without freezing main game thread
            new Thread(() -> {
                System.gc();
            }, "Cosmic-MemoryCleaner").start();
        }
    }
}
