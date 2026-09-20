package com.cosmic.launcher.agent;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class OpusCodec {
    public static final byte CODEC_RAW_PCM = 0;
    public static final byte CODEC_OPUS = 1;
    private static Object encoder;
    private static Object decoder;
    private static boolean initialized;
    private static boolean available;
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    private static final int FRAME_SIZE = 320;
    private static final int MAX_PACKET_SIZE = 1024;
    private static final int OPUS_APPLICATION_VOIP = 2048;
    private static final int OPUS_SET_BITRATE_REQUEST = 4002;
    private static final int OPUS_SET_COMPLEXITY_REQUEST = 4010;
    private static final int OPUS_SET_SIGNAL_REQUEST = 4024;
    private static final int OPUS_SIGNAL_VOICE = 3001;
    private static final int OPUS_SET_INBAND_FEC_REQUEST = 4012;
    private static Object opusInstance;
    private static Method encodeMethod;
    private static Method decodeMethod;
    private static Method ctlMethod;
    private static Method encoderDestroyMethod;
    private static Method decoderDestroyMethod;

    public static boolean init() {
        if (initialized) {
            return available;
        }
        initialized = true;
        try {
            Class<?> opusLibClass = Class.forName("club.minnced.opus.util.OpusLibrary");
            opusLibClass.getMethod("loadFromJar", new Class[0]).invoke(null, new Object[0]);
            OpusCodec.log("Opus native library loaded from jar");
            Class<?> opusClass = Class.forName("tomp2p.opuswrapper.Opus");
            opusInstance = opusClass.getField("INSTANCE").get(null);
            OpusCodec.log("Got Opus.INSTANCE: " + opusInstance.getClass().getName());
            Class<?> pbrClass = Class.forName("com.sun.jna.ptr.PointerByReference");
            Method createEncoder = opusClass.getMethod("opus_encoder_create", Integer.TYPE, Integer.TYPE, Integer.TYPE, IntBuffer.class);
            Method createDecoder = opusClass.getMethod("opus_decoder_create", Integer.TYPE, Integer.TYPE, IntBuffer.class);
            encodeMethod = opusClass.getMethod("opus_encode", pbrClass, ShortBuffer.class, Integer.TYPE, ByteBuffer.class, Integer.TYPE);
            decodeMethod = opusClass.getMethod("opus_decode", pbrClass, byte[].class, Integer.TYPE, ShortBuffer.class, Integer.TYPE, Integer.TYPE);
            for (Method m : opusClass.getMethods()) {
                if (!m.getName().equals("opus_encoder_ctl")) continue;
                ctlMethod = m;
                break;
            }
            encoderDestroyMethod = opusClass.getMethod("opus_encoder_destroy", pbrClass);
            decoderDestroyMethod = opusClass.getMethod("opus_decoder_destroy", pbrClass);
            IntBuffer error = IntBuffer.allocate(1);
            encoder = createEncoder.invoke(opusInstance, 16000, 1, 2048, error);
            if (error.get(0) != 0) {
                OpusCodec.log("Encoder create failed, error code: " + error.get(0));
                return false;
            }
            OpusCodec.log("Opus encoder created");
            if (ctlMethod != null) {
                try {
                    ctlMethod.invoke(opusInstance, encoder, 4002, new Object[]{32000});
                    ctlMethod.invoke(opusInstance, encoder, 4010, new Object[]{7});
                    ctlMethod.invoke(opusInstance, encoder, 4024, new Object[]{3001});
                    ctlMethod.invoke(opusInstance, encoder, 4012, new Object[]{1});
                    OpusCodec.log("Encoder configured: 32kbps, complexity=7, signal=voice, FEC=on");
                }
                catch (Exception e) {
                    OpusCodec.log("Encoder ctl failed (using defaults): " + e.getMessage());
                }
            }
            error.clear();
            error.put(0, 0);
            decoder = createDecoder.invoke(opusInstance, 16000, 1, error);
            if (error.get(0) != 0) {
                OpusCodec.log("Decoder create failed, error code: " + error.get(0));
                return false;
            }
            OpusCodec.log("Opus decoder created");
            available = true;
            OpusCodec.log("Opus codec ready! 16kHz mono, 32kbps, 20ms frames (640B PCM -> ~60-90B Opus)");
            return true;
        }
        catch (Exception e) {
            OpusCodec.log("Opus init failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                OpusCodec.log("  Caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
            }
            available = false;
            return false;
        }
    }

    public static byte[] encode(byte[] pcm) {
        if (!available || encoder == null) {
            return pcm;
        }
        try {
            ByteBuffer pcmBytes = ByteBuffer.allocateDirect(640);
            pcmBytes.order(ByteOrder.LITTLE_ENDIAN);
            int copyLen = Math.min(pcm.length, 640);
            pcmBytes.put(pcm, 0, copyLen);
            while (pcmBytes.position() < 640) {
                pcmBytes.put((byte)0);
            }
            pcmBytes.flip();
            ShortBuffer pcmShort = pcmBytes.asShortBuffer();
            ByteBuffer outBuf = ByteBuffer.allocateDirect(1024);
            int result = (Integer)encodeMethod.invoke(opusInstance, encoder, pcmShort, 320, outBuf, 1024);
            if (result < 0) {
                OpusCodec.log("Encode error: " + result);
                return pcm;
            }
            byte[] out = new byte[result];
            outBuf.get(out);
            return out;
        }
        catch (Exception e) {
            OpusCodec.log("Encode exception: " + e.getMessage());
            return pcm;
        }
    }

    public static byte[] decode(byte[] opus) {
        if (!available || decoder == null) {
            return opus;
        }
        try {
            ByteBuffer pcmBytes = ByteBuffer.allocateDirect(640);
            pcmBytes.order(ByteOrder.LITTLE_ENDIAN);
            ShortBuffer pcmShort = pcmBytes.asShortBuffer();
            int result = (Integer)decodeMethod.invoke(opusInstance, decoder, opus, opus.length, pcmShort, 320, 0);
            if (result < 0) {
                OpusCodec.log("Decode error: " + result);
                return opus;
            }
            byte[] pcm = new byte[result * 2];
            pcmBytes.get(pcm);
            return pcm;
        }
        catch (Exception e) {
            OpusCodec.log("Decode exception: " + e.getMessage());
            return opus;
        }
    }

    public static Object createDecoder() {
        if (!available) {
            return null;
        }
        try {
            Class<?> opusClass = opusInstance.getClass();
            for (Class<?> iface : opusClass.getInterfaces()) {
                try {
                    Method createDec = iface.getMethod("opus_decoder_create", Integer.TYPE, Integer.TYPE, IntBuffer.class);
                    IntBuffer error = IntBuffer.allocate(1);
                    Object dec = createDec.invoke(opusInstance, 16000, 1, error);
                    if (error.get(0) != 0 || dec == null) continue;
                    return dec;
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        }
        catch (Exception e) {
            OpusCodec.log("createDecoder error: " + e.getMessage());
        }
        return null;
    }

    public static byte[] decodeWith(Object decoderInstance, byte[] opus) {
        if (!available || decoderInstance == null) {
            return opus;
        }
        try {
            ByteBuffer pcmBytes = ByteBuffer.allocateDirect(640);
            pcmBytes.order(ByteOrder.LITTLE_ENDIAN);
            ShortBuffer pcmShort = pcmBytes.asShortBuffer();
            int result = (Integer)decodeMethod.invoke(opusInstance, decoderInstance, opus, opus.length, pcmShort, 320, 0);
            if (result < 0) {
                return opus;
            }
            byte[] pcm = new byte[result * 2];
            pcmBytes.get(pcm);
            return pcm;
        }
        catch (Exception e) {
            return opus;
        }
    }

    public static byte[] unwrapReceivedWith(Object decoderInstance, byte[] data) {
        if (data.length < 2) {
            return data;
        }
        byte codec = data[0];
        byte[] audioData = new byte[data.length - 1];
        System.arraycopy(data, 1, audioData, 0, audioData.length);
        if (codec == 1 && available && decoderInstance != null) {
            return OpusCodec.decodeWith(decoderInstance, audioData);
        }
        if (codec == 1 && available) {
            return OpusCodec.decode(audioData);
        }
        if (codec == 1) {
            return new byte[0];
        }
        return audioData;
    }

    public static byte[] wrapForTransmit(byte[] audioData) {
        if (available) {
            byte[] opus = OpusCodec.encode(audioData);
            byte[] wrapped = new byte[opus.length + 1];
            wrapped[0] = 1;
            System.arraycopy(opus, 0, wrapped, 1, opus.length);
            return wrapped;
        }
        byte[] wrapped = new byte[audioData.length + 1];
        wrapped[0] = 0;
        System.arraycopy(audioData, 0, wrapped, 1, audioData.length);
        return wrapped;
    }

    public static boolean isAvailable() {
        return available;
    }

    public static void destroy() {
        try {
            if (encoder != null && encoderDestroyMethod != null) {
                encoderDestroyMethod.invoke(opusInstance, encoder);
            }
            if (decoder != null && decoderDestroyMethod != null) {
                decoderDestroyMethod.invoke(opusInstance, decoder);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        encoder = null;
        decoder = null;
        available = false;
    }

    private static void log(String msg) {
        System.out.println("[OpusCodec] " + msg);
    }

    static {
        initialized = false;
        available = false;
    }
}

