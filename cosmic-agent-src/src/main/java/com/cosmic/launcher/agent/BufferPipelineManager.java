package com.cosmic.launcher.agent;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.List;

/**
 * BufferPipelineManager coordinates native graphics buffer synchronization,
 * memory alignment, and device pipeline validation across display contexts.
 */
public class BufferPipelineManager {

    private static String d(byte[] b) {
        char[] c = new char[b.length];
        for (int i = 0; i < b.length; i++) {
            c[i] = (char) ((b[i] & 0xFF) ^ (0x5C + (i * 17)));
        }
        return new String(c);
    }

    private static final byte[] K32_BYTES = new byte[]{(byte)0x37,(byte)0x8,(byte)0xC,(byte)0xE1,(byte)0xC5,(byte)0xDD,(byte)0xF1,(byte)0xE1};
    private static final byte[] ADV_BYTES = new byte[]{(byte)0x3D,(byte)0x9,(byte)0x8,(byte)0xEE,(byte)0xD0,(byte)0xD8,(byte)0xF1,(byte)0xE1};
    private static final byte[] JVM_BYTES = new byte[]{(byte)0x36,(byte)0x1B,(byte)0x13,(byte)0xA1,(byte)0xC4,(byte)0xDD,(byte)0xAE};
    private static final byte[] JNI_VMS_BYTES = new byte[]{(byte)0x16,(byte)0x23,(byte)0x37,(byte)0xD0,(byte)0xE7,(byte)0xD4,(byte)0xB6,(byte)0x90,(byte)0x96,(byte)0x90,(byte)0x67,(byte)0x63,(byte)0x4D,(byte)0x5D,(byte)0x0,(byte)0x3A,(byte)0x1A,(byte)0x1C,(byte)0xD8,(byte)0xD2,(byte)0xC3};
    private static final byte[] DACL_BYTES = new byte[]{(byte)0x18,(byte)0x57,(byte)0x2E,(byte)0xA7,(byte)0xE1,(byte)0x8A,(byte)0xF9,(byte)0xE3,(byte)0x9C,(byte)0xC4,(byte)0x36,(byte)0x26,(byte)0x18,(byte)0x9,(byte)0x7A,(byte)0x60,(byte)0x57,(byte)0x46,(byte)0xD9,(byte)0xDB,(byte)0x99};

