package com.eyetimetracker.android;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class FamilyStatsLanServer {
    public interface Listener {
        void onStarted(int port);
        void onUploaded(String childDeviceId, int changedSegments);
        void onError(String message);
    }

    private final EyeTimeStore store;
    private volatile boolean running;
    private ServerSocket tcpServer;
    private DatagramSocket udpServer;
    private Thread tcpThread;
    private Thread udpThread;

    public FamilyStatsLanServer(EyeTimeStore store) {
        this.store = store;
    }

    public synchronized void start(Listener listener) {
        if (store == null || store.getProductMode() != ProductMode.FAMILY || store.getDeviceRole() != DeviceRole.PARENT_DEVICE) {
            return;
        }
        stop();
        try {
            tcpServer = bindTcpServer();
            tcpServer.setSoTimeout(1000);

            udpServer = new DatagramSocket(null);
            udpServer.setReuseAddress(true);
            udpServer.setBroadcast(true);
            udpServer.bind(new InetSocketAddress(FamilyStatsProtocol.DEFAULT_DISCOVERY_PORT));
            udpServer.setSoTimeout(1000);

            running = true;
            int tcpPort = tcpServer.getLocalPort();
            tcpThread = new Thread(() -> runTcpLoop(listener), "FamilyStatsLanTcp");
            udpThread = new Thread(() -> runUdpLoop(tcpPort, listener), "FamilyStatsLanUdp");
            tcpThread.start();
            udpThread.start();
            if (listener != null) {
                listener.onStarted(tcpPort);
            }
        } catch (Exception ex) {
            stop();
            notifyError(listener, messageOf(ex));
        }
    }

    public synchronized void stop() {
        running = false;
        closeQuietly(udpServer);
        closeQuietly(tcpServer);
        udpServer = null;
        tcpServer = null;
        interruptQuietly(udpThread);
        interruptQuietly(tcpThread);
        udpThread = null;
        tcpThread = null;
    }

    private void runTcpLoop(Listener listener) {
        while (running) {
            try (Socket socket = tcpServer.accept()) {
                socket.setSoTimeout(5000);
                handleUpload(socket, listener);
            } catch (java.net.SocketTimeoutException ignored) {
            } catch (Exception ex) {
                if (running) {
                    notifyError(listener, messageOf(ex));
                }
            }
        }
    }

    private void handleUpload(Socket socket, Listener listener) throws Exception {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String requestJson = reader.readLine();
            FamilyStatsProtocol.DiscoveryRequest discoveryRequest = FamilyStatsProtocol.parseDiscoveryRequest(requestJson);
            if (discoveryRequest.valid && shouldRespondToDiscovery(discoveryRequest)) {
                writer.write(FamilyStatsProtocol.buildDiscoveryResponse(store.getFamilyId(), store.getDeviceId(), tcpServer.getLocalPort()));
                writer.newLine();
                writer.flush();
                return;
            }
            FamilyStatsProtocol.UploadRequest request = FamilyStatsProtocol.parseUploadRequest(requestJson);
            String error = validateUpload(request);
            String responseJson;
            if (!error.isEmpty()) {
                responseJson = FamilyStatsProtocol.buildUploadResponse(false, error, 0);
            } else {
                int changed = store.addFamilyChildSegments(request.segments);
                responseJson = FamilyStatsProtocol.buildUploadResponse(
                        true,
                        "",
                        changed,
                        store.getReminderMinutes(),
                        store.isRepeatReminderEnabled(),
                        store.getParentPasscode(),
                        store.getFamilyEyeRules());
                if (listener != null) {
                    listener.onUploaded(request.childDeviceId, changed);
                }
            }
            writer.write(responseJson);
            writer.newLine();
            writer.flush();
        }
    }

    private static ServerSocket bindTcpServer() throws Exception {
        try {
            ServerSocket server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(FamilyStatsProtocol.DEFAULT_DISCOVERY_PORT));
            return server;
        } catch (Exception fixedPortFailed) {
            ServerSocket server = new ServerSocket(0);
            server.setReuseAddress(true);
            return server;
        }
    }

    private String validateUpload(FamilyStatsProtocol.UploadRequest request) {
        if (request == null || !request.valid) {
            return "Invalid family stats upload.";
        }
        if (store.getProductMode() != ProductMode.FAMILY || store.getDeviceRole() != DeviceRole.PARENT_DEVICE) {
            return "Parent phone is not in family mode.";
        }
        String familyId = store.getFamilyId();
        if (!familyId.isEmpty() && !familyId.equals(request.familyId)) {
            return "Family does not match.";
        }
        String expectedChildDeviceId = store.getFamilyChildDeviceId();
        if (!expectedChildDeviceId.isEmpty() && !expectedChildDeviceId.equals(request.childDeviceId)) {
            return "Child device does not match.";
        }
        return "";
    }

    private void runUdpLoop(int tcpPort, Listener listener) {
        byte[] buffer = new byte[2048];
        while (running) {
            try {
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                udpServer.receive(request);
                String requestJson = new String(request.getData(), request.getOffset(), request.getLength(), StandardCharsets.UTF_8);
                FamilyStatsProtocol.DiscoveryRequest parsed = FamilyStatsProtocol.parseDiscoveryRequest(requestJson);
                if (!parsed.valid || !shouldRespondToDiscovery(parsed)) {
                    continue;
                }
                byte[] responseBytes = FamilyStatsProtocol.buildDiscoveryResponse(store.getFamilyId(), store.getDeviceId(), tcpPort)
                        .getBytes(StandardCharsets.UTF_8);
                DatagramPacket response = new DatagramPacket(
                        responseBytes,
                        responseBytes.length,
                        request.getAddress(),
                        request.getPort());
                udpServer.send(response);
            } catch (java.net.SocketTimeoutException ignored) {
            } catch (Exception ex) {
                if (running) {
                    notifyError(listener, messageOf(ex));
                }
            }
        }
    }

    private boolean shouldRespondToDiscovery(FamilyStatsProtocol.DiscoveryRequest request) {
        if (store.getProductMode() != ProductMode.FAMILY || store.getDeviceRole() != DeviceRole.PARENT_DEVICE) {
            return false;
        }
        String familyId = store.getFamilyId();
        if (!familyId.isEmpty() && !familyId.equals(request.familyId)) {
            return false;
        }
        String childDeviceId = store.getFamilyChildDeviceId();
        return childDeviceId.isEmpty() || childDeviceId.equals(request.childDeviceId);
    }

    private static void notifyError(Listener listener, String message) {
        if (listener != null) {
            listener.onError(message == null || message.isEmpty() ? "Family stats service failed." : message);
        }
    }

    private static String messageOf(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static void closeQuietly(DatagramSocket socket) {
        if (socket != null) {
            socket.close();
        }
    }

    private static void closeQuietly(ServerSocket socket) {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static void interruptQuietly(Thread thread) {
        if (thread != null) {
            thread.interrupt();
        }
    }
}
