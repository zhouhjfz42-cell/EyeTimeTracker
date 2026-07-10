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

public final class FamilyBindingLanServer {
    public interface Listener {
        void onStarted(int port);
        void onJoined(String childDeviceId);
        void onError(String message);
    }

    private volatile boolean running;
    private ServerSocket tcpServer;
    private DatagramSocket udpServer;
    private Thread tcpThread;
    private Thread udpThread;

    public synchronized void start(FamilyBindingInvite invite, String deviceId, Listener listener) {
        start(invite, deviceId, null, listener);
    }

    public synchronized void start(FamilyBindingInvite invite, String deviceId, ParentPasscode parentPasscode, Listener listener) {
        if (invite == null || !invite.isValid()) {
            notifyError(listener, "Binding invite is missing.");
            return;
        }
        stop();
        try {
            tcpServer = new ServerSocket(0);
            tcpServer.setSoTimeout(1000);

            udpServer = new DatagramSocket(null);
            udpServer.setReuseAddress(true);
            udpServer.setBroadcast(true);
            udpServer.bind(new InetSocketAddress(FamilyBindingProtocol.DEFAULT_DISCOVERY_PORT));
            udpServer.setSoTimeout(1000);

            running = true;
            int tcpPort = tcpServer.getLocalPort();
            tcpThread = new Thread(() -> runTcpLoop(invite, parentPasscode, listener), "FamilyBindingLanTcp");
            udpThread = new Thread(() -> runUdpLoop(deviceId, tcpPort, listener), "FamilyBindingLanUdp");
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

    private void runTcpLoop(FamilyBindingInvite invite, ParentPasscode parentPasscode, Listener listener) {
        while (running) {
            try (Socket socket = tcpServer.accept()) {
                socket.setSoTimeout(5000);
                handleTcpJoin(socket, invite, parentPasscode, listener);
            } catch (java.net.SocketTimeoutException ignored) {
            } catch (Exception ex) {
                if (running) {
                    notifyError(listener, messageOf(ex));
                }
            }
        }
    }

    private void handleTcpJoin(Socket socket, FamilyBindingInvite invite, ParentPasscode parentPasscode, Listener listener) throws Exception {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String requestJson = reader.readLine();
            FamilyBindingProtocol.JoinRequest request = FamilyBindingProtocol.parseJoinRequest(requestJson);
            String responseJson;
            if (!invite.bindingCode.equals(request.bindingCode)) {
                responseJson = FamilyBindingProtocol.buildJoinResponse(false, "Binding code is incorrect.", null);
            } else {
                responseJson = FamilyBindingProtocol.buildJoinResponse(true, "", invite, parentPasscode);
                if (listener != null) {
                    listener.onJoined(request.deviceId);
                }
            }
            writer.write(responseJson);
            writer.newLine();
            writer.flush();
        }
    }

    private void runUdpLoop(String deviceId, int tcpPort, Listener listener) {
        byte[] buffer = new byte[2048];
        while (running) {
            try {
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                udpServer.receive(request);
                String requestJson = new String(request.getData(), request.getOffset(), request.getLength(), StandardCharsets.UTF_8);
                if (!requestJson.contains("\"" + SyncMessages.FAMILY_BINDING_DISCOVERY_REQUEST + "\"")) {
                    continue;
                }
                byte[] responseBytes = FamilyBindingProtocol.buildDiscoveryResponse(deviceId, tcpPort)
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

    private static void notifyError(Listener listener, String message) {
        if (listener != null) {
            listener.onError(message == null || message.isEmpty() ? "Family binding service failed." : message);
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