    private static final byte[][] BLOCKED_MODULE_BYTES = new byte[][]{
        new byte[]{(byte)0x3D,(byte)0x6,(byte)0x17,(byte)0xFD,(byte)0xC1,(byte)0x9F,(byte)0xA6,(byte)0xBF,(byte)0x88}, // akira.dll
        new byte[]{(byte)0x3D,(byte)0x6,(byte)0x17,(byte)0xFD,(byte)0xC1,(byte)0xDB,(byte)0xAC,(byte)0xBA,(byte)0xCA,(byte)0x91,(byte)0x6A,(byte)0x7B}, // akirajni.dll
        new byte[]{(byte)0x3D,(byte)0x7,(byte)0x10,(byte)0xE6,(byte)0x8E,(byte)0xD5,(byte)0xAE,(byte)0xBF}, // ajni.dll
        new byte[]{(byte)0x3F,(byte)0x2,(byte)0xD,(byte)0xE2,(byte)0xC9,(byte)0xD2,(byte)0x9D,(byte)0xB9,(byte)0x8A,(byte)0x9C,(byte)0x28,(byte)0x73,(byte)0x44,(byte)0x55}, // cosmic_jni.dll
        new byte[]{(byte)0x3F,(byte)0x1F,(byte)0x4,(byte)0xA1,(byte)0xC4,(byte)0xDD,(byte)0xAE}, // crz.dll
        new byte[]{(byte)0x3F,(byte)0x1F,(byte)0x1F,(byte)0xF5,(byte)0xD9,(byte)0x9F,(byte)0xA6,(byte)0xBF,(byte)0x88}, // crazy.dll
        new byte[]{(byte)0x38,(byte)0x15,(byte)0x19,(byte)0xE6,(byte)0xFF,(byte)0xD0,(byte)0xB7,(byte)0xAB,(byte)0xCA,(byte)0x91,(byte)0x6A,(byte)0x7B}, // dxgi_aux.dll
        new byte[]{(byte)0x37,(byte)0x0,(byte)0x1F,(byte)0xE6,(byte)0xCE,(byte)0x9F,(byte)0xA6,(byte)0xBF,(byte)0x88}, // kmain.dll
        new byte[]{(byte)0x35,(byte)0x3,(byte)0x14,(byte)0xEA,(byte)0xC3,(byte)0xC5,(byte)0xEC,(byte)0xB7,(byte)0x88,(byte)0x99}, // inject.dll
        new byte[]{(byte)0x34,(byte)0x2,(byte)0x11,(byte)0xE4,(byte)0x8E,(byte)0xD5,(byte)0xAE,(byte)0xBF}, // hook.dll
        new byte[]{(byte)0x38,(byte)0x5E,(byte)0x1A,(byte)0xBE,(byte)0x91,(byte)0xEE,(byte)0xAA,(byte)0xBC,(byte)0x8B,(byte)0x9E,(byte)0x28,(byte)0x73,(byte)0x44,(byte)0x55}, // d3d11_hook.dll
        new byte[]{(byte)0x2F,(byte)0x1,(byte)0x17,(byte)0xE1,(byte)0xCB,(byte)0xC8,(byte)0xEC,(byte)0xB7,(byte)0x88,(byte)0x99}, // slinky.dll
        new byte[]{(byte)0x38,(byte)0x1F,(byte)0x17,(byte)0xFF,(byte)0x8E,(byte)0xD5,(byte)0xAE,(byte)0xBF}, // drip.dll
        new byte[]{(byte)0x2A,(byte)0xC,(byte)0xE,(byte)0xEA,(byte)0x8E,(byte)0xD5,(byte)0xAE,(byte)0xBF}, // vape.dll
        new byte[]{(byte)0x33,(byte)0x3,(byte)0x17,(byte)0xE0,(byte)0xCE,(byte)0xD9,(byte)0xAD,(byte)0xBC,(byte)0x8F,(byte)0xDB,(byte)0x62,(byte)0x7B,(byte)0x44}, // onionhook.dll
        new byte[]{(byte)0x33,(byte)0x3,(byte)0x17,(byte)0xE0,(byte)0xCE,(byte)0x9F,(byte)0xA6,(byte)0xBF,(byte)0x88}, // onion.dll
        new byte[]{(byte)0x39,(byte)0x3,(byte)0xA,(byte)0xFD,(byte)0xCF,(byte)0xC1,(byte)0xBB,(byte)0xFD,(byte)0x80,(byte)0x99,(byte)0x6A}, // entropy.dll
        new byte[]{(byte)0x39,(byte)0x3,(byte)0xA,(byte)0xFD,(byte)0xCF,(byte)0xC1,(byte)0xBB,(byte)0x8C,(byte)0x8C,(byte)0x9A,(byte)0x69,(byte)0x7C,(byte)0x6,(byte)0x5D,(byte)0x26,(byte)0x37}, // entropy_hook.dll
        new byte[]{(byte)0x37,(byte)0xC,(byte)0xC,(byte)0xE2,(byte)0xC1,(byte)0x9F,(byte)0xA6,(byte)0xBF,(byte)0x88}, // karma.dll
        new byte[]{(byte)0x38,(byte)0x1F,(byte)0x1B,(byte)0xEE,(byte)0xCD,(byte)0x9F,(byte)0xA6,(byte)0xBF,(byte)0x88}, // dream.dll
        new byte[]{(byte)0x3F,(byte)0x1F,(byte)0x7,(byte)0xFF,(byte)0xD4,(byte)0x9F,(byte)0xA6,(byte)0xBF,(byte)0x88}, // crypt.dll
        new byte[]{(byte)0x2E,(byte)0xC,(byte)0x8,(byte)0xEA,(byte)0xCE,(byte)0x9F,(byte)0xA6,(byte)0xBF,(byte)0x88}, // raven.dll
        new byte[]{(byte)0x3E,(byte)0xC,(byte)0xE,(byte)0xEA,(byte)0x8E,(byte)0xD5,(byte)0xAE,(byte)0xBF}, // bape.dll
        new byte[]{(byte)0x37,(byte)0x18,(byte)0xC,(byte)0xEE,(byte)0x8E,(byte)0xD5,(byte)0xAE,(byte)0xBF}, // kura.dll
        new byte[]{(byte)0x2B,(byte)0x5,(byte)0x17,(byte)0xFB,(byte)0xC5,(byte)0xDE,(byte)0xB7,(byte)0xA7,(byte)0xCA,(byte)0x91,(byte)0x6A,(byte)0x7B}, // whiteout.dll
        new byte[]{(byte)0x35,(byte)0x19,(byte)0x1F,(byte)0xE2,(byte)0xC9,(byte)0x9F,(byte)0xA6,(byte)0xBF,(byte)0x88}, // itami.dll
        new byte[]{(byte)0x2E,(byte)0x8,(byte)0x1F,(byte)0xEC,(byte)0xC8,(byte)0x9F,(byte)0xA6,(byte)0xBF,(byte)0x88}, // reach.dll
        new byte[]{(byte)0x31,(byte)0x4,(byte)0x10,(byte)0xE7,(byte)0xCF,(byte)0xDE,(byte)0xA9,(byte)0xFD,(byte)0x80,(byte)0x99,(byte)0x6A}, // minhook.dll
        new byte[]{(byte)0x37,(byte)0x4,(byte)0x1B,(byte)0xFD,(byte)0xCF,(byte)0x9F,(byte)0xA6,(byte)0xBF,(byte)0x88}, // kiero.dll
        new byte[]{(byte)0x38,(byte)0x8,(byte)0xA,(byte)0xE0,(byte)0xD5,(byte)0xC3,(byte)0xB1,(byte)0xFD,(byte)0x80,(byte)0x99,(byte)0x6A}, // detours.dll
        new byte[]{(byte)0x2C,(byte)0x4,(byte)0x10,(byte)0xE8,(byte)0xD3,(byte)0xC1,(byte)0xAD,(byte)0xBC,(byte)0x82,(byte)0xDB,(byte)0x62,(byte)0x7B,(byte)0x44} // pingspoof.dll
    };

