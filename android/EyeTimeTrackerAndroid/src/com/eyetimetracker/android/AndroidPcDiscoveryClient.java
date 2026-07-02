package com.eyetimetracker.android;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public final class AndroidPcDiscoveryClient {
    public static final int DEFAULT_DISCOVERY_PORT = 17419;
    private static final String DEFAULT_BROADCAST_HOST = "255.255.255.255";

    private final int timeoutMillis;

    public AndroidPcDiscoveryClient() {
        this(2500);
    }

    public AndroidPcDiscoveryClient(int timeoutMillis) {
        this.timeoutMillis = Math.max(50, timeoutMillis);
    }

    public DiscoveryResult discover() {
        return discover(DEFAULT_BROADCAST_HOST, DEFAULT_DISCOVERY_PORT);
    }

    public DiscoveryResult discover(String host, int port) {
        if (host == null || host.trim().isEmpty() || port <= 0 || port > 65535) {
            return DiscoveryResult.empty();
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(timeoutMillis);

            byte[] requestBytes = buildRequestJson().getBytes(StandardCharsets.UTF_8);
            DatagramPacket request = new DatagramPacket(
                    requestBytes,
                    requestBytes.length,
                    InetAddress.getByName(host),
                    port);
            socket.send(request);

            byte[] responseBytes = new byte[2048];
            DatagramPacket response = new DatagramPacket(responseBytes, responseBytes.length);
            socket.receive(response);

            String json = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
            if (!SyncMessages.DISCOVERY_RESPONSE.equals(readString(json, "Type", "type"))) {
                return DiscoveryResult.empty();
            }

            int syncPort = readInt(json, "Port", "port");
            if (syncPort <= 0 || syncPort > 65535) {
                return DiscoveryResult.empty();
            }

            return new DiscoveryResult(
                    true,
                    response.getAddress().getHostAddress(),
                    syncPort,
                    readString(json, "DeviceId", "deviceId"),
                    readString(json, "Platform", "platform"));
        } catch (Exception ignored) {
            return DiscoveryResult.empty();
        }
    }

    private static String buildRequestJson() {
        return "{\"Type\":\"" + SyncMessages.DISCOVERY_REQUEST + "\",\"Platform\":\"android\"}";
    }

    private static int readInt(String json, String pascalName, String camelName) {
        try {
            return Integer.parseInt(readRawValue(json, pascalName, camelName));
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String readString(String json, String pascalName, String camelName) {
        String raw = readRawValue(json, pascalName, camelName);
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            return raw.substring(1, raw.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return raw;
    }

    private static String readRawValue(String json, String pascalName, String camelName) {
        String value = readRawValue(json, pascalName);
        return value.isEmpty() ? readRawValue(json, camelName) : value;
    }

    private static String readRawValue(String json, String name) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(name) + "\"\\s*:\\s*(\"(?:\\\\.|[^\"])*\"|-?\\d+|true|false|null)",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return "";
        }
        String value = matcher.group(1);
        return "null".equals(value) ? "" : value;
    }

    public static final class DiscoveryResult {
        public final boolean found;
        public final String host;
        public final int port;
        public final String deviceId;
        public final String platform;

        public DiscoveryResult(boolean found, String host, int port, String deviceId, String platform) {
            this.found = found;
            this.host = host == null ? "" : host;
            this.port = port;
            this.deviceId = deviceId == null ? "" : deviceId;
            this.platform = platform == null ? "" : platform;
        }

        public static DiscoveryResult empty() {
            return new DiscoveryResult(false, "", 0, "", "");
        }
    }
}
