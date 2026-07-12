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

public final class FamilyStatsUploadRequestClient {
    private static final int UDP_TIMEOUT_MS = 300;
    private static final int TCP_CONNECT_TIMEOUT_MS = 120;
    private static final int TCP_READ_TIMEOUT_MS = 220;

    public RequestResult requestUpload(EyeTimeStore store) {
        if (store == null || store.getProductMode() != ProductMode.FAMILY || store.getDeviceRole() != DeviceRole.PARENT_DEVICE) {
            return RequestResult.skipped("Parent phone is not in family mode.");
        }
        String familyId = store.getFamilyId();
        String childDeviceId = store.getFamilyChildDeviceId();
        if (familyId.isEmpty() || childDeviceId.isEmpty()) {
            return RequestResult.skipped("Family child device is not bound.");
        }

        String requestJson = FamilyStatsProtocol.buildUploadNowRequest(familyId, childDeviceId, store.getDeviceId());
        byte[] requestBytes = requestJson.getBytes(StandardCharsets.UTF_8);
        int udpSent = sendUdp(requestBytes);
        int tcpAccepted = requestByTcpScan(requestJson);
        return new RequestResult(udpSent > 0 || tcpAccepted > 0, false, "", udpSent, tcpAccepted);
    }

    private int sendUdp(byte[] requestBytes) {
        int sent = 0;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(UDP_TIMEOUT_MS);
            for (InetAddress address : LanDiscoveryAddresses.broadcastAddresses()) {
                DatagramPacket request = new DatagramPacket(
                        requestBytes,
                        requestBytes.length,
                        address,
                        FamilyStatsProtocol.DEFAULT_DISCOVERY_PORT);
                socket.send(request);
                sent++;
            }
        } catch (Exception ignored) {
        }
        return sent;
    }

    private int requestByTcpScan(String requestJson) {
        int accepted = 0;
        for (InetAddress address : LanDiscoveryAddresses.candidateHosts()) {
            String host = address.getHostAddress();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, FamilyStatsProtocol.DEFAULT_DISCOVERY_PORT), TCP_CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(TCP_READ_TIMEOUT_MS);
                try (
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                    writer.write(requestJson);
                    writer.newLine();
                    writer.flush();
                    FamilyStatsProtocol.UploadNowResponse response = FamilyStatsProtocol.parseUploadNowResponse(reader.readLine());
                    if (response.accepted) {
                        accepted++;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return accepted;
    }

    public static final class RequestResult {
        public final boolean requested;
        public final boolean skipped;
        public final String error;
        public final int udpSent;
        public final int tcpAccepted;

        private RequestResult(boolean requested, boolean skipped, String error, int udpSent, int tcpAccepted) {
            this.requested = requested;
            this.skipped = skipped;
            this.error = error == null ? "" : error;
            this.udpSent = Math.max(0, udpSent);
            this.tcpAccepted = Math.max(0, tcpAccepted);
        }

        public static RequestResult skipped(String message) {
            return new RequestResult(false, true, message, 0, 0);
        }
    }
}