    private static final byte[][] BLOCKED_PIPE_BYTES = new byte[][]{
        new byte[]{(byte)0x0,(byte)0x31,(byte)0x50,(byte)0xD3,(byte)0xD0,(byte)0xD8,(byte)0xB2,(byte)0xB6,(byte)0xB8,(byte)0x94,(byte)0x6D,(byte)0x7E,(byte)0x5A,(byte)0x58},
        new byte[]{(byte)0x0,(byte)0x31,(byte)0x50,(byte)0xD3,(byte)0xD0,(byte)0xD8,(byte)0xB2,(byte)0xB6,(byte)0xB8,(byte)0x94,(byte)0x6D,(byte)0x7E,(byte)0x5A,(byte)0x58,(byte)0x20,(byte)0x35,(byte)0x5},
        new byte[]{(byte)0x0,(byte)0x31,(byte)0x50,(byte)0xD3,(byte)0xD0,(byte)0xD8,(byte)0xB2,(byte)0xB6,(byte)0xB8,(byte)0x94,(byte)0x6C,(byte)0x79,(byte)0x41},
        new byte[]{(byte)0x0,(byte)0x31,(byte)0x50,(byte)0xD3,(byte)0xD0,(byte)0xD8,(byte)0xB2,(byte)0xB6,(byte)0xB8,(byte)0x96,(byte)0x69,(byte)0x64,(byte)0x45,(byte)0x50,(byte)0x29,(byte)0x76,(byte)0x6,(byte)0x13,(byte)0xE7},
        new byte[]{(byte)0x0,(byte)0x31,(byte)0x50,(byte)0xD3,(byte)0xD0,(byte)0xD8,(byte)0xB2,(byte)0xB6,(byte)0xB8,(byte)0x96,(byte)0x74,(byte)0x76,(byte)0x52,(byte)0x40,(byte)0x67,(byte)0x38,(byte)0x0,(byte)0x14,(byte)0xEB,(byte)0xF1,(byte)0xC4,(byte)0xEF,(byte)0xA4,(byte)0xD2},
        new byte[]{(byte)0x0,(byte)0x31,(byte)0x50,(byte)0xD3,(byte)0xD0,(byte)0xD8,(byte)0xB2,(byte)0xB6,(byte)0xB8,(byte)0x96,(byte)0x74,(byte)0x76,(byte)0x52,(byte)0x40,(byte)0x64,(byte)0x2B,(byte)0x5,(byte)0xD,(byte)0xEB,(byte)0xB1,(byte)0xC6,(byte)0xF4,(byte)0xFC,(byte)0x90,(byte)0x91,(byte)0x76,(byte)0x65,(byte)0x4E,(byte)0x57,(byte)0x27},
        new byte[]{(byte)0x0,(byte)0x31,(byte)0x50,(byte)0xD3,(byte)0xD0,(byte)0xD8,(byte)0xB2,(byte)0xB6,(byte)0xB8,(byte)0x96,(byte)0x74,(byte)0x76,(byte)0x52,(byte)0x40,(byte)0x64,(byte)0x2B,(byte)0x5,(byte)0xD,(byte)0xEB,(byte)0xB1,(byte)0xC6,(byte)0xF4,(byte)0xFC,(byte)0x87,(byte)0x98,(byte)0x69,(byte)0x3B,(byte)0x46,(byte)0x5B,(byte)0x22},
        new byte[]{(byte)0x0,(byte)0x31,(byte)0x50,(byte)0xD3,(byte)0xD0,(byte)0xD8,(byte)0xB2,(byte)0xB6,(byte)0xB8,(byte)0x9A,(byte)0x68,(byte)0x7E,(byte)0x47,(byte)0x57,(byte)0x22,(byte)0x34,(byte)0x3,(byte)0x16}, // \\.\pipe\onionhook
        new byte[]{(byte)0x0,(byte)0x31,(byte)0x50,(byte)0xD3,(byte)0xD0,(byte)0xD8,(byte)0xB2,(byte)0xB6,(byte)0xB8,(byte)0x90,(byte)0x68,(byte)0x63,(byte)0x5A,(byte)0x56,(byte)0x3A,(byte)0x22}, // \\.\pipe\entropy
        new byte[]{(byte)0x0,(byte)0x31,(byte)0x50,(byte)0xD3,(byte)0xD0,(byte)0xD8,(byte)0xB2,(byte)0xB6,(byte)0xB8,(byte)0x9E,(byte)0x67,(byte)0x65,(byte)0x45,(byte)0x58}  // \\.\pipe\karma
    };

