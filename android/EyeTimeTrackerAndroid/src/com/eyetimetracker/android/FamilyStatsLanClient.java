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
import java.time.LocalDate;
import java.util.List;

public final class FamilyStatsLanClient {
    private static final String BROADCAST_HOST = "255.255.255.255";
    private final int discoveryTimeoutMillis;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public FamilyStatsLanClient() {
        this(2500, 2500, 5000);
    }

    public FamilyStatsLanClient(int discoveryTimeoutMillis, int connectTimeoutMillis, int readTimeoutMillis) {
        this.discoveryTimeoutMillis = Math.max(50, discoveryTimeoutMillis);
        this.connectTimeoutMillis = Math.max(50, connectTimeoutMillis);
        this.readTimeoutMillis = Math.max(50, readTimeoutMillis);
    }

    public UploadResult upload(EyeTimeStore store) {
        if (store == null || store.getProductMode() != ProductMode.FAMILY || store.getDeviceRole() != DeviceRole.CHILD_DEVICE) {
            return UploadResult.skipped("Child device is not in family mode.");
        }
        String familyId = store.getFamilyId();
        String childDeviceId = store.getDeviceId();
        ChildProfile childProfile = store.getActiveChildProfile();
        if (familyId.isEmpty() || childProfile == null || !childProfile.isValid()) {
            return UploadResult.skipped("Family binding is incomplete.");
        }

        DiscoveryTarget target = discover(familyId, childDeviceId);
        if (!target.found) {
            return UploadResult.skipped("Parent phone not found.");
        }

        LocalDate today = LocalDate.now();
        List<UsageSegment> segments = store.getSegments(today.minusDays(30), today);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target.host, target.port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            try (
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.write(FamilyStatsProtocol.buildUploadRequest(familyId, childProfile.childId, childDeviceId, segments));
                writer.newLine();
                writer.flush();

                FamilyStatsProtocol.UploadResponse response = FamilyStatsProtocol.parseUploadResponse(reader.readLine());
                if (!response.accepted) {
                    return UploadResult.failed(response.error.isEmpty() ? "Family stats upload was rejected." : response.error);
                }
                if (response.hasReminderSettings) {
                    store.saveReminderSettings(response.reminderMinutes, response.repeatReminder);
                }
                if (response.parentPasscode != null && response.parentPasscode.isConfigured()) {
                    store.saveParentPasscode(response.parentPasscode);
                }
                return UploadResult.success(response.changedSegments);
            }
        } catch (Exception ex) {
            return UploadResult.failed(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private DiscoveryTarget discover(String familyId, String childDeviceId) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(discoveryTimeoutMillis);
            byte[] requestBytes = FamilyStatsProtocol.buildDiscoveryRequest(familyId, childDeviceId).getBytes(StandardCharsets.UTF_8);
            DatagramPacket request = new DatagramPacket(
                    requestBytes,
                    requestBytes.length,
                    InetAddress.getByName(BROADCAST_HOST),
                    FamilyStatsProtocol.DEFAULT_DISCOVERY_PORT);
            socket.send(request);

            long deadline = System.currentTimeMillis() + discoveryTimeoutMillis;
            byte[] responseBytes = new byte[2048];
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket response = new DatagramPacket(responseBytes, responseBytes.length);
                socket.receive(response);
                String responseJson = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
                FamilyStatsProtocol.DiscoveryResponse parsed = FamilyStatsProtocol.parseDiscoveryResponse(responseJson);
                if (parsed.found && parsed.port > 0 && familyId.equals(parsed.familyId)) {
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

    public static final class UploadResult {
        public final boolean success;
        public final boolean skipped;
        public final String error;
        public final int changedSegments;

        private UploadResult(boolean success, boolean skipped, String error, int changedSegments) {
            this.success = success;
            this.skipped = skipped;
            this.error = error == null ? "" : error;
            this.changedSegments = Math.max(0, changedSegments);
        }

        public static UploadResult success(int changedSegments) {
            return new UploadResult(true, false, "", changedSegments);
        }

        public static UploadResult skipped(String message) {
            return new UploadResult(false, true, message, 0);
        }

        public static UploadResult failed(String error) {
            return new UploadResult(false, false, error, 0);
        }
    }
}
