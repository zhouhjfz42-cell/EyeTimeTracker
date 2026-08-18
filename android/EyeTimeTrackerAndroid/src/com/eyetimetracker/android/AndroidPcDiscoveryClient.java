package com.eyetimetracker.android;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class AndroidPcDiscoveryClient {
    public static final int DEFAULT_DISCOVERY_PORT = 17419;
    private static final int FIRST_TCP_DISCOVERY_PORT = 17420;
    private static final int LAST_TCP_DISCOVERY_PORT = 17429;
    private static final String DEFAULT_BROADCAST_HOST = "255.255.255.255";

    private final int timeoutMillis;

    public AndroidPcDiscoveryClient() {
        this(2500);
    }

    public AndroidPcDiscoveryClient(int timeoutMillis) {
        this.timeoutMillis = Math.max(50, timeoutMillis);
    }

    public DiscoveryResult discover() {
        byte[] requestBytes = buildRequestJson().getBytes(StandardCharsets.UTF_8);
        DiscoveryResult broadcastResult = discoverUdp(LanDiscoveryAddresses.broadcastAddresses(), DEFAULT_DISCOVERY_PORT, requestBytes);
        return broadcastResult.found ? broadcastResult : discoverTcpScan();
    }

    public DiscoveryResult discoverKnownPeer(String preferredHost, int preferredPort) {
        byte[] requestBytes = buildRequestJson().getBytes(StandardCharsets.UTF_8);
        DiscoveryResult broadcastResult = discoverUdp(LanDiscoveryAddresses.broadcastAddresses(), DEFAULT_DISCOVERY_PORT, requestBytes);
        if (broadcastResult.found) {
            return broadcastResult;
        }

        if (preferredHost == null || preferredHost.trim().isEmpty()) {
            return DiscoveryResult.empty();
        }

        String requestJson = buildRequestJson();
        int perConnectTimeout = Math.max(80, Math.min(220, timeoutMillis / 8));
        int perReadTimeout = Math.max(100, Math.min(300, timeoutMillis / 6));
        if (preferredPort > 0 && preferredPort <= 65535) {
            DiscoveryResult result = discoverTcp(preferredHost.trim(), preferredPort, requestJson, perConnectTimeout, perReadTimeout);
            if (result.found) {
                return result;
            }
        }
        for (int port = FIRST_TCP_DISCOVERY_PORT; port <= LAST_TCP_DISCOVERY_PORT; port++) {
            if (port == preferredPort) {
                continue;
            }
            DiscoveryResult result = discoverTcp(preferredHost.trim(), port, requestJson, perConnectTimeout, perReadTimeout);
            if (result.found) {
                return result;
            }
        }
        return discoverTcpScan();
    }

    public DiscoveryResult discover(String host, int port) {
        if (host == null || host.trim().isEmpty() || port <= 0 || port > 65535) {
            return DiscoveryResult.empty();
        }

        return discoverUdp(java.util.Collections.singletonList(hostToAddress(host)), port, buildRequestJson().getBytes(StandardCharsets.UTF_8));
    }

    private DiscoveryResult discoverUdp(java.util.List<InetAddress> addresses, int port, byte[] requestBytes) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(timeoutMillis);

            for (InetAddress address : addresses) {
                if (address == null) {
                    continue;
                }
                DatagramPacket request = new DatagramPacket(requestBytes, requestBytes.length, address, port);
                socket.send(request);
            }

            long deadline = System.currentTimeMillis() + timeoutMillis;
            byte[] responseBytes = new byte[2048];
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket response = new DatagramPacket(responseBytes, responseBytes.length);
                socket.receive(response);
                DiscoveryResult result = parseDiscoveryResponse(
                        new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8),
                        response.getAddress().getHostAddress());
                if (result.found) {
                    return result;
                }
            }
        } catch (Exception ignored) {
        }
        return DiscoveryResult.empty();
    }

    private DiscoveryResult discoverTcpScan() {
        String requestJson = buildRequestJson();
        int perConnectTimeout = Math.max(80, Math.min(220, timeoutMillis / 8));
        int perReadTimeout = Math.max(100, Math.min(300, timeoutMillis / 6));
        java.util.List<InetAddress> candidates = LanDiscoveryAddresses.candidateHosts();
        for (InetAddress address : candidates) {
            String host = address.getHostAddress();
            DiscoveryResult result = discoverTcp(host, FIRST_TCP_DISCOVERY_PORT, requestJson, perConnectTimeout, perReadTimeout);
            if (result.found) {
                return result;
            }
        }
        for (InetAddress address : candidates) {
            String host = address.getHostAddress();
            for (int port = FIRST_TCP_DISCOVERY_PORT + 1; port <= LAST_TCP_DISCOVERY_PORT; port++) {
                DiscoveryResult result = discoverTcp(host, port, requestJson, perConnectTimeout, perReadTimeout);
                if (result.found) {
                    return result;
                }
            }
        }
        return DiscoveryResult.empty();
    }

    private DiscoveryResult discoverTcp(String host, int port, String requestJson, int connectTimeout, int readTimeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeout);
            socket.setSoTimeout(readTimeout);
            try (
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.write(requestJson);
                writer.newLine();
                writer.flush();
                return parseDiscoveryResponse(reader.readLine(), host);
            }
        } catch (Exception ignored) {
            return DiscoveryResult.empty();
        }
    }

    private static DiscoveryResult parseDiscoveryResponse(String json, String host) {
        if (!SyncMessages.DISCOVERY_RESPONSE.equals(readString(json, "Type", "type"))) {
            return DiscoveryResult.empty();
        }

        int syncPort = readInt(json, "Port", "port");
        if (syncPort <= 0 || syncPort > 65535) {
            return DiscoveryResult.empty();
        }

        return new DiscoveryResult(
                true,
                host,
                syncPort,
                readString(json, "DeviceId", "deviceId"),
                readString(json, "Platform", "platform"));
    }

    private static InetAddress hostToAddress(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (Exception ex) {
            return null;
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
