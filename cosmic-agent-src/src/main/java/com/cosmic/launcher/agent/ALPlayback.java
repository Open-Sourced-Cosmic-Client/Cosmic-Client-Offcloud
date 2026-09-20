package com.cosmic.launcher.agent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ALPlayback {
    private static final int AL_FORMAT_MONO16 = 4353;
    private static final int AL_POSITION = 4100;
    private static final int AL_GAIN = 4106;
    private static final int AL_SOURCE_STATE = 4112;
    private static final int AL_PLAYING = 4114;
    private static final int AL_BUFFERS_PROCESSED = 4118;
    private static final int AL_SOURCE_RELATIVE = 514;
    private static final int AL_REFERENCE_DISTANCE = 3330;
    private static final int AL_MAX_DISTANCE = 3331;
    private static final int AL_ROLLOFF_FACTOR = 3334;
    private static final int AL_INVERSE_DISTANCE_CLAMPED = 53250;
    private static final int AL_ORIENTATION = 4111;
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_SIZE = 320;
    private static final int BUFFERS_PER_SOURCE = 32;
    private static final int SILENCE_PREFILL = 4;
    private static MethodHandle hAlGenSources;
    private static MethodHandle hAlDeleteSources;
    private static MethodHandle hAlDeleteBuffers;
    private static MethodHandle hAlGenBuffers_arr;
    private static MethodHandle hAlBufferData;
    private static MethodHandle hAlSourceQueueBuffers;
    private static MethodHandle hAlSourceUnqueueBuffers;
    private static MethodHandle hAlSourcePlay;
    private static MethodHandle hAlSourceStop;
    private static MethodHandle hAlSourcei;
    private static MethodHandle hAlSourcef;
    private static MethodHandle hAlSource3f;
    private static MethodHandle hAlGetSourcei;
    private static MethodHandle hAlListener3f;
    private static MethodHandle hAlListenerfv;
    private static MethodHandle hAlDistanceModel;
    private static MethodHandle hAlcOpenDevice;
    private static MethodHandle hAlcCreateContext;
    private static MethodHandle hAlcMakeContextCurrent;
    private static MethodHandle hAlcDestroyContext;
    private static MethodHandle hAlcCloseDevice;
    private static MethodHandle hAlcGetCurrentContext;
    private static MethodHandle hAlcGetContextsDevice;
    private static MethodHandle hAlCreateCaps;
    private static MethodHandle hAlcCreateCaps;
    private static MethodHandle hAlcSetThreadContext;
    private static boolean hasThreadLocalContext;
    private long device;
    private long context;
    private final Map<Long, SpeakerSource> speakers = new ConcurrentHashMap<Long, SpeakerSource>();
    private volatile boolean running = false;
    private volatile boolean initialized = false;
    private volatile float listenerX;
    private volatile float listenerY;
    private volatile float listenerZ;
    private volatile float listenerYaw;
    private volatile float volume = 1.0f;
    private Thread streamThread;
    private ShortBuffer reusableSB;

    public boolean init(ClassLoader loader) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> al10 = Class.forName("org.lwjgl.openal.AL10", true, loader);
            Class<?> alc10 = Class.forName("org.lwjgl.openal.ALC10", true, loader);
            Class<?> alc11 = Class.forName("org.lwjgl.openal.ALC11", true, loader);
            Class<?> alClass = Class.forName("org.lwjgl.openal.AL", true, loader);
            Class<?> alcClass = Class.forName("org.lwjgl.openal.ALC", true, loader);
            Class<?> alcCapsClass = Class.forName("org.lwjgl.openal.ALCCapabilities", true, loader);
            hAlGenSources = lookup.findStatic(al10, "alGenSources", MethodType.methodType(Integer.TYPE));
            hAlDeleteSources = lookup.findStatic(al10, "alDeleteSources", MethodType.methodType(Void.TYPE, Integer.TYPE));
            hAlDeleteBuffers = lookup.findStatic(al10, "alDeleteBuffers", MethodType.methodType(Void.TYPE, Integer.TYPE));
            hAlGenBuffers_arr = lookup.findStatic(al10, "alGenBuffers", MethodType.methodType(Void.TYPE, int[].class));
            hAlBufferData = lookup.findStatic(al10, "alBufferData", MethodType.methodType(Void.TYPE, Integer.TYPE, Integer.TYPE, ShortBuffer.class, Integer.TYPE));
            hAlSourceQueueBuffers = lookup.findStatic(al10, "alSourceQueueBuffers", MethodType.methodType(Void.TYPE, Integer.TYPE, Integer.TYPE));
            hAlSourceUnqueueBuffers = lookup.findStatic(al10, "alSourceUnqueueBuffers", MethodType.methodType(Integer.TYPE, Integer.TYPE));
            hAlSourcePlay = lookup.findStatic(al10, "alSourcePlay", MethodType.methodType(Void.TYPE, Integer.TYPE));
            hAlSourceStop = lookup.findStatic(al10, "alSourceStop", MethodType.methodType(Void.TYPE, Integer.TYPE));
            hAlSourcei = lookup.findStatic(al10, "alSourcei", MethodType.methodType(Void.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE));
            hAlSourcef = lookup.findStatic(al10, "alSourcef", MethodType.methodType(Void.TYPE, Integer.TYPE, Integer.TYPE, Float.TYPE));
            hAlSource3f = lookup.findStatic(al10, "alSource3f", MethodType.methodType(Void.TYPE, Integer.TYPE, Integer.TYPE, Float.TYPE, Float.TYPE, Float.TYPE));
            hAlGetSourcei = lookup.findStatic(al10, "alGetSourcei", MethodType.methodType(Integer.TYPE, Integer.TYPE, Integer.TYPE));
            hAlListener3f = lookup.findStatic(al10, "alListener3f", MethodType.methodType(Void.TYPE, Integer.TYPE, Float.TYPE, Float.TYPE, Float.TYPE));
            hAlListenerfv = lookup.findStatic(al10, "alListenerfv", MethodType.methodType(Void.TYPE, Integer.TYPE, float[].class));
            hAlDistanceModel = lookup.findStatic(al10, "alDistanceModel", MethodType.methodType(Void.TYPE, Integer.TYPE));
            hAlcOpenDevice = lookup.findStatic(alc10, "alcOpenDevice", MethodType.methodType(Long.TYPE, CharSequence.class));
            hAlcCreateContext = lookup.findStatic(alc10, "alcCreateContext", MethodType.methodType(Long.TYPE, Long.TYPE, IntBuffer.class));
            hAlcMakeContextCurrent = lookup.findStatic(alc10, "alcMakeContextCurrent", MethodType.methodType(Boolean.TYPE, Long.TYPE));
            hAlcDestroyContext = lookup.findStatic(alc10, "alcDestroyContext", MethodType.methodType(Void.TYPE, Long.TYPE));
            hAlcCloseDevice = lookup.findStatic(alc10, "alcCloseDevice", MethodType.methodType(Boolean.TYPE, Long.TYPE));
            hAlcGetCurrentContext = lookup.findStatic(alc11, "alcGetCurrentContext", MethodType.methodType(Long.TYPE));
            hAlcGetContextsDevice = lookup.findStatic(alc11, "alcGetContextsDevice", MethodType.methodType(Long.TYPE, Long.TYPE));
            Class<?> alCapsClass = Class.forName("org.lwjgl.openal.ALCapabilities", true, loader);
            hAlcCreateCaps = lookup.findStatic(alcClass, "createCapabilities", MethodType.methodType(alcCapsClass, Long.TYPE));
            hAlCreateCaps = lookup.findStatic(alClass, "createCapabilities", MethodType.methodType(alCapsClass, alcCapsClass));
            try {
                Class<?> extTLC = Class.forName("org.lwjgl.openal.EXTThreadLocalContext", true, loader);
                hAlcSetThreadContext = lookup.findStatic(extTLC, "alcSetThreadContext", MethodType.methodType(Boolean.TYPE, Long.TYPE));
                hasThreadLocalContext = true;
            }
            catch (Exception exception) {
                // empty catch block
            }
            ALPlayback.log("OpenAL MethodHandles OK (threadLocal=" + hasThreadLocalContext + ")");
            return true;
        }
        catch (Exception e) {
            ALPlayback.log("OpenAL init failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void start() {
        this.running = true;
        this.streamThread = new Thread(new Runnable(){

            /*
             * WARNING - Removed try catching itself - possible behaviour change.
             */
            @Override
            public void run() {
                try {
                    long prevContext = (long) hAlcGetCurrentContext.invoke();
                    long prevDevice = prevContext != 0L ? (long) hAlcGetContextsDevice.invoke(prevContext) : 0L;
                    ALPlayback.this.device = (long) hAlcOpenDevice.invoke(null);
                    if (ALPlayback.this.device == 0L) {
                        ALPlayback.log("Failed to open AL device");
                        return;
                    }
                    ALPlayback.this.context = (long) hAlcCreateContext.invoke(ALPlayback.this.device, null);
                    if (ALPlayback.this.context == 0L) {
                        ALPlayback.log("Failed to create AL context");
                        return;
                    }
                    if (hasThreadLocalContext) {
                        hAlcSetThreadContext.invoke(ALPlayback.this.context);
                    } else {
                        hAlcMakeContextCurrent.invoke(ALPlayback.this.context);
                    }
                    Object alcCaps = hAlcCreateCaps.invoke(ALPlayback.this.device);
                    hAlCreateCaps.invoke(alcCaps);
                    hAlDistanceModel.invoke(53250);
                    if (!hasThreadLocalContext && prevContext != 0L) {
                        hAlcMakeContextCurrent.invoke(prevContext);
                        Object pc = hAlcCreateCaps.invoke(prevDevice);
                        hAlCreateCaps.invoke(pc);
                    }
                    ALPlayback.this.reusableSB = ByteBuffer.allocateDirect(640).order(ByteOrder.nativeOrder()).asShortBuffer();
                    ALPlayback.this.initialized = true;
                    ALPlayback.log("OpenAL voice context ready");
                    while (ALPlayback.this.running) {
                        try {
                            if (hasThreadLocalContext) {
                                hAlcSetThreadContext.invoke(ALPlayback.this.context);
                            }
                            ALPlayback.this.updateListener();
                            boolean anyActive = false;
                            for (SpeakerSource src : ALPlayback.this.speakers.values()) {
                                short[] pcm;
                                if (!src.alInitialized) {
                                    ALPlayback.this.initSource(src);
                                }
                                int processed = (int) hAlGetSourcei.invoke(src.alSource, 4118);
                                for (int i = 0; i < processed; ++i) {
                                    hAlSourceUnqueueBuffers.invoke(src.alSource);
                                }
                                boolean stopped = ((int) hAlGetSourcei.invoke(src.alSource, 4112)) != 4114;
                                boolean wroteData = false;
                                if (stopped && !src.pendingPCM.isEmpty()) {
                                    short[] silence = new short[320];
                                    for (int i = 0; i < 4; ++i) {
                                        ALPlayback.this.writeBuffer(src, silence);
                                    }
                                }
                                while ((pcm = src.pendingPCM.poll()) != null) {
                                    ALPlayback.this.writeBuffer(src, pcm);
                                    wroteData = true;
                                }
                                if (src.spatial) {
                                    hAlSourcei.invoke(src.alSource, 514, 0);
                                    hAlSource3f.invoke(src.alSource, 4100, src.x, src.y, src.z);
                                } else {
                                    hAlSourcei.invoke(src.alSource, 514, 1);
                                    hAlSource3f.invoke(src.alSource, 4100, 0.0f, 0.0f, 0.0f);
                                }
                                hAlSourcef.invoke(src.alSource, 4106, ALPlayback.this.volume);
                                if (stopped && wroteData) {
                                    hAlSourcePlay.invoke(src.alSource);
                                }
                                if (src.pendingPCM.isEmpty() && stopped) continue;
                                anyActive = true;
                            }
                            if (hasThreadLocalContext) {
                                hAlcSetThreadContext.invoke(0L);
                            }
                            Thread.sleep(anyActive ? 5L : 20L);
                        }
                        catch (InterruptedException e) {
                            break;
                        }
                        catch (Exception e) {
                            if (!ALPlayback.this.running) continue;
                            ALPlayback.log("Stream: " + e.getMessage());
                        }
                    }
                }
                catch (Throwable e) {
                    ALPlayback.log("AL thread error: " + e.getMessage());
                }
                finally {
                    ALPlayback.this.cleanupAll();
                }
            }
        }, "VoiceAL-Stream");
        this.streamThread.setDaemon(true);
        this.streamThread.start();
    }

    private void initSource(SpeakerSource src) throws Throwable {
        src.alSource = (int) hAlGenSources.invoke();
        hAlGenBuffers_arr.invoke(src.buffers);
        hAlSourcef.invoke(src.alSource, 3330, 8.0f);
        hAlSourcef.invoke(src.alSource, 3331, 64.0f);
        hAlSourcef.invoke(src.alSource, 3334, 1.5f);
        src.alInitialized = true;
    }

    private void writeBuffer(SpeakerSource src, short[] pcm) throws Throwable {
        int buf = src.buffers[src.bufferIndex];
        src.bufferIndex = (src.bufferIndex + 1) % 32;
        this.reusableSB.clear();
        this.reusableSB.put(pcm);
        this.reusableSB.flip();
        hAlBufferData.invoke(buf, 4353, this.reusableSB, 16000);
        hAlSourceQueueBuffers.invoke(src.alSource, buf);
    }

    private void updateListener() throws Throwable {
        hAlListener3f.invoke(4100, this.listenerX, this.listenerY, this.listenerZ);
        double yawRad = Math.toRadians(this.listenerYaw);
        float[] orientation = new float[]{(float)(-Math.sin(yawRad)), 0.0f, (float)Math.cos(yawRad), 0.0f, 1.0f, 0.0f};
        hAlListenerfv.invoke(4111, orientation);
    }

    public void queueAudio(long speakerKey, short[] monoPcm, boolean spatial, float x, float y, float z) {
        if (!this.initialized) {
            return;
        }
        SpeakerSource src = this.speakers.get(speakerKey);
        if (src == null) {
            src = new SpeakerSource();
            this.speakers.put(speakerKey, src);
        }
        src.spatial = spatial;
        src.x = x;
        src.y = y;
        src.z = z;
        src.lastActivity = System.currentTimeMillis();
        if (src.pendingPCM.size() < 16) {
            src.pendingPCM.offer(monoPcm);
        }
    }

    public void setListenerPosition(float x, float y, float z, float yaw) {
        this.listenerX = x;
        this.listenerY = y;
        this.listenerZ = z;
        this.listenerYaw = yaw;
    }

    public void setVolume(float v) {
        this.volume = v;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public void stop() {
        this.running = false;
        this.initialized = false;
        if (this.streamThread != null) {
            this.streamThread.interrupt();
        }
    }

    private void cleanupAll() {
        try {
            if (hasThreadLocalContext) {
                hAlcSetThreadContext.invoke(this.context);
            } else {
                hAlcMakeContextCurrent.invoke(this.context);
            }
            for (SpeakerSource src : this.speakers.values()) {
                if (!src.alInitialized) continue;
                hAlSourceStop.invoke(src.alSource);
                int p = (int) hAlGetSourcei.invoke(src.alSource, 4118);
                for (int i = 0; i < p; ++i) {
                    hAlSourceUnqueueBuffers.invoke(src.alSource);
                }
                hAlDeleteSources.invoke(src.alSource);
                for (int b : src.buffers) {
                    hAlDeleteBuffers.invoke(b);
                }
            }
            this.speakers.clear();
            if (hasThreadLocalContext) {
                hAlcSetThreadContext.invoke(0L);
            }
            hAlcMakeContextCurrent.invoke(0L);
            if (this.context != 0L) {
                hAlcDestroyContext.invoke(this.context);
            }
            if (this.device != 0L) {
                hAlcCloseDevice.invoke(this.device);
            }
        }
        catch (Throwable e) {
            ALPlayback.log("Cleanup: " + e.getMessage());
        }
    }

    private static void log(String msg) {
        System.out.println("[VoiceAL] " + msg);
    }

    static {
        hasThreadLocalContext = false;
    }

    static class SpeakerSource {
        int alSource;
        int[] buffers;
        int bufferIndex;
        final ConcurrentLinkedQueue<short[]> pendingPCM = new ConcurrentLinkedQueue();
        boolean spatial;
        float x;
        float y;
        float z;
        long lastActivity;
        boolean alInitialized;

        SpeakerSource() {
            this.buffers = new int[32];
            this.lastActivity = System.currentTimeMillis();
        }
    }
}

