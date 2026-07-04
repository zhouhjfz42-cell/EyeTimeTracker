package com.eyetimetracker.android;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class AndroidSyncClient {
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public AndroidSyncClient() {
        this(3000, 5000);
    }

    public AndroidSyncClient(int connectTimeoutMillis, int readTimeoutMillis) {
        this.connectTimeoutMillis = Math.max(1, connectTimeoutMillis);
        this.readTimeoutMillis = Math.max(1, readTimeoutMillis);
    }

    public String sendJson(SyncSettings settings, String requestJson) {
        if (settings == null) {
            return "";
        }
        if (!settings.isPaired || isBlank(settings.peerHost) || settings.peerPort <= 0) {
            settings.lastError = "Sync settings are incomplete.";
            return "";
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(settings.peerHost, settings.peerPort), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            try (
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.write(requestJson == null ? "" : requestJson);
                writer.newLine();
                writer.flush();

                String response = reader.readLine();
                settings.lastError = "";
                return response == null ? "" : response;
            }
        } catch (Exception ex) {
            settings.lastError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return "";
        }
    }

    public String sendJson(String host, int port, String requestJson, SyncSettings settings) {
        if (settings == null) {
            settings = new SyncSettings();
        }
        if (isBlank(host) || port <= 0) {
            settings.lastError = "PC address is incomplete.";
            return "";
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            try (
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.write(requestJson == null ? "" : requestJson);
                writer.newLine();
                writer.flush();

                String response = reader.readLine();
                settings.lastError = "";
                return response == null ? "" : response;
            }
        } catch (Exception ex) {
            settings.lastError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return "";
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
