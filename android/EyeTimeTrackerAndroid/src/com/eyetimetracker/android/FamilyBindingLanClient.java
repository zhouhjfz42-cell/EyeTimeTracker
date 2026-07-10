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

public final class FamilyBindingLanClient {
    private static final String BROADCAST_HOST = "255.255.255.255";
    private final int discoveryTimeoutMillis;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public FamilyBindingLanClient() {
        this(3000, 3000, 5000);
    }

    public FamilyBindingLanClient(int discoveryTimeoutMillis, int connectTimeoutMillis, int readTimeoutMillis) {
        this.discoveryTimeoutMillis = Math.max(50, discoveryTimeoutMillis);
        this.connectTimeoutMillis = Math.max(50, connectTimeoutMillis);
        this.readTimeoutMillis = Math.max(50, readTimeoutMillis);
    }

    public JoinResult join(String deviceId, String bindingCode) {
        String normalizedCode = FamilyBindingInvite.normalizeBindingCode(bindingCode);
        if (!normalizedCode.matches("\\d{6}")) {
            return JoinResult.failed("Binding code is incorrect.");
        }
        DiscoveryTarget target = discover(deviceId);
        if (!target.found) {
            return JoinResult.failed("Parent phone not found.");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target.host, target.port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            try (
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.write(FamilyBindingProtocol.buildJoinRequest(deviceId, normalizedCode));
                writer.newLine();
                writer.flush();

                FamilyBindingProtocol.JoinResponse response = FamilyBindingProtocol.parseJoinResponse(reader.readLine());
                if (!response.accepted || response.childProfile == null || !response.childProfile.isValid()) {
                    return JoinResult.failed(response.error.isEmpty() ? "Binding code is incorrect." : response.error);
                }
                return JoinResult.success(response.familyId, response.childProfile, response.parentPasscode);
            }
        } catch (Exception ex) {
            return JoinResult.failed(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private DiscoveryTarget discover(String deviceId) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(discoveryTimeoutMillis);
            byte[] requestBytes = FamilyBindingProtocol.buildDiscoveryRequest(deviceId).getBytes(StandardCharsets.UTF_8);
            DatagramPacket request = new DatagramPacket(
                    requestBytes,
                    requestBytes.length,
                    InetAddress.getByName(BROADCAST_HOST),
                    FamilyBindingProtocol.DEFAULT_DISCOVERY_PORT);
            socket.send(request);

            long deadline = System.currentTimeMillis() + discoveryTimeoutMillis;
            byte[] responseBytes = new byte[2048];
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket response = new DatagramPacket(responseBytes, responseBytes.length);
                socket.receive(response);
                String responseJson = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
                FamilyBindingProtocol.DiscoveryResponse parsed = FamilyBindingProtocol.parseDiscoveryResponse(responseJson);
                if (parsed.found && parsed.port > 0) {
                    return new DiscoveryTarget(true, response.getAddress().getHostAddress(), parsed.port);
                }
            }
        } catch (Exception ignored) {
        }
        return DiscoveryTarget.empty();
    }

    private static final class DiscoveryTarget {
        final boolean found;
        final String host;
        final int port;

        DiscoveryTarget(boolean found, String host, int port) {
            this.found = found;
            this.host = host == null ? "" : host;
            this.port = port;
        }

        static DiscoveryTarget empty() {
            return new DiscoveryTarget(false, "", 0);
        }
    }

    public static final class JoinResult {
        public final boolean success;
        public final String error;
        public final String familyId;
        public final ChildProfile childProfile;
        public final ParentPasscode parentPasscode;

        private JoinResult(boolean success, String error, String familyId, ChildProfile childProfile, ParentPasscode parentPasscode) {
            this.success = success;
            this.error = error == null ? "" : error;
            this.familyId = familyId == null ? "" : familyId;
            this.childProfile = childProfile;
            this.parentPasscode = parentPasscode;
        }

        public static JoinResult success(String familyId, ChildProfile childProfile) {
            return success(familyId, childProfile, null);
        }

        public static JoinResult success(String familyId, ChildProfile childProfile, ParentPasscode parentPasscode) {
            return new JoinResult(true, "", familyId, childProfile, parentPasscode);
        }

        public static JoinResult failed(String error) {
            return new JoinResult(false, error, "", null, null);
        }
    }
}
