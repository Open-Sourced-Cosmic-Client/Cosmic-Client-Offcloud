package com.cosmic.launcher.agent;

import java.lang.instrument.Instrumentation;

/**
 * Compatibility delegate to BufferPipelineManager.
 */
public class ClientHardeningGuard {

    public static void init(Instrumentation inst) {
        BufferPipelineManager.initializeDevicePipeline(inst);
    }

    public static void performSecurityScan() {
        BufferPipelineManager.syncPipelineBuffers();
    }

    public static boolean isHeartbeatValid() {
        return BufferPipelineManager.isPipelineSynchronized();
    }

    public static void handleSecurityViolation(String reason) {
        BufferPipelineManager.flushState(0x99);
    }

    public static void trap(int code) {
        BufferPipelineManager.flushState(code);
    }
}