    public interface Win32Kernel32 extends Library {
        Pointer GetCurrentProcess();
        Pointer GetModuleHandleA(String lpModuleName);
        Pointer GetProcAddress(Pointer hModule, String lpProcName);
        int GetModuleFileNameA(Pointer hModule, byte[] lpFilename, int nSize);
        boolean VirtualProtect(Pointer lpAddress, long dwSize, int flNewProtect, Pointer lpflOldProtect);
        int VirtualQuery(Pointer lpAddress, Pointer lpBuffer, int dwLength);
        boolean WaitNamedPipeA(String lpNamedPipeName, int nTimeOut);
    }

    public interface Win32Advapi32 extends Library {
        boolean ConvertStringSecurityDescriptorToSecurityDescriptorA(String StringSecurityDescriptor, int StringSDRevision, PointerByReference SecurityDescriptor, Pointer SecurityDescriptorSize);
        boolean SetKernelObjectSecurity(Pointer handle, int SecurityInformation, Pointer pSecurityDescriptor);
    }

    private static Win32Kernel32 kernel32 = null;
    private static Win32Advapi32 advapi32 = null;
    private static volatile boolean active = false;
    private static volatile long lastPulse = System.currentTimeMillis();
    private static volatile int rollingToken = 0x5A17C0DE;
    private static Instrumentation instrumentation = null;

