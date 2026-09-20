package com.cosmic.launcher.agent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

public class PluginChannelHelper {
    private static final String TARGET_VOICE_CHANNEL = "voicechat:auth";
    private static final java.util.Set<String> LOGGED_CHANNELS = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public static void onCustomPayload(Object packet) {
        if (packet == null) {
            return;
        }
        try {
            String channel = extractChannel(packet);
            if (channel == null) {
                return;
            }

            Object packetBuffer = extractData(packet);
            if (packetBuffer == null) {
                return;
            }

            if (TARGET_VOICE_CHANNEL.equals(channel)) {
                handleVoiceAuth(packetBuffer);
            } else if (channel.toLowerCase().contains("decimate") || channel.equals("CC") || channel.toLowerCase().contains("cosmic")) {
                if (LOGGED_CHANNELS.add(channel)) {
                    System.out.println("[CosmicAgent/PluginChannel] Received custom payload on channel: " + channel + " (channel active)");
                }
            }
        } catch (Throwable e) {
            System.err.println("[CosmicAgent/PluginChannel] Payload handle error: " + e.getMessage());
        }
    }

    private static String extractChannel(Object packet) {
        try {
            Method m = packet.getClass().getMethod("a");
            if (m.getReturnType() == String.class) {
                return (String) m.invoke(packet);
            }
        } catch (Throwable ignored) {
        }

        try {
            Field f = packet.getClass().getDeclaredField("b");
            f.setAccessible(true);
            Object val = f.get(packet);
            if (val instanceof String) {
                return (String) val;
            }
        } catch (Throwable ignored) {
        }

        for (Field f : packet.getClass().getDeclaredFields()) {
            if (f.getType() == String.class) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(packet);
                    if (val instanceof String) {
                        return (String) val;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return null;
    }

    private static Object extractData(Object packet) {
        try {
            Method m = packet.getClass().getMethod("b");
            Object res = m.invoke(packet);
            if (res != null) {
                return res;
            }
        } catch (Throwable ignored) {
        }

        try {
            Field f = packet.getClass().getDeclaredField("a");
            f.setAccessible(true);
            Object res = f.get(packet);
            if (res != null) {
                return res;
            }
        } catch (Throwable ignored) {
        }

        for (Field f : packet.getClass().getDeclaredFields()) {
            if (f.getType() != String.class && !f.getType().isPrimitive()) {
                try {
                    f.setAccessible(true);
                    Object res = f.get(packet);
                    if (res != null) {
                        return res;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return null;
    }

    private static void handleVoiceAuth(Object packetBuffer) {
        try {
            byte[] payload = readAllReadableBytes(packetBuffer);
            if (payload == null || payload.length < 36) {
                return;
            }
            int offset = 0;
            byte[] token = new byte[32];
            System.arraycopy(payload, offset, token, 0, 32);
            int port = (payload[offset += 32] & 0xFF) << 8 | payload[offset + 1] & 0xFF;
            int ipLen = (payload[offset += 2] & 0xFF) << 8 | payload[offset + 1] & 0xFF;
            if (ipLen <= 0 || payload.length < (offset += 2) + ipLen) {
                return;
            }
            String host = new String(payload, offset, ipLen, StandardCharsets.UTF_8);
            VoiceUDPClient.setServerEndpoint(host, port);
            VoiceUDPClient.setAuthToken(token);
            System.out.println("[CosmicAgent/PluginChannel] Voice endpoint set (" + host + ":" + port + ")");
        } catch (Throwable e) {
            System.err.println("[CosmicAgent/PluginChannel] Voice auth error: " + e.getMessage());
        }
    }

    private static byte[] readAllReadableBytes(Object buf) {
        if (buf == null) return null;
        try {
            Method readableBytes = buf.getClass().getMethod("readableBytes");
            int readable = (Integer) readableBytes.invoke(buf);
            if (readable <= 0) {
                return null;
            }
            byte[] data = new byte[readable];
            try {
                Method readBytesMethod = buf.getClass().getMethod("readBytes", byte[].class);
                readBytesMethod.invoke(buf, new Object[]{data});
                return data;
            } catch (NoSuchMethodException ignored) {
                Method readerIndexMethod = buf.getClass().getMethod("readerIndex");
                int ri = (Integer) readerIndexMethod.invoke(buf);
                Method getByte = buf.getClass().getMethod("getByte", int.class);
                for (int i = 0; i < readable; ++i) {
                    data[i] = (Byte) getByte.invoke(buf, ri + i);
                }
                return data;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
