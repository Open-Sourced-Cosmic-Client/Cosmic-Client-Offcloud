package com.cosmic.launcher.agent;

import com.cosmic.launcher.agent.VoiceAudioEngine;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VoiceUDPClient {
    private static final int VOICE_PORT = 9876;
    private static DatagramSocket socket;
    private static InetAddress serverAddress;
    private static String serverIp;
    private static volatile int serverPort;
    private static volatile boolean running;
    private static volatile boolean authenticated;
    private static volatile byte[] authToken;
    private static UUID playerUUID;
    private static final ConcurrentLinkedQueue<byte[]> incomingAudio;
    private static volatile long lastPacketReceived;
    private static final long RECONNECT_TIMEOUT_MS = 15000L;
    private static int sequenceNumber;

    public static void setServerEndpoint(String ip, int port) {
        if (ip == null || ip.trim().isEmpty()) {
            return;
        }
        if (port < 1 || port > 65535) {
            return;
        }
        serverIp = ip;
        serverPort = port;
        try {
            serverAddress = InetAddress.getByName(ip);
            VoiceUDPClient.log("Server endpoint received (" + ip + ":" + port + ")");
        }
        catch (Exception e) {
            VoiceUDPClient.log("Failed to resolve server endpoint (" + ip + ":" + port + ") - " + e.getMessage());
        }
    }

    public static void setAuthToken(byte[] token) {
        authToken = token;
        VoiceUDPClient.log("Auth token received (" + (token != null ? token.length : 0) + " bytes)");
        if (running && socket != null && !socket.isClosed()) {
            VoiceUDPClient.sendHello();
        }
    }

    public static boolean connect(String ip, UUID uuid) {
        playerUUID = uuid;
        if (serverIp == null || serverIp.trim().isEmpty()) {
            serverIp = ip;
        }
        try {
            serverAddress = InetAddress.getByName(serverIp);
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            socket = new DatagramSocket();
            socket.setSoTimeout(100);
            running = true;
            authenticated = false;
            lastPacketReceived = System.currentTimeMillis();
            sequenceNumber = 0;
            VoiceUDPClient.sendHello();
            Thread keepalive = new Thread(() -> {
                while (running) {
                    try {
                        Thread.sleep(10000L);
                        long silenceMs = System.currentTimeMillis() - lastPacketReceived;
                        if (silenceMs > 15000L && authenticated) {
                            VoiceUDPClient.log("No packets for " + silenceMs / 1000L + "s - reconnecting...");
                            try {
                                if (socket != null && !socket.isClosed()) {
                                    socket.close();
                                }
                                socket = new DatagramSocket();
                                socket.setSoTimeout(100);
                                authenticated = false;
                                lastPacketReceived = System.currentTimeMillis();
                            }
                            catch (Exception e) {
                                VoiceUDPClient.log("Reconnect socket error: " + e.getMessage());
                            }
                        }
                        VoiceUDPClient.sendHello();
                    }
                    catch (Exception exception) {}
                }
            }, "VoiceUDPKeepalive");
            keepalive.setDaemon(true);
            keepalive.start();
            Thread recv = new Thread(() -> {
                byte[] buf = new byte[2048];
                block9: while (running) {
                    try {
                        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                        socket.receive(pkt);
                        byte[] data = new byte[pkt.getLength()];
                        System.arraycopy(buf, 0, data, 0, pkt.getLength());
                        if (data.length < 1) continue;
                        lastPacketReceived = System.currentTimeMillis();
                        switch (data[0]) {
                            case 1: {
                                if (data.length <= 30) break;
                                byte[] payload = new byte[data.length - 1];
                                System.arraycopy(data, 1, payload, 0, payload.length);
                                incomingAudio.offer(payload);
                                break;
                            }
                            case 8: {
                                if (data.length < 2) break;
                                authenticated = data[1] == 0;
                                VoiceUDPClient.log("Auth " + (authenticated ? "SUCCESS" : "REJECTED"));
                                break;
                            }
                            case 10: {
                                boolean speaking;
                                if (data.length < 4) break;
                                boolean bl = speaking = data[1] == 1;
                                int nameLen = (data[2] & 0xFF) << 8 | data[3] & 0xFF;
                                if (data.length < 4 + nameLen) continue block9;
                                String name = new String(data, 4, nameLen, "UTF-8");
                                VoiceAudioEngine.setPlayerSpeaking(name, speaking);
                                break;
                            }
                            case 9: {
                                if (data.length < 5) break;
                                float range = Float.intBitsToFloat(data[1] & 0xFF | (data[2] & 0xFF) << 8 | (data[3] & 0xFF) << 16 | (data[4] & 0xFF) << 24);
                                VoiceAudioEngine.setProxRange(range);
                                VoiceUDPClient.log("Server prox range: " + range + " blocks");
                            }
                        }
                    }
                    catch (SocketTimeoutException pkt) {
                    }
                    catch (Exception e) {
                        if (!running) continue;
                        VoiceUDPClient.log("Receive error: " + e.getMessage());
                    }
                }
            }, "VoiceUDPReceive");
            recv.setDaemon(true);
            recv.start();
            VoiceUDPClient.log("Connected to " + serverIp + ":" + serverPort);
            return true;
        }
        catch (Exception e) {
            VoiceUDPClient.log("Connect error: " + e.getMessage());
            return false;
        }
    }

    private static void sendHello() {
        if (authToken != null && authToken.length == 32) {
            byte[] hello = new byte[33];
            hello[0] = 2;
            System.arraycopy(authToken, 0, hello, 1, 32);
            VoiceUDPClient.send(hello);
        } else if (playerUUID != null) {
            byte[] hello = new byte[17];
            hello[0] = 2;
            ByteBuffer bb = ByteBuffer.wrap(hello, 1, 16);
            bb.putLong(playerUUID.getMostSignificantBits());
            bb.putLong(playerUUID.getLeastSignificantBits());
            VoiceUDPClient.send(hello);
            VoiceUDPClient.log("Sent UUID fallback hello");
        }
    }

    public static void sendAudio(byte[] encodedAudio) {
        if (socket == null || socket.isClosed() || !running) {
            return;
        }
        int seq = ++sequenceNumber;
        byte[] pkt = new byte[encodedAudio.length + 5];
        pkt[0] = 1;
        pkt[1] = (byte)(seq >> 24 & 0xFF);
        pkt[2] = (byte)(seq >> 16 & 0xFF);
        pkt[3] = (byte)(seq >> 8 & 0xFF);
        pkt[4] = (byte)(seq & 0xFF);
        System.arraycopy(encodedAudio, 0, pkt, 5, encodedAudio.length);
        VoiceUDPClient.send(pkt);
    }

    public static byte[] pollAudio() {
        return incomingAudio.poll();
    }

    public static void disconnect() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            try {
                VoiceUDPClient.send(new byte[]{3});
            }
            catch (Exception exception) {
                // empty catch block
            }
            socket.close();
        }
    }

    private static void send(byte[] data) {
        try {
            if (socket == null || socket.isClosed() || serverAddress == null) {
                return;
            }
            socket.send(new DatagramPacket(data, data.length, serverAddress, serverPort));
        }
        catch (Exception e) {
            VoiceUDPClient.log("Send error: " + e.getMessage());
        }
    }

    public static boolean isConnected() {
        return socket != null && !socket.isClosed() && running;
    }

    public static boolean isAuthenticated() {
        return authenticated;
    }

    private static void log(String msg) {
        System.out.println("[VoiceUDP] " + msg);
    }

    static {
        serverPort = 9876;
        running = false;
        authenticated = false;
        authToken = null;
        incomingAudio = new ConcurrentLinkedQueue();
        lastPacketReceived = 0L;
        sequenceNumber = 0;
    }
}

