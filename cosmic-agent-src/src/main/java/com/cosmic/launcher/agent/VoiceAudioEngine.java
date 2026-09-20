package com.cosmic.launcher.agent;

import com.cosmic.launcher.agent.ALPlayback;
import com.cosmic.launcher.agent.OpusCodec;
import com.cosmic.launcher.agent.SpeakingHeaderMap;
import com.cosmic.launcher.agent.VoiceModuleHelper;
import com.cosmic.launcher.agent.VoiceUDPClient;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

public class VoiceAudioEngine {
    private static final AudioFormat CAPTURE_FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);
    private static final int CAPTURE_FRAME = 640;
    private static double listenerX;
    private static double listenerY;
    private static double listenerZ;
    private static float listenerYaw;
    private static TargetDataLine micLine;
    private static ALPlayback alPlayback;
    private static volatile boolean capturing;
    private static volatile boolean initialized;
    private static volatile boolean enabled;
    private static float volume;
    private static boolean wasPttPressed;
    private static Object pttKeybind;
    private static Field pttNField;
    private static int pttKeyCode;
    private static int tickCount;
    private static volatile Object wbRef;
    private static Object networkHandler;
    private static Method sendPacketMethod;
    private static Constructor<?> payloadPacketCtor;
    private static Constructor<?> packetBufferCtor;
    private static Method unpooledBuffer;
    private static Method writeBytesMethod;
    private static ClassLoader ccLoader;
    private static Thread audioRecvThread;
    private static Mixer.Info[] inputDevices;
    private static Mixer.Info[] outputDevices;
    private static int selectedInputIndex;
    private static int selectedOutputIndex;
    private static int sendCount;
    private static final Map<Long, Object> speakerDecoders;
    private static Object voiceEnableToggle;
    private static Object mainModuleToggle;
    private static Method mainToggleGetMethod;
    private static Object voiceVolumeSlider;
    private static Method toggleGetMethod;
    private static Method sliderGetMethod;
    private static Object ccPttKeybind;
    private static int lastMcKeyCode;
    private static int lastCcKeyCode;
    private static final String SETTINGS_FILE;
    private static Properties savedProps;
    private static boolean settingsLoaded;
    private static boolean settingsDirty;
    private static Method keybindGetMethod;
    private static Object playerEntity;
    private static Field posXField;
    private static Field posYField;
    private static Field posZField;
    private static Field yawField;
    private static boolean playerFieldsResolved;
    private static SpeakingHeaderMap speakingHeaderMap;
    private static boolean headerMapInstalled;
    private static Object guiIngame;
    private static Method recordPlayingMethod;
    private static boolean actionBarResolved;
    private static Field recordTimerField;
    private static final int NOISE_GATE_THRESHOLD = 80;
    private static int noiseGateHoldFrames;
    private static final float AGC_TARGET_RMS = 4000.0f;
    private static final float AGC_MAX_GAIN = 8.0f;
    private static final float AGC_MIN_GAIN = 0.2f;
    private static final float AGC_ATTACK = 0.1f;
    private static final float AGC_RELEASE = 0.02f;
    private static float captureGain;
    private static String detectedServerIp;
    private static UUID cachedUUID;
    private static Object micCombobox;
    private static Object spkCombobox;
    private static Method comboGetMethod;
    private static String lastMicDevice;
    private static String lastSpkDevice;

    public static void tick(Object wbInstance) {
        block21: {
            ++tickCount;
            try {
                if (!initialized) {
                    initialized = true;
                    VoiceAudioEngine.initialize(wbInstance);
                    try {
                        boolean opusOk = OpusCodec.init();
                        VoiceAudioEngine.log("Opus codec: " + (opusOk ? "ENABLED (32kbps)" : "unavailable, using raw PCM"));
                    }
                    catch (Exception e) {
                        VoiceAudioEngine.log("Opus init skipped: " + e.getMessage());
                    }
                }
                if (headerMapInstalled || tickCount > 60) {
                    // empty if block
                }
                if (tickCount % 40 == 0 && wbRef != null) {
                    UUID playerUUID;
                    try {
                        Object r6 = wbRef.getClass().getMethod("L", new Class[0]).invoke(wbRef, new Object[0]);
                        if (r6 != null && r6 != networkHandler) {
                            networkHandler = r6;
                            sendPacketMethod = null;
                            VoiceAudioEngine.initNetwork(wbRef);
                        } else if (r6 == null && networkHandler != null) {
                            networkHandler = null;
                            sendPacketMethod = null;
                            VoiceUDPClient.disconnect();
                        }
                    }
                    catch (Exception r6) {
                        // empty catch block
                    }
                    if (!VoiceUDPClient.isConnected() && networkHandler != null && enabled && (playerUUID = VoiceAudioEngine.getPlayerUUID()) != null) {
                        VoiceUDPClient.connect(VoiceAudioEngine.getServerIp(), playerUUID);
                    }
                }
                if (tickCount % 20 == 0) {
                    VoiceAudioEngine.syncUIControls();
                }
                VoiceAudioEngine.updateListenerPosition(wbInstance);
                if (!enabled || pttKeybind == null) {
                    return;
                }
                boolean pttPressed = VoiceAudioEngine.checkPtt();
                if (pttPressed && !wasPttPressed) {
                    VoiceAudioEngine.startCapture();
                } else if (!pttPressed && wasPttPressed) {
                    VoiceAudioEngine.stopCapture();
                }
                if (pttPressed && !wasPttPressed) {
                    VoiceAudioEngine.showActionBar(wbInstance, "\u00a7a\u00a7l\u25cf \u00a7r\u00a7fSpeaking", 999);
                } else if (!pttPressed && wasPttPressed) {
                    VoiceAudioEngine.showActionBar(wbInstance, "", 0);
                }
                wasPttPressed = pttPressed;
            }
            catch (Exception e) {
                if (tickCount % 6000 != 0) break block21;
                VoiceAudioEngine.log("Tick error: " + e.getMessage());
            }
        }
    }

    private static boolean checkPtt() {
        if (pttNField != null) {
            try {
                return pttNField.getBoolean(pttKeybind);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (pttKeyCode > 0) {
            try {
                long window = 0L;
                try {
                    Class<?> display = MainMenuHelper.getDisplayClass(null);
                    if (display != null) {
                        window = (Long)display.getMethod("getWindow", new Class[0]).invoke(null, new Object[0]);
                    }
                }
                catch (Exception display) {
                    // empty catch block
                }
                if (window != 0L) {
                    Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
                    return (Integer)glfw.getMethod("glfwGetKey", Long.TYPE, Integer.TYPE).invoke(null, window, pttKeyCode) == 1;
                }
            }
            catch (Exception window) {
                // empty catch block
            }
            try {
                Class<?> keyboard = Class.forName("org.lwjgl.input.Keyboard");
                return (Boolean)keyboard.getMethod("isKeyDown", Integer.TYPE).invoke(null, pttKeyCode);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        try {
            for (Method m : pttKeybind.getClass().getDeclaredMethods()) {
                if (!m.getName().equals("c") || m.getParameterCount() != 0 || m.getReturnType() != Boolean.TYPE) continue;
                m.setAccessible(true);
                return (Boolean)m.invoke(pttKeybind, new Object[0]);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return false;
    }

    private static void initialize(Object wbInstance) {
        int i;
        VoiceAudioEngine.log("Initializing voice audio engine...");
        ccLoader = wbInstance.getClass().getClassLoader();
        wbRef = wbInstance;
        VoiceAudioEngine.loadSettings();
        VoiceAudioEngine.enumerateDevices();
        String savedInput = savedProps.getProperty("input_device", "");
        String savedOutput = savedProps.getProperty("output_device", "");
        if (!savedInput.isEmpty()) {
            for (i = 0; i < inputDevices.length; ++i) {
                if (!inputDevices[i].getName().equals(savedInput)) continue;
                selectedInputIndex = i;
                break;
            }
        }
        if (!savedOutput.isEmpty()) {
            for (i = 0; i < outputDevices.length; ++i) {
                if (!outputDevices[i].getName().equals(savedOutput)) continue;
                selectedOutputIndex = i;
                break;
            }
        }
        VoiceAudioEngine.openMic(selectedInputIndex);
        alPlayback = new ALPlayback();
        if (alPlayback.init(ccLoader)) {
            alPlayback.setVolume(volume);
            alPlayback.start();
            VoiceAudioEngine.log("OpenAL playback started");
        } else {
            VoiceAudioEngine.log("OpenAL init failed - no audio output");
        }
        audioRecvThread = new Thread(() -> {
            while (true) {
                try {
                    while (true) {
                        if (!enabled || alPlayback == null || !alPlayback.isInitialized()) {
                            while (VoiceUDPClient.pollAudio() != null) {
                            }
                            Thread.sleep(100L);
                            continue;
                        }
                        VoiceAudioEngine.pollUDPAudio();
                        Thread.sleep(5L);
                    }
                }
                catch (Exception exception) {
                    // continue
                }
            }
        }, "VoiceUDPRecv");
        audioRecvThread.setDaemon(true);
        audioRecvThread.start();
        VoiceAudioEngine.initNetwork(wbInstance);
        new Thread(() -> {
            try {
                Thread.sleep(3000L);
                VoiceModuleHelper.injectVoiceModule(ccLoader);
            }
            catch (Exception e) {
                VoiceAudioEngine.log("Module injection error: " + e.getMessage());
            }
        }, "VoiceModuleInject").start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                int finalKey;
                VoiceAudioEngine.syncUIControls();
                if (pttKeybind != null && (finalKey = VoiceAudioEngine.readKeyCodeFull(pttKeybind)) != 0) {
                    pttKeyCode = finalKey;
                }
                VoiceAudioEngine.saveSettings();
                VoiceAudioEngine.log("Shutdown: saved settings (ptt_key=" + pttKeyCode + ")");
            }
            catch (Exception e) {
                VoiceAudioEngine.log("Shutdown save error: " + e.getMessage());
            }
        }, "VoiceShutdownSave"));
        VoiceAudioEngine.log("Voice engine ready! 16kHz, 20ms frames, UDP transport");
    }

    private static void initNetwork(Object wbInstance) {
        try {
            networkHandler = wbInstance.getClass().getMethod("L", new Class[0]).invoke(wbInstance, new Object[0]);
            if (networkHandler == null) {
                VoiceAudioEngine.log("Network: r6 null");
                return;
            }
            Class<?> yClass = Class.forName("cosmicclient.y", true, ccLoader);
            for (Constructor<?> constructor : yClass.getDeclaredConstructors()) {
                if (constructor.getParameterCount() < 2 || constructor.getParameterTypes()[0] != String.class) continue;
                payloadPacketCtor = constructor;
                payloadPacketCtor.setAccessible(true);
                break;
            }
            for (Method method : networkHandler.getClass().getMethods()) {
                Class<?> pType;
                if (!method.getName().equals("a") || method.getParameterCount() != 1 || !(pType = method.getParameterTypes()[0]).isAssignableFrom(yClass)) continue;
                sendPacketMethod = method;
                sendPacketMethod.setAccessible(true);
                break;
            }
            Class<?> cBClass = Class.forName("cosmicclient.cB", true, ccLoader);
            Class<?> byteBufClass = Class.forName("io.netty.buffer.ByteBuf", true, ccLoader);
            packetBufferCtor = cBClass.getConstructor(byteBufClass);
            unpooledBuffer = Class.forName("io.netty.buffer.Unpooled", true, ccLoader).getMethod("buffer", new Class[0]);
            writeBytesMethod = cBClass.getMethod("writeBytes", byte[].class);
            VoiceAudioEngine.log("Network ready!");
        }
        catch (Exception e) {
            VoiceAudioEngine.log("Network error: " + e.getMessage());
        }
    }

    private static void sendAudio(byte[] pcmFrame) {
        block7: {
            if (!VoiceUDPClient.isConnected()) {
                if (sendCount == 0) {
                    VoiceAudioEngine.log("UDP not connected, can't send");
                }
                return;
            }
            if (!enabled) {
                if (sendCount == 0) {
                    VoiceAudioEngine.log("Voice disabled, not sending");
                }
                return;
            }
            try {
                byte[] wrapped = OpusCodec.wrapForTransmit(pcmFrame);
                VoiceUDPClient.sendAudio(wrapped);
                if (++sendCount <= 3 || sendCount % 50 == 0) {
                    VoiceAudioEngine.log("Sent audio #" + sendCount + ": " + pcmFrame.length + "B PCM -> " + wrapped.length + "B (" + (OpusCodec.isAvailable() ? "Opus" : "raw") + ")");
                }
            }
            catch (Exception e) {
                if (sendCount % 600 != 0) break block7;
                VoiceAudioEngine.log("Send error: " + e.getMessage());
            }
        }
    }

    private static void pollUDPAudio() {
        byte[] data;
        while ((data = VoiceUDPClient.pollAudio()) != null) {
            byte[] monoPcmBytes;
            if (data.length <= 29) continue;
            byte mode = data[0];
            long uuidHi = VoiceAudioEngine.readLong(data, 1);
            long uuidLo = VoiceAudioEngine.readLong(data, 9);
            long speakerKey = uuidHi ^ uuidLo;
            float spkX = VoiceAudioEngine.readFloat(data, 17);
            float spkY = VoiceAudioEngine.readFloat(data, 21);
            float spkZ = VoiceAudioEngine.readFloat(data, 25);
            byte[] codecAndAudio = new byte[data.length - 29];
            System.arraycopy(data, 29, codecAndAudio, 0, codecAndAudio.length);
            Object dec = speakerDecoders.get(speakerKey);
            if (dec == null && (dec = OpusCodec.createDecoder()) != null) {
                speakerDecoders.put(speakerKey, dec);
            }
            if ((monoPcmBytes = OpusCodec.unwrapReceivedWith(dec, codecAndAudio)).length == 0) continue;
            short[] monoPcm = new short[monoPcmBytes.length / 2];
            for (int i = 0; i < monoPcm.length; ++i) {
                monoPcm[i] = (short)(monoPcmBytes[i * 2] & 0xFF | monoPcmBytes[i * 2 + 1] << 8);
            }
            boolean spatial = mode == 0;
            alPlayback.queueAudio(speakerKey, monoPcm, spatial, spkX, spkY, spkZ);
        }
    }

    private static long readLong(byte[] data, int off) {
        return (long)(data[off] & 0xFF) << 56 | (long)(data[off + 1] & 0xFF) << 48 | (long)(data[off + 2] & 0xFF) << 40 | (long)(data[off + 3] & 0xFF) << 32 | (long)(data[off + 4] & 0xFF) << 24 | (long)(data[off + 5] & 0xFF) << 16 | (long)(data[off + 6] & 0xFF) << 8 | (long)(data[off + 7] & 0xFF);
    }

    private static void startCapture() {
        if (micLine == null || capturing) {
            return;
        }
        capturing = true;
        VoiceAudioEngine.log("*** PTT PRESSED ***");
        micLine.start();
        new Thread(() -> {
            byte[] buffer = new byte[640];
            while (capturing) {
                int bytesRead = micLine.read(buffer, 0, buffer.length);
                if (bytesRead <= 0 || !VoiceAudioEngine.isAboveNoiseGate(buffer, bytesRead)) continue;
                byte[] frame = new byte[bytesRead];
                System.arraycopy(buffer, 0, frame, 0, bytesRead);
                frame = VoiceAudioEngine.applyAGC(frame, true);
                VoiceAudioEngine.sendAudio(frame);
            }
        }, "VoiceCapture").start();
    }

    private static void stopCapture() {
        if (!capturing) {
            return;
        }
        capturing = false;
        VoiceAudioEngine.log("*** PTT RELEASED ***");
        if (micLine != null) {
            micLine.stop();
            micLine.flush();
        }
    }

    public static void setMainToggle(Object toggle) {
        mainModuleToggle = toggle;
        if (toggle != null) {
            for (Method m : toggle.getClass().getMethods()) {
                if (!m.getName().equals("d") || m.getParameterCount() != 0 || !m.getReturnType().getName().contains("Comparable")) continue;
                mainToggleGetMethod = m;
                break;
            }
        }
    }

    public static void setVoiceModule(Object enableToggle, Object volumeSlider) {
        if (enableToggle != null) {
            voiceEnableToggle = enableToggle;
        }
        if (volumeSlider != null) {
            voiceVolumeSlider = volumeSlider;
        }
        if (enableToggle != null) {
            try {
                for (Method m : enableToggle.getClass().getMethods()) {
                    if (!m.getName().equals("d") || m.getParameterCount() != 0 || !m.getReturnType().getName().contains("Comparable")) continue;
                    toggleGetMethod = m;
                    break;
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (volumeSlider != null) {
            try {
                for (Method m : volumeSlider.getClass().getMethods()) {
                    if (!m.getName().equals("d") || m.getParameterCount() != 0 || !m.getReturnType().getName().contains("Comparable")) continue;
                    sliderGetMethod = m;
                    break;
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        VoiceAudioEngine.log("UI controls linked: enable=" + (enableToggle != null) + " volume=" + (volumeSlider != null));
    }

    public static void setCcPttKeybind(Object keybind) {
        ccPttKeybind = keybind;
        if (keybind != null) {
            lastCcKeyCode = VoiceAudioEngine.readKeyCode(keybind);
            VoiceAudioEngine.log("CC PTT keybind linked (keyCode=" + lastCcKeyCode + ")");
        }
    }

    public static void setPrimaryPttKeybind(Object keybind) {
        pttKeybind = keybind;
        ccPttKeybind = keybind;
        try {
            pttNField = VoiceAudioEngine.findFieldInHierarchy(keybind.getClass(), "n");
            if (pttNField != null) {
                pttNField.setAccessible(true);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        try {
            for (Method m : keybind.getClass().getMethods()) {
                Class<?> rt;
                if (!m.getName().equals("d") || m.getParameterCount() != 0 || (rt = m.getReturnType()) == Void.TYPE || rt == Boolean.TYPE || rt == List.class) continue;
                keybindGetMethod = m;
                VoiceAudioEngine.log("Found keybind d() method: returns " + rt.getSimpleName());
                break;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        int code = VoiceAudioEngine.readKeyCodeFull(keybind);
        if (code != 0) {
            pttKeyCode = code;
        }
        lastMcKeyCode = pttKeyCode;
        lastCcKeyCode = pttKeyCode;
        VoiceAudioEngine.log("Primary PTT keybind set (keyCode=" + pttKeyCode + ", class=" + keybind.getClass().getSimpleName() + ")");
    }

    private static void syncUIControls() {
        boolean newEnabled;
        boolean checkboxVal = enabled;
        if (toggleGetMethod != null && voiceEnableToggle != null) {
            try {
                Object val = toggleGetMethod.invoke(voiceEnableToggle, new Object[0]);
                if (val instanceof Boolean) {
                    checkboxVal = (Boolean)val;
                } else if (val != null) {
                    checkboxVal = "true".equalsIgnoreCase(val.toString());
                }
            }
            catch (Exception val) {
                // empty catch block
            }
        }
        boolean mainVal = enabled;
        if (mainToggleGetMethod != null && mainModuleToggle != null) {
            try {
                Object val = mainToggleGetMethod.invoke(mainModuleToggle, new Object[0]);
                if (val instanceof Boolean) {
                    mainVal = (Boolean)val;
                } else if (val != null) {
                    mainVal = "true".equalsIgnoreCase(val.toString());
                }
            }
            catch (Exception val) {
                // empty catch block
            }
        }
        boolean bl = newEnabled = checkboxVal && mainVal;
        if (newEnabled != enabled) {
            enabled = newEnabled;
            settingsDirty = true;
            VoiceAudioEngine.log("Voice " + (enabled ? "enabled" : "disabled") + " via UI toggle");
            try {
                if (voiceEnableToggle != null) {
                    VoiceAudioEngine.syncComparableFields(voiceEnableToggle, enabled);
                }
                if (mainModuleToggle != null) {
                    VoiceAudioEngine.syncComparableFields(mainModuleToggle, enabled);
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
            if (!enabled) {
                if (capturing) {
                    VoiceAudioEngine.stopCapture();
                }
                VoiceUDPClient.disconnect();
                VoiceAudioEngine.log("UDP disconnected (voice disabled)");
            }
        }
        if (sliderGetMethod != null && voiceVolumeSlider != null) {
            try {
                float newVol;
                Object val = sliderGetMethod.invoke(voiceVolumeSlider, new Object[0]);
                if (val instanceof Number && Math.abs((newVol = ((Number)val).floatValue() / 100.0f) - volume) > 0.01f) {
                    volume = Math.max(0.0f, Math.min(1.0f, newVol));
                    if (alPlayback != null) {
                        alPlayback.setVolume(volume);
                    }
                    settingsDirty = true;
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        VoiceAudioEngine.syncKeybinds();
        VoiceAudioEngine.syncDeviceComboboxes();
        if (settingsDirty) {
            settingsDirty = false;
            VoiceAudioEngine.saveSettings();
        }
    }

    private static void syncKeybinds() {
        int currentKey;
        if (pttKeybind == null) {
            return;
        }
        int dVal = 0;
        if (keybindGetMethod != null) {
            try {
                Object val = keybindGetMethod.invoke(pttKeybind, new Object[0]);
                if (val instanceof Number) {
                    dVal = ((Number)val).intValue();
                }
            }
            catch (Exception val) {
                // empty catch block
            }
        }
        int pVal = 0;
        try {
            Field pField = VoiceAudioEngine.findFieldInHierarchy(pttKeybind.getClass(), "p");
            if (pField != null) {
                pField.setAccessible(true);
                pVal = pField.getInt(pttKeybind);
            }
        }
        catch (Exception pField) {
            // empty catch block
        }
        int n = currentKey = dVal != 0 ? dVal : pVal;
        if (currentKey != 0 && currentKey != lastMcKeyCode) {
            VoiceAudioEngine.log("PTT keybind changed: " + lastMcKeyCode + " -> " + currentKey + " (d()=" + dVal + " p=" + pVal + ")");
            pttKeyCode = currentKey;
            lastMcKeyCode = currentKey;
            lastCcKeyCode = currentKey;
            if (pVal != currentKey) {
                VoiceAudioEngine.writeKeyCode(pttKeybind, currentKey);
            }
            if (dVal != currentKey) {
                VoiceAudioEngine.syncComparableFields(pttKeybind, currentKey);
            }
            settingsDirty = true;
        } else if (dVal != 0 && pVal != 0 && dVal != pVal) {
            int newer = dVal != lastCcKeyCode ? dVal : pVal;
            VoiceAudioEngine.log("Keybind d()/p desync: d()=" + dVal + " p=" + pVal + " -> fixing to " + newer);
            pttKeyCode = newer;
            VoiceAudioEngine.writeKeyCode(pttKeybind, newer);
            VoiceAudioEngine.syncComparableFields(pttKeybind, newer);
            lastMcKeyCode = newer;
            lastCcKeyCode = newer;
            settingsDirty = true;
        }
    }

    private static void syncComparableFields(Object keybind, Object value) {
        for (Class<?> cls = keybind.getClass(); cls != null && !cls.getName().equals("java.lang.Object"); cls = cls.getSuperclass()) {
            for (Field ff : cls.getDeclaredFields()) {
                if (Modifier.isStatic(ff.getModifiers())) continue;
                try {
                    String typeName = ff.getType().getName();
                    if (!typeName.contains("Comparable") && !typeName.equals("java.lang.Object")) continue;
                    ff.setAccessible(true);
                    Object existing = ff.get(keybind);
                    if (existing != null && !(existing instanceof Integer)) continue;
                    ff.set(keybind, value);
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        }
    }

    private static int readKeyCodeFull(Object keybind) {
        if (keybindGetMethod != null) {
            try {
                int code;
                Object val = keybindGetMethod.invoke(keybind, new Object[0]);
                if (val instanceof Number && (code = ((Number)val).intValue()) != 0) {
                    return code;
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        return VoiceAudioEngine.readKeyCode(keybind);
    }

    private static int readKeyCode(Object keybind) {
        for (String name : new String[]{"p", "k", "i"}) {
            try {
                Field f = VoiceAudioEngine.findFieldInHierarchy(keybind.getClass(), name);
                if (f == null || f.getType() != Integer.TYPE) continue;
                f.setAccessible(true);
                int val = f.getInt(keybind);
                if (val == 0) continue;
                return val;
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        return 0;
    }

    private static void writeKeyCode(Object keybind, int code) {
        for (String name : new String[]{"k", "p", "i"}) {
            try {
                Field f = VoiceAudioEngine.findFieldInHierarchy(keybind.getClass(), name);
                if (f == null || f.getType() != Integer.TYPE) continue;
                f.setAccessible(true);
                f.setInt(keybind, code);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    private static Field findFieldInHierarchy(Class<?> cls, String name) {
        while (cls != null && !cls.getName().equals("java.lang.Object")) {
            try {
                return cls.getDeclaredField(name);
            }
            catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    public static void loadSettings() {
        block14: {
            if (settingsLoaded) {
                return;
            }
            settingsLoaded = true;
            try {
                File f = new File(SETTINGS_FILE);
                if (f.exists()) {
                    String enStr;
                    String volStr;
                    int savedKey;
                    try (FileInputStream fis = new FileInputStream(f);){
                        savedProps.load(fis);
                    }
                    String keyStr = savedProps.getProperty("ptt_key");
                    if (keyStr != null && (savedKey = Integer.parseInt(keyStr)) != 0) {
                        pttKeyCode = savedKey;
                        lastMcKeyCode = savedKey;
                        lastCcKeyCode = savedKey;
                        VoiceAudioEngine.log("Loaded saved PTT key: " + savedKey);
                    }
                    if ((volStr = savedProps.getProperty("volume")) != null) {
                        float rawVol = Float.parseFloat(volStr);
                        if (rawVol <= 10.0f && rawVol > 0.0f) {
                            rawVol = 100.0f;
                        }
                        volume = Math.max(0.0f, Math.min(1.0f, rawVol / 100.0f));
                        VoiceAudioEngine.log("Loaded saved volume: " + volume * 100.0f);
                    }
                    if ((enStr = savedProps.getProperty("enabled")) != null) {
                        enabled = Boolean.parseBoolean(enStr);
                        VoiceAudioEngine.log("Loaded saved enabled: " + enabled);
                    }
                    VoiceAudioEngine.log("Settings loaded from " + SETTINGS_FILE);
                    break block14;
                }
                VoiceAudioEngine.log("No saved settings, using defaults");
            }
            catch (Exception e) {
                VoiceAudioEngine.log("Load settings error: " + e.getMessage());
            }
        }
    }

    public static void saveSettings() {
        try {
            File dir = new File(SETTINGS_FILE).getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            savedProps.setProperty("ptt_key", String.valueOf(pttKeyCode));
            savedProps.setProperty("volume", String.valueOf(Math.round(volume * 100.0f)));
            savedProps.setProperty("enabled", String.valueOf(enabled));
            if (selectedInputIndex >= 0 && selectedInputIndex < inputDevices.length) {
                savedProps.setProperty("input_device", inputDevices[selectedInputIndex].getName());
            }
            if (selectedOutputIndex >= 0 && selectedOutputIndex < outputDevices.length) {
                savedProps.setProperty("output_device", outputDevices[selectedOutputIndex].getName());
            }
            try (FileOutputStream fos = new FileOutputStream(SETTINGS_FILE);){
                savedProps.store(fos, "Cosmic Voice Chat Settings");
            }
            try {
                VoiceModuleHelper.saveConfig();
            }
            catch (Exception exception) {}
        }
        catch (Exception e) {
            VoiceAudioEngine.log("Save settings error: " + e.getMessage());
        }
    }

    public static int getSavedKeyCode() {
        String keyStr;
        if (!settingsLoaded) {
            VoiceAudioEngine.loadSettings();
        }
        if ((keyStr = savedProps.getProperty("ptt_key")) != null) {
            try {
                int k = Integer.parseInt(keyStr);
                if (k != 0) {
                    return k;
                }
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        return pttKeyCode;
    }

    public static float getSavedVolume() {
        if (!settingsLoaded) {
            VoiceAudioEngine.loadSettings();
        }
        return volume * 100.0f;
    }

    public static boolean getSavedEnabled() {
        if (!settingsLoaded) {
            VoiceAudioEngine.loadSettings();
        }
        return enabled;
    }

    private static void updateListenerPosition(Object wbInstance) {
        block19: {
            try {
                Field dField;
                if (!playerFieldsResolved) {
                    playerFieldsResolved = true;
                    try {
                        dField = wbInstance.getClass().getDeclaredField("D");
                        dField.setAccessible(true);
                        Object entity = dField.get(wbInstance);
                        if (entity != null) {
                            Class<?> entityClass = entity.getClass();
                            posXField = VoiceAudioEngine.findFieldInHierarchy(entityClass, "t");
                            posYField = VoiceAudioEngine.findFieldInHierarchy(entityClass, "U");
                            posZField = VoiceAudioEngine.findFieldInHierarchy(entityClass, "N");
                            yawField = VoiceAudioEngine.findFieldInHierarchy(entityClass, "ae");
                            if (posXField != null) {
                                posXField.setAccessible(true);
                            }
                            if (posYField != null) {
                                posYField.setAccessible(true);
                            }
                            if (posZField != null) {
                                posZField.setAccessible(true);
                            }
                            if (yawField != null) {
                                yawField.setAccessible(true);
                            }
                            if (posXField != null && posYField != null && posZField != null) {
                                playerEntity = entity;
                                double x = posXField.getDouble(entity);
                                double y = posYField.getDouble(entity);
                                double z = posZField.getDouble(entity);
                                float yaw = yawField != null ? yawField.getFloat(entity) : 0.0f;
                                VoiceAudioEngine.log("Player entity found: wb.D (" + entityClass.getSimpleName() + ") pos=" + (int)x + "," + (int)y + "," + (int)z + " yaw=" + (int)yaw);
                            } else {
                                VoiceAudioEngine.log("Entity fields not found: posX=" + (posXField != null) + " posY=" + (posYField != null) + " posZ=" + (posZField != null));
                            }
                        } else {
                            playerFieldsResolved = false;
                        }
                    }
                    catch (Exception e) {
                        VoiceAudioEngine.log("Entity resolve error: " + e.getMessage());
                        playerFieldsResolved = false;
                    }
                }
                if (playerEntity == null || posXField == null) break block19;
                try {
                    dField = wbInstance.getClass().getDeclaredField("D");
                    dField.setAccessible(true);
                    Object currentEntity = dField.get(wbInstance);
                    if (currentEntity != null && currentEntity != playerEntity) {
                        playerEntity = currentEntity;
                    }
                    if (playerEntity != null) {
                        listenerX = posXField.getDouble(playerEntity);
                        listenerY = posYField.getDouble(playerEntity);
                        listenerZ = posZField.getDouble(playerEntity);
                        if (yawField != null) {
                            listenerYaw = yawField.getFloat(playerEntity);
                        }
                        if (alPlayback != null && alPlayback.isInitialized()) {
                            alPlayback.setListenerPosition((float)listenerX, (float)listenerY, (float)listenerZ, listenerYaw);
                        }
                    }
                }
                catch (Exception exception) {}
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    private static float readFloat(byte[] data, int off) {
        return Float.intBitsToFloat(data[off] & 0xFF | (data[off + 1] & 0xFF) << 8 | (data[off + 2] & 0xFF) << 16 | (data[off + 3] & 0xFF) << 24);
    }

    private static void installHeaderMap() {
        if (headerMapInstalled || wbRef == null) {
            return;
        }
        headerMapInstalled = true;
        try {
            ClassLoader cl = wbRef.getClass().getClassLoader();
            Class<?> ugClass = Class.forName("cosmicclient.ug", true, cl);
            Object ug = ugClass.getMethod("i", new Class[0]).invoke(null, new Object[0]);
            if (ug == null) {
                headerMapInstalled = false;
                return;
            }
            Object ix = ug.getClass().getMethod("F", new Class[0]).invoke(ug, new Object[0]);
            if (ix == null) {
                headerMapInstalled = false;
                return;
            }
            Field oField = ix.getClass().getDeclaredField("o");
            oField.setAccessible(true);
            Map originalMap = (Map)oField.get(ix);
            speakingHeaderMap = new SpeakingHeaderMap(originalMap);
            oField.set(ix, speakingHeaderMap);
            VoiceAudioEngine.log("Installed SpeakingHeaderMap on IX.o (original had " + String.valueOf(originalMap != null ? Integer.valueOf(originalMap.size()) : "null") + " entries)");
        }
        catch (Exception e) {
            VoiceAudioEngine.log("Header map install error: " + e.getMessage());
            headerMapInstalled = false;
        }
    }

    public static void setPlayerSpeaking(String playerName, boolean speaking) {
        if (speakingHeaderMap != null) {
            speakingHeaderMap.setSpeaking(playerName, speaking);
        }
    }

    private static void showActionBar(Object wbInstance, String message, int ticks) {
        try {
            if (!actionBarResolved) {
                actionBarResolved = true;
                for (Field f : wbInstance.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Object candidate = f.get(wbInstance);
                    if (candidate == null) continue;
                    for (Method m : candidate.getClass().getDeclaredMethods()) {
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length != 2 || params[0] != String.class || params[1] != Boolean.TYPE || m.getReturnType() != Void.TYPE) continue;
                        guiIngame = candidate;
                        recordPlayingMethod = m;
                        recordPlayingMethod.setAccessible(true);
                        for (Field tf : candidate.getClass().getDeclaredFields()) {
                            if (tf.getType() != Integer.TYPE || Modifier.isStatic(tf.getModifiers())) continue;
                            tf.setAccessible(true);
                            int val = tf.getInt(candidate);
                            recordPlayingMethod.invoke(candidate, "test", true);
                            int after = tf.getInt(candidate);
                            if (after != 60) continue;
                            recordTimerField = tf;
                            break;
                        }
                        VoiceAudioEngine.log("Found action bar: " + f.getName() + "." + m.getName() + " timer=" + (recordTimerField != null ? recordTimerField.getName() : "?"));
                        break;
                    }
                    if (guiIngame != null) break;
                }
            }
            if (recordPlayingMethod != null && guiIngame != null) {
                recordPlayingMethod.invoke(guiIngame, message, true);
                if (recordTimerField != null) {
                    recordTimerField.setInt(guiIngame, ticks);
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private static byte[] applyAGC(byte[] pcm, boolean isCapture) {
        float currentGain;
        int samples = pcm.length / 2;
        long sumSquares = 0L;
        for (int i = 0; i < pcm.length - 1; i += 2) {
            short s = (short)(pcm[i] & 0xFF | pcm[i + 1] << 8);
            sumSquares += (long)s * (long)s;
        }
        float rms = (float)Math.sqrt((double)sumSquares / (double)samples);
        if (rms < 10.0f) {
            return pcm;
        }
        float desiredGain = 4000.0f / rms;
        desiredGain = Math.max(0.2f, Math.min(8.0f, desiredGain));
        float f = currentGain = isCapture ? captureGain : 1.0f;
        currentGain = desiredGain < currentGain ? (currentGain += (desiredGain - currentGain) * 0.1f) : (currentGain += (desiredGain - currentGain) * 0.02f);
        if (isCapture) {
            captureGain = currentGain;
        }
        byte[] result = new byte[pcm.length];
        for (int i = 0; i < pcm.length - 1; i += 2) {
            short s = (short)(pcm[i] & 0xFF | pcm[i + 1] << 8);
            int amplified = (int)((float)s * currentGain);
            amplified = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, amplified));
            result[i] = (byte)(amplified & 0xFF);
            result[i + 1] = (byte)(amplified >> 8 & 0xFF);
        }
        return result;
    }

    private static boolean isAboveNoiseGate(byte[] pcm, int length) {
        long sumSquares = 0L;
        int samples = length / 2;
        for (int i = 0; i < length - 1; i += 2) {
            short s = (short)(pcm[i] & 0xFF | pcm[i + 1] << 8);
            sumSquares += (long)s * (long)s;
        }
        double rms = Math.sqrt((double)sumSquares / (double)samples);
        if (rms > 80.0) {
            noiseGateHoldFrames = 15;
            return true;
        }
        if (noiseGateHoldFrames > 0) {
            --noiseGateHoldFrames;
            return true;
        }
        return false;
    }

    private static String getServerIp() {
        if (detectedServerIp != null) {
            return detectedServerIp;
        }
        if (wbRef == null) {
            return "127.0.0.1";
        }
        try {
            Object netHandler = wbRef.getClass().getMethod("L", new Class[0]).invoke(wbRef, new Object[0]);
            if (netHandler == null) {
                return "127.0.0.1";
            }
            for (Field f : netHandler.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object candidate = f.get(netHandler);
                if (candidate == null) continue;
                for (Field sf : candidate.getClass().getDeclaredFields()) {
                    String ip;
                    if (!sf.getType().getName().contains("SocketAddress") && !sf.getType().getName().contains("InetSocketAddress")) continue;
                    sf.setAccessible(true);
                    Object addr = sf.get(candidate);
                    if (!(addr instanceof InetSocketAddress) || (ip = ((InetSocketAddress)addr).getHostString()) == null || ip.isEmpty()) continue;
                    detectedServerIp = ip;
                    VoiceAudioEngine.log("Detected server IP: " + ip);
                    return ip;
                }
            }
        }
        catch (Exception e) {
            VoiceAudioEngine.log("Server IP detection error: " + e.getMessage());
        }
        return "127.0.0.1";
    }

    private static UUID getPlayerUUID() {
        Object session;
        if (cachedUUID != null) {
            return cachedUUID;
        }
        if (wbRef == null) {
            return null;
        }
        try {
            Field dField = wbRef.getClass().getDeclaredField("D");
            dField.setAccessible(true);
            Object entity = dField.get(wbRef);
            if (entity != null) {
                UUID uuid;
                for (Method method : entity.getClass().getMethods()) {
                    if (method.getReturnType() != UUID.class || method.getParameterCount() != 0 || (uuid = (UUID)method.invoke(entity, new Object[0])) == null) continue;
                    cachedUUID = uuid;
                    VoiceAudioEngine.log("UUID from entity." + method.getName() + "(): " + String.valueOf(uuid));
                    return uuid;
                }
                for (AccessibleObject accessibleObject : entity.getClass().getFields()) {
                    if (((Field)accessibleObject).getType() != UUID.class || (uuid = (UUID)((Field)accessibleObject).get(entity)) == null) continue;
                    cachedUUID = uuid;
                    VoiceAudioEngine.log("UUID from entity field " + ((Field)accessibleObject).getName() + ": " + String.valueOf(uuid));
                    return uuid;
                }
            }
        }
        catch (Exception dField) {
            // empty catch block
        }
        try {
            Field aXField2 = wbRef.getClass().getDeclaredField("aX");
            aXField2.setAccessible(true);
            session = aXField2.get(wbRef);
            if (session != null) {
                Method getIdMethod;
                UUID uUID;
                Method aMethod = session.getClass().getDeclaredMethod("a", new Class[0]);
                aMethod.setAccessible(true);
                Object gameProfile = aMethod.invoke(session, new Object[0]);
                if (gameProfile != null && (uUID = (UUID)(getIdMethod = gameProfile.getClass().getMethod("getId", new Class[0])).invoke(gameProfile, new Object[0])) != null) {
                    cachedUUID = uUID;
                    VoiceAudioEngine.log("UUID from GameProfile: " + String.valueOf(uUID));
                    return uUID;
                }
            }
        }
        catch (Exception aXField2) {
            // empty catch block
        }
        try {
            Field aXField = wbRef.getClass().getDeclaredField("aX");
            aXField.setAccessible(true);
            session = aXField.get(wbRef);
            if (session != null) {
                Field cField = session.getClass().getDeclaredField("c");
                cField.setAccessible(true);
                Object uuidStr = cField.get(session);
                if (uuidStr instanceof String && !((String)uuidStr).isEmpty()) {
                    UUID uuid;
                    cachedUUID = uuid = UUID.fromString((String)uuidStr);
                    VoiceAudioEngine.log("UUID from session.c: " + String.valueOf(uuid));
                    return uuid;
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return null;
    }

    public static void setProxRange(float range) {
        VoiceAudioEngine.log("Prox range updated: " + range + " blocks");
    }

    public static void setEnabled(boolean e) {
        enabled = e;
        VoiceAudioEngine.log("Voice " + (e ? "enabled" : "disabled"));
    }

    public static void setVolume(float v) {
        volume = Math.max(0.0f, Math.min(2.0f, v));
        if (alPlayback != null) {
            alPlayback.setVolume(volume);
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static float getVolume() {
        return volume;
    }

    private static void enumerateDevices() {
        int i;
        ArrayList<Mixer.Info> inputs = new ArrayList<Mixer.Info>();
        ArrayList<Mixer.Info> outputs = new ArrayList<Mixer.Info>();
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, CAPTURE_FORMAT);
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                if (mixer.isLineSupported(micInfo)) {
                    inputs.add(info);
                }
                outputs.add(info);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        inputDevices = inputs.toArray(new Mixer.Info[0]);
        outputDevices = outputs.toArray(new Mixer.Info[0]);
        VoiceAudioEngine.log("Input devices (" + inputDevices.length + "):");
        for (i = 0; i < inputDevices.length; ++i) {
            VoiceAudioEngine.log("  [" + i + "] " + inputDevices[i].getName());
        }
        VoiceAudioEngine.log("Output devices (" + outputDevices.length + "):");
        for (i = 0; i < outputDevices.length; ++i) {
            VoiceAudioEngine.log("  [" + i + "] " + outputDevices[i].getName());
        }
    }

    private static void openMic(int index) {
        try {
            if (micLine != null) {
                try { micLine.stop(); } catch (Throwable ignored) {}
                try { micLine.close(); } catch (Throwable ignored) {}
                micLine = null;
            }
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, CAPTURE_FORMAT);
            if (index >= 0 && index < inputDevices.length) {
                Mixer mixer = AudioSystem.getMixer(inputDevices[index]);
                if (!mixer.isLineSupported(micInfo)) {
                    VoiceAudioEngine.log("Mic: device [" + index + "] " + inputDevices[index].getName() + " does not support 16kHz capture, trying system default");
                    if (AudioSystem.isLineSupported(micInfo)) {
                        micLine = (TargetDataLine)AudioSystem.getLine(micInfo);
                    }
                } else {
                    micLine = (TargetDataLine)mixer.getLine(micInfo);
                    VoiceAudioEngine.log("Mic: using device [" + index + "] " + inputDevices[index].getName());
                }
            } else {
                if (AudioSystem.isLineSupported(micInfo)) {
                    micLine = (TargetDataLine)AudioSystem.getLine(micInfo);
                    VoiceAudioEngine.log("Mic: using system default");
                }
            }
            if (micLine != null) {
                micLine.open(CAPTURE_FORMAT, 2560);
            } else {
                VoiceAudioEngine.log("Mic: no supported audio capture device available (mic input disabled)");
            }
        }
        catch (Exception e) {
            VoiceAudioEngine.log("Mic open error: " + e.getMessage());
        }
    }

    public static int getInputDeviceCount() {
        return inputDevices.length;
    }

    public static int getOutputDeviceCount() {
        return outputDevices.length;
    }

    public static int getSavedInputIndex() {
        if (!settingsLoaded) {
            VoiceAudioEngine.loadSettings();
        }
        return selectedInputIndex;
    }

    public static int getSavedOutputIndex() {
        if (!settingsLoaded) {
            VoiceAudioEngine.loadSettings();
        }
        return selectedOutputIndex;
    }

    public static String getInputDeviceName(int idx) {
        return idx >= 0 && idx < inputDevices.length ? inputDevices[idx].getName() : "Default";
    }

    public static String getOutputDeviceName(int idx) {
        return idx >= 0 && idx < outputDevices.length ? outputDevices[idx].getName() : "Default";
    }

    public static void setDeviceComboboxes(Object micCombo, Object spkCombo) {
        micCombobox = micCombo;
        spkCombobox = spkCombo;
        if (micCombo != null && comboGetMethod == null) {
            for (Method m : micCombo.getClass().getMethods()) {
                Class<?> rt;
                if (!m.getName().equals("d") || m.getParameterCount() != 0 || (rt = m.getReturnType()) == Void.TYPE || rt == Boolean.TYPE || rt == List.class) continue;
                comboGetMethod = m;
                break;
            }
        }
        lastMicDevice = VoiceAudioEngine.getInputDeviceName(selectedInputIndex);
        lastSpkDevice = VoiceAudioEngine.getOutputDeviceName(selectedOutputIndex);
        VoiceAudioEngine.log("Device comboboxes linked");
    }

    private static void syncDeviceComboboxes() {
        block10: {
            int i;
            String name;
            Object val2;
            block9: {
                if (comboGetMethod == null) {
                    return;
                }
                if (micCombobox != null) {
                    try {
                        val2 = comboGetMethod.invoke(micCombobox, new Object[0]);
                        if (!(val2 instanceof String) || (name = (String)val2).equals(lastMicDevice) || name.isEmpty()) break block9;
                        lastMicDevice = name;
                        for (i = 0; i < inputDevices.length; ++i) {
                            if (!inputDevices[i].getName().equals(name)) continue;
                            VoiceAudioEngine.log("Mic device changed -> " + name);
                            selectedInputIndex = i;
                            VoiceAudioEngine.openMic(i);
                            settingsDirty = true;
                            break;
                        }
                    }
                    catch (Exception exVal) {
                        // empty catch block
                    }
                }
            }
            if (spkCombobox != null) {
                try {
                    val2 = comboGetMethod.invoke(spkCombobox, new Object[0]);
                    if (!(val2 instanceof String) || (name = (String)val2).equals(lastSpkDevice) || name.isEmpty()) break block10;
                    lastSpkDevice = name;
                    for (i = 0; i < outputDevices.length; ++i) {
                        if (!outputDevices[i].getName().equals(name)) continue;
                        VoiceAudioEngine.log("Speaker device changed -> " + name);
                        selectedOutputIndex = i;
                        settingsDirty = true;
                        break;
                    }
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        }
    }

    private static void log(String msg) {
        System.out.println("[VoiceAudio] " + msg);
    }

    static {
        capturing = false;
        initialized = false;
        enabled = true;
        volume = 1.0f;
        wasPttPressed = false;
        pttKeybind = null;
        pttNField = null;
        pttKeyCode = 0;
        tickCount = 0;
        wbRef = null;
        networkHandler = null;
        sendPacketMethod = null;
        payloadPacketCtor = null;
        packetBufferCtor = null;
        unpooledBuffer = null;
        writeBytesMethod = null;
        ccLoader = null;
        audioRecvThread = null;
        inputDevices = new Mixer.Info[0];
        outputDevices = new Mixer.Info[0];
        selectedInputIndex = 0;
        selectedOutputIndex = 0;
        sendCount = 0;
        speakerDecoders = new ConcurrentHashMap<Long, Object>();
        voiceEnableToggle = null;
        mainModuleToggle = null;
        mainToggleGetMethod = null;
        voiceVolumeSlider = null;
        toggleGetMethod = null;
        sliderGetMethod = null;
        ccPttKeybind = null;
        lastMcKeyCode = 0;
        lastCcKeyCode = 0;
        String appData = System.getenv("APPDATA");
        String dir = appData != null ? appData + "/CosmicVoice" : System.getProperty("user.home") + "/.cosmicvoice";
        SETTINGS_FILE = dir + "/settings.properties";
        savedProps = new Properties();
        settingsLoaded = false;
        settingsDirty = false;
        keybindGetMethod = null;
        playerEntity = null;
        posXField = null;
        posYField = null;
        posZField = null;
        yawField = null;
        playerFieldsResolved = false;
        speakingHeaderMap = null;
        headerMapInstalled = false;
        guiIngame = null;
        recordPlayingMethod = null;
        actionBarResolved = false;
        recordTimerField = null;
        noiseGateHoldFrames = 0;
        captureGain = 1.0f;
        detectedServerIp = null;
        cachedUUID = null;
        micCombobox = null;
        spkCombobox = null;
        comboGetMethod = null;
        lastMicDevice = "";
        lastSpkDevice = "";
    }
}

