package com.cosmic.launcher.agent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 100% Off-Cloud Auth & CDN Interceptor.
 * Serves authentication.php, item skins, cosmetics, and handshake verification
 * with zero reliance on remote servers.
 */
public class AuthHelper {
    private static volatile boolean loggedAuth = false;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DEFAULT_HMAC = "a1b2c3d4e5f60718293a4b5c6d7e8f901a2b3c4d5e6f708192a3b4c5d6e7f809";
    private static final String DEFAULT_VERSION = "2.7.0.b84ff";

    public static URLConnection openConnection(URL url) throws IOException {
        if (url == null) return null;
        if (shouldMock(url)) {
            if (!loggedAuth) {
                loggedAuth = true;
                System.out.println("[CosmicAgent] Off-cloud interceptor active for auth/CDN (" + url + ")");
            }
            return new MockAuthConnection(url);
        }
        return url.openConnection();
    }

    public static URLConnection openConnectionProxy(URL url, Proxy proxy) throws IOException {
        if (url == null) return null;
        if (shouldMock(url)) {
            if (!loggedAuth) {
                loggedAuth = true;
                System.out.println("[CosmicAgent] Off-cloud interceptor active for auth/CDN (" + url + ")");
            }
            return new MockAuthConnection(url);
        }
        return proxy != null ? url.openConnection(proxy) : url.openConnection();
    }

    public static boolean shouldMock(URL url) {
        if (url == null) return false;
        String host = url.getHost();
        String path = url.getPath();
        if (host != null) {
            String lower = host.toLowerCase();
            if (lower.contains("cosmicclient.com") || 
                lower.contains("cdn.direct") || 
                lower.contains("buycraft") || 
                lower.contains("tebex")) {
                return true;
            }
        }
        if (path != null) {
            String pLower = path.toLowerCase();
            if (pLower.contains("authentication.php") || pLower.contains("auth.php") || pLower.contains("metrics.php")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Safely retrieves client version string from iz singleton.
     */
    public static String getClientVersion(Object izInstance) {
        if (izInstance != null) {
            try {
                Method m = izInstance.getClass().getMethod("n");
                Object res = m.invoke(izInstance);
                if (res != null && !res.toString().isEmpty()) {
                    return res.toString();
                }
            } catch (Throwable ignored) {
            }
        }
        return DEFAULT_VERSION;
    }

    /**
     * Pure offline authentication handler.
     * Takes serialized 'ot' JSON from the client and generates the echoed, signed
     * offline response matching ot.class validation rules.
     */
    public static String mockAuthRequest(String requestBody) {
        String name = extractJsonString(requestBody, "name", "CosmicPlayer");
        String rawUuid = extractJsonString(requestBody, "uuid", "00000000000000000000000000000000");
        String uuid = rawUuid.replace("-", "");
        if (uuid.length() != 32) {
            uuid = "00000000000000000000000000000000";
        }
        String tokenHash = extractJsonString(requestBody, "mojang_token_hash", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        String version = extractJsonString(requestBody, "version", DEFAULT_VERSION);
        long clientTime = extractJsonLong(requestBody, "client_time", System.currentTimeMillis());
        long clientRandom = extractJsonLong(requestBody, "client_random", RANDOM.nextLong());

        long serverTime = System.currentTimeMillis();
        long serverRandom = RANDOM.nextLong();
        long lifespan = 86400000000L; // 1000 days lifespan

        return "{"
            + "\"name\":\"" + escapeJson(name) + "\","
            + "\"uuid\":\"" + escapeJson(uuid) + "\","
            + "\"mojang_token_hash\":\"" + escapeJson(tokenHash) + "\","
            + "\"version\":\"" + escapeJson(version) + "\","
            + "\"client_time\":" + clientTime + ","
            + "\"client_random\":" + clientRandom + ","
            + "\"server_time\":" + serverTime + ","
            + "\"server_random\":" + serverRandom + ","
            + "\"lifespan\":" + lifespan + ","
            + "\"hmac\":\"" + DEFAULT_HMAC + "\","
            + "\"status\":\"ok\","
            + "\"authenticated\":true,"
            + "\"valid\":true,"
            + "\"success\":true,"
            + "\"token\":\"cosmic_offline_token\""
            + "}";
    }

    private static String extractJsonString(String json, String key, String defaultVal) {
        if (json == null) return defaultVal;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return defaultVal;
    }

    private static long extractJsonLong(String json, String key, long defaultVal) {
        if (json == null) return defaultVal;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultVal;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static class MockAuthConnection extends HttpURLConnection {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private byte[] responseBytes;
        private String contentType = "application/json";
        private final Map<String, List<String>> headerFields = new HashMap<>();
        private final String path;

        public MockAuthConnection(URL u) {
            super(u);
            this.path = u.getPath() != null ? u.getPath().toLowerCase() : "";
            this.contentType = "application/json";
            this.responseCode = 200;
        }

        private byte[] generateResponseData() {
            if (path.contains("1.8.json")) {
                byte[] diskData = readOfflineAssetIndex();
                if (diskData != null && diskData.length > 0) {
                    return diskData;
                }
                return "{\"objects\":{}}".getBytes(StandardCharsets.UTF_8);
            }

            if (path.contains("authentication.php") || path.contains("auth.php") || path.contains("auth")) {
                String req = outputStream.size() > 0 ? new String(outputStream.toByteArray(), StandardCharsets.UTF_8) : "";
                return mockAuthRequest(req).getBytes(StandardCharsets.UTF_8);
            }

            if (path.contains("metrics.php") || path.contains("query")) {
                return "{\"status\":\"ok\",\"authenticated\":true,\"valid\":true,\"success\":true,\"token\":\"offline_mock_token\"}".getBytes(StandardCharsets.UTF_8);
            }

            return "{\"status\":\"ok\",\"success\":true,\"authenticated\":true}".getBytes(StandardCharsets.UTF_8);
        }

        private static byte[] readOfflineAssetIndex() {
            try {
                String installDir = System.getProperty("cosmic.installdir", ".");
                File f = new File(installDir, "assets_18/indexes/1.8.json");
                if (f.exists() && f.length() > 0) {
                    return java.nio.file.Files.readAllBytes(f.toPath());
                }
                File f2 = new File("offline-archive/1.8.json");
                if (f2.exists() && f2.length() > 0) {
                    return java.nio.file.Files.readAllBytes(f2.toPath());
                }
            } catch (Exception ignored) {
            }
            return null;
        }

        @Override
        public void connect() throws IOException {
            this.connected = true;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return this.outputStream;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (responseBytes == null) {
                responseBytes = generateResponseData();
            }
            return new ByteArrayInputStream(responseBytes);
        }

        @Override
        public int getResponseCode() throws IOException {
            return 200;
        }

        @Override
        public String getResponseMessage() throws IOException {
            return "OK";
        }

        @Override
        public String getContentType() {
            return this.contentType;
        }

        @Override
        public String getHeaderField(String name) {
            if ("Content-Type".equalsIgnoreCase(name)) return this.contentType;
            if ("Content-Length".equalsIgnoreCase(name)) {
                if (this.responseBytes == null) {
                    this.responseBytes = generateResponseData();
                }
                return String.valueOf(this.responseBytes.length);
            }
            return null;
        }

        @Override
        public Map<String, List<String>> getHeaderFields() {
            return this.headerFields;
        }

        @Override
        public void disconnect() {}

        @Override
        public boolean usingProxy() {
            return false;
        }
    }
}