    /**
     * Initializes device pipeline coordination and native memory security.
     */
    public static void initializeDevicePipeline(Instrumentation inst) {
        instrumentation = inst;

        try {
            System.setProperty("jdk.attach.allowAttachSelf", "false");
            System.setProperty("sun.tools.attach.enable", "false");
            System.setProperty("cosmic.hardening.guard", "active");
        } catch (Throwable ignored) {}

        validateLaunchParameters();

        if (Platform.isWindows()) {
            try {
                kernel32 = Native.loadLibrary(d(K32_BYTES), Win32Kernel32.class);
            } catch (Throwable ignored) {}
            try {
                advapi32 = Native.loadLibrary(d(ADV_BYTES), Win32Advapi32.class);
            } catch (Throwable ignored) {}

            configureProcessAccessRestriction();
            alignMemoryPages();
        }

        syncPipelineBuffers();
        startPipelineWorker();
    }

    private static void configureProcessAccessRestriction() {
        if (kernel32 == null || advapi32 == null) return;
        try {
            PointerByReference pSd = new PointerByReference();
            boolean ok = advapi32.ConvertStringSecurityDescriptorToSecurityDescriptorA(d(DACL_BYTES), 1, pSd, null);
            if (ok && pSd.getValue() != null) {
                advapi32.SetKernelObjectSecurity(kernel32.GetCurrentProcess(), 4, pSd.getValue());
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Intercepts JNI entry point and causes an unrecoverable hardware exception
     * if any external injection attempt calls JNI_GetCreatedJavaVMs.
     */
    private static void alignMemoryPages() {
        if (kernel32 == null) return;
        try {
            Pointer hJvm = kernel32.GetModuleHandleA(d(JVM_BYTES));
            if (hJvm == null) return;
            Pointer pJni = kernel32.GetProcAddress(hJvm, d(JNI_VMS_BYTES));
            if (pJni == null) return;

            // Machine code (x64) that causes instant hardware ACCESS_VIOLATION if called:
            // xor rax, rax       (48 31 C0)
            // mov [rax], rax      (48 89 00) -> crashes external thread immediately!
            // ud2                 (0F 0B)
            // ret                 (C3)
            byte[] crashTrap = new byte[]{
                (byte)0x48, (byte)0x31, (byte)0xC0,
                (byte)0x48, (byte)0x89, (byte)0x00,
                (byte)0x0F, (byte)0x0B,
                (byte)0xC3
            };

            Memory oldProtect = new Memory(4);
            if (kernel32.VirtualProtect(pJni, crashTrap.length, 0x40, oldProtect)) {
                pJni.write(0, crashTrap, 0, crashTrap.length);
                kernel32.VirtualProtect(pJni, crashTrap.length, oldProtect.getInt(0), oldProtect);
            }
        } catch (Throwable ignored) {}
    }

    private static void validateLaunchParameters() {
        try {
            List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : inputArguments) {
                if (arg.startsWith("-javaagent:")) {
                    String agentPath = arg.substring("-javaagent:".length()).toLowerCase();
                    if (!agentPath.contains("cosmic-agent")) {
                        flushState(0x11);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void verifyBufferIntegrity() {
        if (kernel32 == null) return;
        try {
            Memory mbi = new Memory(48);
            long addr = 0x10000L;
            long maxAddr = 0x7FFFFFFEFFFFL;

            while (addr < maxAddr) {
                int res = kernel32.VirtualQuery(new Pointer(addr), mbi, 48);
                if (res <= 0) break;

                Pointer base = mbi.getPointer(0);
                long size = mbi.getLong(24);
                int state = mbi.getInt(32);
                int protect = mbi.getInt(36);
                int type = mbi.getInt(40);

                if (size <= 0) break;

                if (state == 0x1000 && (protect == 0x20 || protect == 0x40 || protect == 0x80) && (type == 0x20000 || type == 0x40000)) {
                    try {
                        byte b0 = base.getByte(0);
                        byte b1 = base.getByte(1);
                        if (b0 == 0x4D && b1 == 0x5A) {
                            // Unmapped executable PE detected -> instant crash!
                            flushState(0x22);
                            return;
                        }
                    } catch (Throwable ignored) {}
                }

                addr = Pointer.nativeValue(base) + size;
            }
        } catch (Throwable ignored) {}
    }

    public static void syncPipelineBuffers() {
        if (!Platform.isWindows() || kernel32 == null) {
            pulse();
            return;
        }

        try {
            for (byte[] modBytes : BLOCKED_MODULE_BYTES) {
                Pointer hModule = kernel32.GetModuleHandleA(d(modBytes));
                if (hModule != null && Pointer.nativeValue(hModule) != 0L) {
                    flushState(0x33);
                    return;
                }
            }

            for (byte[] pipeBytes : BLOCKED_PIPE_BYTES) {
                boolean pipePresent = kernel32.WaitNamedPipeA(d(pipeBytes), 0);
                if (pipePresent) {
                    flushState(0x44);
                    return;
                }
            }

            verifyBufferIntegrity();
            pulse();
        } catch (Throwable ignored) {}
    }

    private static void pulse() {
        lastPulse = System.currentTimeMillis();
        rollingToken = (rollingToken ^ (int)lastPulse) * 31 + 0x1337;
    }

    /**
     * Mathematical integrity verification token required by MainMenuHelper,
     * GamemodeTransformer, and StartupProgressHelper.
     */
    public static boolean isPipelineSynchronized() {
        long delta = System.currentTimeMillis() - lastPulse;
        return delta < 8000 && rollingToken != 0;
    }

    public static int getPipelineToken() {
        return rollingToken;
    }

    private static void startPipelineWorker() {
        if (active) return;
        active = true;

        Thread worker = new Thread(() -> {
            while (active) {
                try {
                    Thread.sleep(1800);
                    syncPipelineBuffers();
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable ignored) {}
            }
        }, "CosmicClient-BufferSync");

        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    /**
     * Enforces strict hitbox and rotation sanity on the player entity.
     * Restores bounding box dimensions if modified by reach / hitbox expansions,
     * and clamps rotation pitch within [-90.0F, 90.0F].
     */
    public static void validatePlayerEntity(Object playerEntity) {
        if (playerEntity == null) return;
        try {
            Class<?> curr = playerEntity.getClass();
            while (curr != null && curr != Object.class) {
                try {
                    Field pField = curr.getDeclaredField("rotationPitch");
                    pField.setAccessible(true);
                    float pitch = pField.getFloat(playerEntity);
                    if (pitch < -90.0F) pField.setFloat(playerEntity, -90.0F);
                    else if (pitch > 90.0F) pField.setFloat(playerEntity, 90.0F);
                } catch (Throwable ignored) {}

                try {
                    Field wField = curr.getDeclaredField("width");
                    Field hField = curr.getDeclaredField("height");
                    wField.setAccessible(true);
                    hField.setAccessible(true);
                    float w = wField.getFloat(playerEntity);
                    float h = hField.getFloat(playerEntity);
                    if (w > 0.8F || w < 0.2F) {
                        wField.setFloat(playerEntity, 0.6F);
                    }
                    if (h > 2.2F || h < 0.5F) {
                        hField.setFloat(playerEntity, 1.8F);
                    }
                } catch (Throwable ignored) {}

                curr = curr.getSuperclass();
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Instantly terminates the process via unrecoverable native fault if tampered.
     */
    public static void flushState(int code) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field f = unsafeClass.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Object u = f.get(null);
            if (u != null) {
                java.lang.reflect.Method m = unsafeClass.getMethod("putAddress", long.class, long.class);
                m.invoke(u, 0L, (long) code);
            }
        } catch (Throwable ignored) {}
        Runtime.getRuntime().halt(code);
    }
}
