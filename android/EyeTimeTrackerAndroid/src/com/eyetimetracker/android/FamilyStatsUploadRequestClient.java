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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class FamilyStatsUploadRequestClient {
    private static final int UDP_TIMEOUT_MS = 300;
    private static final int TCP_CONNECT_TIMEOUT_MS = 120;
    private static final int TCP_READ_TIMEOUT_MS = 220;
    private static final int TCP_SCAN_DEADLINE_MS = 2500;
    private static final int TCP_SCAN_THREADS = 24;
    private final int parentStatsPort;
    private final String preferredChildHost;
    private final boolean allowTcpScanFallback;

    public FamilyStatsUploadRequestClient() {
        this(0);
    }

    public FamilyStatsUploadRequestClient(int parentStatsPort) {
        this(parentStatsPort, "", true);
    }

    public FamilyStatsUploadRequestClient(int parentStatsPort, String preferredChildHost, boolean allowTcpScanFallback) {
        this.parentStatsPort = Math.max(0, parentStatsPort);
        this.preferredChildHost = preferredChildHost == null ? "" : preferredChildHost.trim();
        this.allowTcpScanFallback = allowTcpScanFallback;
    }

    public RequestResult requestUpload(EyeTimeStore store) {
        if (store == null || store.getProductMode() != ProductMode.FAMILY || store.getDeviceRole() != DeviceRole.PARENT_DEVICE) {
            return RequestResult.skipped("Parent phone is not in family mode.");
        }
        String familyId = store.getFamilyId();
        String childDeviceId = store.getFamilyChildDeviceId();
        if (familyId.isEmpty() || childDeviceId.isEmpty()) {
            return RequestResult.skipped("Family child device is not bound.");
        }

        String requestJson = FamilyStatsProtocol.buildUploadNowRequest(
                familyId,
                childDeviceId,
                store.getDeviceId(),
                parentStatsPort);
        byte[] requestBytes = requestJson.getBytes(StandardCharsets.UTF_8);
        int directAccepted = requestKnownChildByTcp(requestJson);
        int udpSent = sendUdp(requestBytes);
        int tcpAccepted = directAccepted;
        if (tcpAccepted <= 0 && allowTcpScanFallback) {
            tcpAccepted += requestByTcpScan(requestJson);
        }
        return new RequestResult(udpSent > 0 || tcpAccepted > 0, false, "", udpSent, tcpAccepted);
    }

    private int requestKnownChildByTcp(String requestJson) {
        if (preferredChildHost.isEmpty()) {
            return 0;
        }
        return requestByTcp(preferredChildHost, requestJson) ? 1 : 0;
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
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (InetAddress address : LanDiscoveryAddresses.candidateHosts()) {
            String host = address.getHostAddress();
            if (host.equals(preferredChildHost)) {
                continue;
            }
            tasks.add(() -> requestByTcp(host, requestJson));
        }
        if (tasks.isEmpty()) {
            return 0;
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(TCP_SCAN_THREADS, tasks.size()));
        try {
            CompletionService<Boolean> completion = new ExecutorCompletionService<>(executor);
            for (Callable<Boolean> task : tasks) {
                completion.submit(task);
            }

            long deadline = System.currentTimeMillis() + TCP_SCAN_DEADLINE_MS;
            int accepted = 0;
            for (int i = 0; i < tasks.size(); i++) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    break;
                }
                Future<Boolean> result = completion.poll(remaining, TimeUnit.MILLISECONDS);
                if (result == null) {
                    break;
                }
                try {
                    if (Boolean.TRUE.equals(result.get())) {
                        accepted++;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
            return accepted;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return 0;
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean requestByTcp(String host, String requestJson) {
        if (host == null || host.trim().isEmpty()) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host.trim(), FamilyStatsProtocol.DEFAULT_DISCOVERY_PORT), TCP_CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(TCP_READ_TIMEOUT_MS);
            try (
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.write(requestJson);
                writer.newLine();
                writer.flush();
                FamilyStatsProtocol.UploadNowResponse response = FamilyStatsProtocol.parseUploadNowResponse(reader.readLine());
                return response.accepted;
            }
        } catch (Exception ignored) {
            return false;
        }
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
