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

public final class FamilyStatsUploadRequestServer {
    public interface Listener {
        void onStarted();
        void onUploadRequested(String parentHost, int parentStatsPort);
        void onError(String message);
    }

    private final EyeTimeStore store;
    private volatile boolean running;
    private ServerSocket tcpServer;
    private DatagramSocket udpServer;
    private Thread tcpThread;
    private Thread udpThread;

    public FamilyStatsUploadRequestServer(EyeTimeStore store) {
        this.store = store;
    }

    public synchronized void start(Listener listener) {
        if (!isChildFamilyMode()) {
            return;
        }
        stop();
        try {
            tcpServer = new ServerSocket();
            tcpServer.setReuseAddress(true);
            tcpServer.bind(new InetSocketAddress(FamilyStatsProtocol.DEFAULT_DISCOVERY_PORT));
            tcpServer.setSoTimeout(1000);

            udpServer = new DatagramSocket(null);
            udpServer.setReuseAddress(true);
            udpServer.setBroadcast(true);
            udpServer.bind(new InetSocketAddress(FamilyStatsProtocol.DEFAULT_DISCOVERY_PORT));
            udpServer.setSoTimeout(1000);

            running = true;
            tcpThread = new Thread(() -> runTcpLoop(listener), "FamilyStatsUploadRequestTcp");
            udpThread = new Thread(() -> runUdpLoop(listener), "FamilyStatsUploadRequestUdp");
            tcpThread.start();
            udpThread.start();
            if (listener != null) {
                listener.onStarted();
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
                socket.setSoTimeout(2000);
                handleRequest(socket, listener);
            } catch (java.net.SocketTimeoutException ignored) {
            } catch (Exception ex) {
                if (running) {
                    notifyError(listener, messageOf(ex));
                }
            }
        }
    }

    private void handleRequest(Socket socket, Listener listener) throws Exception {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String requestJson = reader.readLine();
            FamilyStatsProtocol.UploadNowRequest request = FamilyStatsProtocol.parseUploadNowRequest(requestJson);
            String error = validate(request);
            writer.write(FamilyStatsProtocol.buildUploadNowResponse(error.isEmpty(), error));
            writer.newLine();
            writer.flush();
            if (error.isEmpty() && listener != null) {
                listener.onUploadRequested(socket.getInetAddress().getHostAddress(), request.parentStatsPort);
            }
        }
    }

    private void runUdpLoop(Listener listener) {
        byte[] buffer = new byte[2048];
        while (running) {
            try {
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                udpServer.receive(request);
                String requestJson = new String(request.getData(), request.getOffset(), request.getLength(), StandardCharsets.UTF_8);
                String error = validate(FamilyStatsProtocol.parseUploadNowRequest(requestJson));
                if (!error.isEmpty()) {
                    continue;
                }
                byte[] responseBytes = FamilyStatsProtocol.buildUploadNowResponse(true, "").getBytes(StandardCharsets.UTF_8);
                DatagramPacket response = new DatagramPacket(
                        responseBytes,
                        responseBytes.length,
                        request.getAddress(),
                        request.getPort());
                udpServer.send(response);
                if (listener != null) {
                    listener.onUploadRequested(request.getAddress().getHostAddress(), FamilyStatsProtocol.parseUploadNowRequest(requestJson).parentStatsPort);
                }
            } catch (java.net.SocketTimeoutException ignored) {
            } catch (Exception ex) {
                if (running) {
                    notifyError(listener, messageOf(ex));
                }
            }
        }
    }

    private String validate(FamilyStatsProtocol.UploadNowRequest request) {
        if (request == null || !request.valid) {
            return "Invalid upload request.";
        }
        if (!isChildFamilyMode()) {
            return "Child device is not in family mode.";
        }
        String familyId = store.getFamilyId();
        if (!familyId.isEmpty() && !familyId.equals(request.familyId)) {
            return "Family does not match.";
        }
        String deviceId = store.getDeviceId();
        if (!deviceId.isEmpty() && !deviceId.equals(request.childDeviceId)) {
            return "Child device does not match.";
        }
        return "";
    }

    private boolean isChildFamilyMode() {
        return store != null
                && store.getProductMode() == ProductMode.FAMILY
                && store.getDeviceRole() == DeviceRole.CHILD_DEVICE;
    }

    private static void notifyError(Listener listener, String message) {
        if (listener != null) {
            listener.onError(message == null || message.isEmpty() ? "Family upload request service failed." : message);
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
